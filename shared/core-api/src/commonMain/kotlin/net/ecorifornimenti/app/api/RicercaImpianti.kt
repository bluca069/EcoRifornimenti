package net.ecorifornimenti.app.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.ecorifornimenti.app.geo.distanzaKm
import net.ecorifornimenti.app.geo.grigliaRicerca
import net.ecorifornimenti.app.model.DettaglioImpianto
import net.ecorifornimenti.app.model.Impianto
import net.ecorifornimenti.app.model.Posizione
import net.ecorifornimenti.app.model.PreferenzaRicerca

/**
 * Lo stato di una ricerca mentre procede: la mappa si disegna su questo.
 *
 * [impianti] e' sempre l'insieme completo di quanto si sa **finora**, gia' ordinato:
 * la UI lo mostra subito e lo sostituisce a ogni avanzamento, senza dover accumulare.
 */
data class AvanzamentoRicerca(
    val impianti: List<Impianto>,
    val celleCompletate: Int,
    val celleTotali: Int,
) {
    val completata: Boolean get() = celleCompletate >= celleTotali
    /** 0.0 - 1.0, per la barra di avanzamento. */
    val frazione: Float get() = if (celleTotali == 0) 1f else celleCompletate.toFloat() / celleTotali
}

/**
 * Da dove arrivano i distributori da mostrare.
 *
 * L'interfaccia esiste perche' la schermata non debba conoscere ne' la rete ne' il
 * client HTTP: nei test le si passa una sorgente finta e si verifica il comportamento
 * della UI, non quello dell'Osservaprezzi.
 */
interface SorgenteImpianti {
    fun cerca(centro: Posizione, pref: PreferenzaRicerca): Flow<AvanzamentoRicerca>
    suspend fun cercaTutto(centro: Posizione, pref: PreferenzaRicerca): List<Impianto>

    /** La scheda completa di un impianto, caricata solo quando l'utente la apre. */
    suspend fun dettaglio(idImpianto: Int): DettaglioImpianto

    /**
     * Dimentica tutto quello che si era gia' chiesto al servizio.
     *
     * Serve quando e' l'utente a chiedere esplicitamente di aggiornare: in quel
     * momento vuole i prezzi di adesso, non quelli che avevamo in tasca.
     */
    suspend fun svuotaCache()
}

/**
 * Cerca i distributori attorno a una posizione, entro il raggio scelto dall'utente.
 *
 * Il servizio copre al massimo 10 km per chiamata, quindi i raggi piu' ampi si
 * ottengono interrogando piu' centri (vedi `grigliaRicerca`). Due conseguenze guidano
 * il disegno di questa classe:
 *
 * - **la prima cella e' quella dell'utente**, quindi i distributori piu' vicini si
 *   possono mostrare dopo circa un secondo, senza aspettare gli anelli esterni;
 * - le celle successive vanno prese con calma: [MAX_RICHIESTE_PARALLELE] alla volta,
 *   perche' oltre le quattro in volo il servizio inizia a rispondere 429.
 */
class RicercaImpianti(
    private val client: OsservaprezziClient,
    private val cache: CacheRicerca = CacheRicerca(),
) : SorgenteImpianti {

    /**
     * Le schede gia' aperte. Riaprire lo stesso distributore e' un gesto comune — si
     * confrontano due o tre stazioni vicine — e nel frattempo i dati non cambiano.
     */
    private val cacheDettagli = mutableMapOf<Int, DettaglioImpianto>()

    /**
     * Emette un aggiornamento ogni volta che una cella arriva: il primo contiene gia'
     * i risultati attorno all'utente. Se il raggio sta in una sola cella, emette una
     * volta sola.
     *
     * Gli errori di una singola cella non interrompono la ricerca — meglio una mappa
     * con un anello mancante che nessuna mappa — tranne quello sulla **prima** cella,
     * che significa non avere niente da mostrare e viene propagato.
     */
    override fun cerca(centro: Posizione, pref: PreferenzaRicerca): Flow<AvanzamentoRicerca> = flow {
        val celle = grigliaRicerca(centro, pref.raggioKm)
        val raccolti = LinkedHashMap<Int, Impianto>()

        suspend fun cella(p: Posizione): List<Impianto> =
            cache.oppure(p, pref.fuelType) { client.cercaPerZona(p, pref.fuelType) }

        // Prima cella: e' quella che popola la mappa, il suo errore e' un errore vero.
        raccolti.assorbi(cella(celle.first()))
        emit(avanzamento(raccolti, centro, pref, completate = 1, totali = celle.size))

        val restanti = celle.drop(1)
        if (restanti.isEmpty()) return@flow

        // Anelli esterni: a gruppi, per non superare il numero di richieste in volo.
        var completate = 1
        restanti.chunked(MAX_RICHIESTE_PARALLELE).forEach { gruppo ->
            val esiti = coroutineScope {
                gruppo.map { p ->
                    async {
                        try {
                            cella(p)
                        } catch (e: ErroreRicerca) {
                            // Un anello esterno caduto lascia comunque una mappa buona.
                            emptyList()
                        }
                    }
                }.awaitAll()
            }
            esiti.forEach { raccolti.assorbi(it) }
            completate += gruppo.size
            emit(avanzamento(raccolti, centro, pref, completate, celle.size))
        }
    }

    /**
     * La stessa ricerca, ma attesa fino in fondo: comoda per i test e per i casi in
     * cui non c'e' una mappa da riempire progressivamente.
     */
    override suspend fun cercaTutto(centro: Posizione, pref: PreferenzaRicerca): List<Impianto> {
        var ultimo = emptyList<Impianto>()
        cerca(centro, pref).collect { ultimo = it.impianti }
        return ultimo
    }

    override suspend fun svuotaCache() {
        cache.svuota()
        cacheDettagli.clear()
    }

    override suspend fun dettaglio(idImpianto: Int): DettaglioImpianto =
        cacheDettagli[idImpianto] ?: client.dettaglio(idImpianto).also { cacheDettagli[idImpianto] = it }

    private fun MutableMap<Int, Impianto>.assorbi(nuovi: List<Impianto>) {
        // Le celle si sovrappongono per costruzione: lo stesso impianto arriva piu'
        // volte. Si tiene la prima versione, sono lo stesso dato.
        nuovi.forEach { if (it.id !in this) put(it.id, it) }
    }

    /**
     * Ricalcola la distanza dalla posizione dell'utente (quella dell'API e' relativa
     * al centro della singola cella), scarta chi e' oltre il raggio scelto o non ha il
     * carburante cercato, e ordina per prezzo crescente: la domanda dell'utente e'
     * "dove costa meno", quindi l'ordine e' quello.
     */
    private fun avanzamento(
        raccolti: Map<Int, Impianto>,
        centro: Posizione,
        pref: PreferenzaRicerca,
        completate: Int,
        totali: Int,
    ): AvanzamentoRicerca {
        val impianti = raccolti.values
            .map { it.copy(distanzaKm = distanzaKm(centro, it.posizione)) }
            .filter { it.distanzaKm <= pref.raggioKm && it.prezzoPer(pref) != null }
            .sortedWith(compareBy({ it.prezzoPer(pref)!!.prezzo }, { it.distanzaKm }))
        return AvanzamentoRicerca(impianti, completate, totali)
    }

    companion object {
        /**
         * Misurato: con 8 richieste in parallelo il servizio risponde 429, con 4 no.
         * Si sta sotto la soglia nota, non sul filo.
         */
        const val MAX_RICHIESTE_PARALLELE = 3
    }
}
