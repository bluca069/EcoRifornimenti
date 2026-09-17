package net.ecorifornimenti.app.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import net.ecorifornimenti.app.api.AvanzamentoRicerca
import net.ecorifornimenti.app.api.ErroreRicerca
import net.ecorifornimenti.app.api.SorgenteImpianti
import net.ecorifornimenti.app.model.DettaglioImpianto
import net.ecorifornimenti.app.model.Impianto
import net.ecorifornimenti.app.model.ModalitaErogazione
import net.ecorifornimenti.app.model.PrezzoCarburante
import net.ecorifornimenti.app.model.Servizio
import net.ecorifornimenti.app.geo.PosizioneFissa
import net.ecorifornimenti.app.geo.ProviderPosizione
import net.ecorifornimenti.app.model.Posizione
import net.ecorifornimenti.app.model.PreferenzaRicerca
import net.ecorifornimenti.app.model.TipoCarburante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val MILANO = Posizione(45.4642, 9.19)

private fun impianto(id: Int, nome: String, prezzo: Double, lat: Double, lng: Double) = Impianto(
    id = id,
    nome = nome,
    bandiera = "Bandiera $id",
    posizione = Posizione(lat, lng),
    prezzi = listOf(PrezzoCarburante(fuelId = 1, nome = "Benzina", prezzo = prezzo, self = true)),
)

/** Due distributori di benzina, uno caro e uno conveniente. */
private val DUE_IMPIANTI = listOf(
    impianto(2, "Conveniente", 1.800, 45.4642, 9.19),
    impianto(1, "Caro", 2.100, 45.47, 9.20),
)

/**
 * Sorgente finta: restituisce quel che le si dice, conta le ricerche e puo' fallire.
 * Cosi' i test guardano la schermata, non la rete.
 */
private class SorgenteFinta(
    private val esito: (PreferenzaRicerca) -> List<Impianto> = { DUE_IMPIANTI },
    private val errore: ErroreRicerca? = null,
    private val passi: Int = 1,
    private val erroreDettaglio: ErroreRicerca? = null,
) : SorgenteImpianti {
    var ricerche = 0
        private set
    var dettagliChiesti = 0
        private set

    override fun cerca(centro: Posizione, pref: PreferenzaRicerca): Flow<AvanzamentoRicerca> = flow {
        ricerche++
        if (errore != null) throw errore
        // Il carburante lo filtra la sorgente vera; qui basta rispettarne l'effetto.
        val trovati = esito(pref).filter { it.prezzoPer(pref) != null }
        repeat(passi) { i ->
            emit(AvanzamentoRicerca(trovati, celleCompletate = i + 1, celleTotali = passi))
        }
    }

    override suspend fun cercaTutto(centro: Posizione, pref: PreferenzaRicerca): List<Impianto> =
        esito(pref)

    var cacheSvuotata = 0
        private set

    override suspend fun svuotaCache() { cacheSvuotata++ }

    override suspend fun dettaglio(idImpianto: Int): DettaglioImpianto {
        dettagliChiesti++
        erroreDettaglio?.let { throw it }
        return DettaglioImpianto(
            id = idImpianto,
            nome = "Distributore $idImpianto",
            indirizzo = "VIA DI PROVA 1 - 20100 MILANO (MI)",
            bandiera = "Bandiera",
            societa = "Societa'",
            telefono = "",
            email = "",
            sitoWeb = "",
            prezzi = emptyList(),
            servizi = listOf(Servizio("6", "Bancomat")),
            orari = emptyList(),
        )
    }
}

private fun modello(
    scope: TestScope,
    posizioni: ProviderPosizione = PosizioneFissa(MILANO),
    sorgente: SorgenteFinta = SorgenteFinta(),
    preferenze: ArchivioPreferenze = PreferenzeInMemoria(),
): ModelloRicerca = ModelloRicerca(sorgente, posizioni, scope, preferenze)

@OptIn(ExperimentalCoroutinesApi::class)
class ModelloRicercaTest {

    @Test
    fun `all'avvio cerca e mostra i risultati ordinati`() = runTest {
        val m = modello(this)
        m.avvia()
        advanceUntilIdle()

        val s = m.stato.value
        assertTrue(s.stato is StatoSchermata.Pronta)
        assertEquals(listOf(2, 1), s.impianti.map { it.id }, "il piu' conveniente va in testa")
        assertEquals(2, s.migliore?.id)
        assertEquals(MILANO, s.posizione)
        assertTrue(!s.mostraAvanzamento, "a ricerca finita la barra sparisce")
    }

    @Test
    fun `le fasce di prezzo sono pronte per la mappa`() = runTest {
        val m = modello(this)
        m.avvia()
        advanceUntilIdle()

        val s = m.stato.value
        assertEquals(s.impianti.size, s.fasce.size)
    }

    @Test
    fun `senza permesso spiega cosa manca invece di restare vuota`() = runTest {
        val m = modello(this, posizioni = PosizioneFissa(MILANO, concesso = false))
        m.avvia()
        advanceUntilIdle()

        val stato = m.stato.value.stato
        assertTrue(stato is StatoSchermata.Errore)
        assertTrue((stato as StatoSchermata.Errore).messaggio.contains("permesso"))
    }

    @Test
    fun `senza posizione disponibile lo dice`() = runTest {
        val vuoto = object : ProviderPosizione {
            override fun permessoConcesso() = true
            override suspend fun posizioneCorrente(): Posizione? = null
        }
        val m = modello(this, posizioni = vuoto)
        m.avvia()
        advanceUntilIdle()

        assertTrue(m.stato.value.stato is StatoSchermata.Errore)
    }

    @Test
    fun `cambiare carburante rifa' la ricerca`() = runTest {
        val m = modello(this)
        m.avvia()
        advanceUntilIdle()

        m.cambiaPreferenza(m.stato.value.preferenza.copy(tipo = TipoCarburante.GASOLIO))
        advanceUntilIdle()

        assertEquals(TipoCarburante.GASOLIO, m.stato.value.preferenza.tipo)
        // Nessun impianto eroga gasolio nella risposta finta: la lista si svuota,
        // ma la schermata resta utilizzabile invece di andare in errore.
        assertTrue(m.stato.value.stato is StatoSchermata.Pronta)
        assertTrue(m.stato.value.impianti.isEmpty())
    }

    @Test
    fun `ripetere la stessa preferenza non fa nulla`() = runTest {
        val m = modello(this)
        m.avvia()
        advanceUntilIdle()
        val prima = m.stato.value

        m.cambiaPreferenza(prima.preferenza)
        advanceUntilIdle()

        assertEquals(prima.impianti, m.stato.value.impianti)
    }

    @Test
    fun `la selezione si imposta e si annulla`() = runTest {
        val m = modello(this)
        m.avvia()
        advanceUntilIdle()

        m.seleziona(m.stato.value.impianti.first())
        assertEquals(2, m.stato.value.selezionato?.id)

        m.seleziona(null)
        assertNull(m.stato.value.selezionato)
    }

    @Test
    fun `una nuova ricerca azzera la selezione precedente`() = runTest {
        val m = modello(this)
        m.avvia()
        advanceUntilIdle()
        m.seleziona(m.stato.value.impianti.first())

        m.cercaIn(Posizione(45.50, 9.25))
        advanceUntilIdle()

        assertNull(m.stato.value.selezionato)
    }

    @Test
    fun `se la ricerca fallisce del tutto lo stato e' un errore leggibile`() = runTest {
        val m = modello(this, sorgente = SorgenteFinta(errore = ErroreRicerca.TroppeRichieste))
        m.avvia()
        advanceUntilIdle()

        val stato = m.stato.value.stato
        assertTrue(stato is StatoSchermata.Errore)
        assertTrue((stato as StatoSchermata.Errore).messaggio.isNotBlank())
    }

    @Test
    fun `riprova riparte dalla stessa posizione`() = runTest {
        val sorgente = SorgenteFinta()
        val m = modello(this, sorgente = sorgente)
        m.avvia()
        advanceUntilIdle()
        val dopoAvvio = sorgente.ricerche

        m.riprova()
        advanceUntilIdle()

        assertEquals(dopoAvvio + 1, sorgente.ricerche)
        assertEquals(MILANO, m.stato.value.posizione, "la posizione non deve cambiare")
        assertTrue(m.stato.value.stato is StatoSchermata.Pronta)
    }

    @Test
    fun `parte dall'ultima posizione nota e non rifa' la ricerca per pochi metri`() = runTest {
        val vicino = object : ProviderPosizione {
            override fun permessoConcesso() = true
            override suspend fun ultimaPosizioneNota() = MILANO
            // Fix preciso a ~200 metri da quello noto: non giustifica una seconda raffica.
            override suspend fun posizioneCorrente() = Posizione(MILANO.lat + 0.002, MILANO.lng)
        }
        val sorgente = SorgenteFinta()
        val m = modello(this, posizioni = vicino, sorgente = sorgente)
        m.avvia()
        advanceUntilIdle()

        assertEquals(1, sorgente.ricerche, "una sola ricerca: lo spostamento e' irrilevante")
        assertEquals(MILANO, m.stato.value.posizione)
    }

    @Test
    fun `se il fix preciso e' lontano la ricerca si rifa`() = runTest {
        val lontano = object : ProviderPosizione {
            override fun permessoConcesso() = true
            override suspend fun ultimaPosizioneNota() = MILANO
            // 5 km piu' in la': la classifica dei distributori cambia davvero.
            override suspend fun posizioneCorrente() = Posizione(MILANO.lat + 0.045, MILANO.lng)
        }
        val m = modello(this, posizioni = lontano)
        m.avvia()
        advanceUntilIdle()

        assertEquals(MILANO.lat + 0.045, m.stato.value.posizione?.lat)
    }

    @Test
    fun `durante gli anelli esterni la barra avanza e poi sparisce`() = runTest {
        val m = modello(this, sorgente = SorgenteFinta(passi = 4))
        m.avvia()
        advanceUntilIdle()

        val s = m.stato.value
        assertEquals(1f, s.avanzamento)
        assertTrue(!s.mostraAvanzamento, "finita la ricerca la barra non resta accesa")
        assertTrue(s.impianti.isNotEmpty())
    }

    @Test
    fun `cercare in un punto scelto a mano funziona come l'avvio`() = runTest {
        val m = modello(this)
        m.cercaIn(Posizione(43.7696, 11.2558))
        advanceUntilIdle()

        assertEquals(43.7696, m.stato.value.posizione?.lat)
        assertTrue(m.stato.value.stato is StatoSchermata.Pronta)
    }
}

class SchedaEPreferenzeTest {

    @Test
    fun `aprire un distributore mostra subito quel che si sa e poi il dettaglio`() = runTest {
        val sorgente = SorgenteFinta()
        val m = modello(this, sorgente = sorgente)
        m.avvia()
        advanceUntilIdle()

        m.seleziona(m.stato.value.impianti.first())
        // Prima ancora che il dettaglio arrivi la scheda c'e' gia', col nome e il prezzo.
        val subito = m.stato.value.scheda
        assertEquals(2, subito?.impianto?.id)
        assertTrue(subito?.caricamento == true)

        advanceUntilIdle()
        val dopo = m.stato.value.scheda
        assertTrue(dopo?.caricamento == false)
        assertEquals("VIA DI PROVA 1 - 20100 MILANO (MI)", dopo?.dettaglio?.indirizzo)
        assertEquals(1, sorgente.dettagliChiesti)
    }

    @Test
    fun `se il dettaglio non arriva la scheda resta utilizzabile`() = runTest {
        val sorgente = SorgenteFinta(erroreDettaglio = ErroreRicerca.TroppeRichieste)
        val m = modello(this, sorgente = sorgente)
        m.avvia()
        advanceUntilIdle()

        m.seleziona(m.stato.value.impianti.first())
        advanceUntilIdle()

        val scheda = m.stato.value.scheda
        assertTrue(scheda != null, "la scheda non deve sparire per un errore di rete")
        assertTrue(scheda!!.erroreDettaglio)
        assertNull(scheda.dettaglio)
        // I prezzi che si avevano gia' restano a disposizione.
        assertTrue(scheda.impianto.prezzi.isNotEmpty())
    }

    @Test
    fun `chiudere la scheda la toglie insieme alla selezione`() = runTest {
        val m = modello(this)
        m.avvia()
        advanceUntilIdle()
        m.seleziona(m.stato.value.impianti.first())
        advanceUntilIdle()

        m.seleziona(null)

        assertNull(m.stato.value.scheda)
        assertNull(m.stato.value.selezionato)
    }

    @Test
    fun `cambiare distributore prima che il dettaglio arrivi non mescola le schede`() = runTest {
        val m = modello(this)
        m.avvia()
        advanceUntilIdle()

        m.seleziona(m.stato.value.impianti[0])
        m.seleziona(m.stato.value.impianti[1])
        advanceUntilIdle()

        val scheda = m.stato.value.scheda
        assertEquals(m.stato.value.impianti[1].id, scheda?.impianto?.id)
        assertEquals(scheda?.impianto?.id, scheda?.dettaglio?.id)
    }

    @Test
    fun `una nuova ricerca chiude la scheda aperta`() = runTest {
        val m = modello(this)
        m.avvia()
        advanceUntilIdle()
        m.seleziona(m.stato.value.impianti.first())
        advanceUntilIdle()

        m.cambiaPreferenza(m.stato.value.preferenza.copy(raggioKm = 25))
        advanceUntilIdle()

        assertNull(m.stato.value.scheda)
    }

    @Test
    fun `le preferenze scelte vengono salvate`() = runTest {
        val archivio = PreferenzeInMemoria()
        val m = modello(this, preferenze = archivio)
        m.avvia()
        advanceUntilIdle()

        m.cambiaPreferenza(PreferenzaRicerca(TipoCarburante.GPL, raggioKm = 25))

        assertEquals(TipoCarburante.GPL, archivio.leggi().tipo)
        assertEquals(25, archivio.leggi().raggioKm)
    }

    @Test
    fun `senza nulla di salvato si parte dai valori iniziali`() = runTest {
        val m = modello(this, preferenze = PreferenzeInMemoria())
        val p = m.stato.value.preferenza
        assertEquals(ModalitaErogazione.SELF, p.modalita)
        assertEquals(10, p.raggioKm)
    }

    @Test
    fun `all'avvio si riparte dalle preferenze salvate`() = runTest {
        val archivio = PreferenzeInMemoria(PreferenzaRicerca(TipoCarburante.METANO, raggioKm = 15))
        val m = modello(this, preferenze = archivio)

        assertEquals(TipoCarburante.METANO, m.stato.value.preferenza.tipo)
        assertEquals(15, m.stato.value.preferenza.raggioKm)
    }
}

class AggiornamentoTest {

    /**
     * Tirare giu' la lista deve rileggere i prezzi davvero: se si limitasse a
     * ripetere la ricerca, la cache restituirebbe gli stessi numeri e il gesto
     * sarebbe una bugia.
     */
    @Test
    fun `aggiornare butta via la cache e rifa' la ricerca`() = runTest {
        val sorgente = SorgenteFinta()
        val m = modello(this, sorgente = sorgente)
        m.avvia()
        advanceUntilIdle()
        val dopoAvvio = sorgente.ricerche

        m.aggiorna()
        advanceUntilIdle()

        assertEquals(1, sorgente.cacheSvuotata)
        assertEquals(dopoAvvio + 1, sorgente.ricerche)
    }

    @Test
    fun `durante l'aggiornamento lo stato lo segnala, e poi smette`() = runTest {
        val sorgente = SorgenteFinta(passi = 3)
        val m = modello(this, sorgente = sorgente)
        m.avvia()
        advanceUntilIdle()

        m.aggiorna()
        assertTrue(m.stato.value.inAggiornamento, "la rotella deve comparire subito")

        advanceUntilIdle()
        assertTrue(!m.stato.value.inAggiornamento, "e sparire quando ha finito")
        assertTrue(m.stato.value.impianti.isNotEmpty())
    }

    @Test
    fun `aggiornare senza una posizione riparte dall'avvio`() = runTest {
        val sorgente = SorgenteFinta()
        val m = modello(this, sorgente = sorgente)

        m.aggiorna()
        advanceUntilIdle()

        assertTrue(m.stato.value.stato is StatoSchermata.Pronta)
        assertEquals(MILANO, m.stato.value.posizione)
    }

    @Test
    fun `se l'aggiornamento fallisce la rotella non resta girando`() = runTest {
        val sorgente = SorgenteFinta()
        val m = modello(this, sorgente = sorgente)
        m.avvia()
        advanceUntilIdle()

        val rotto = modello(this, sorgente = SorgenteFinta(errore = ErroreRicerca.TroppeRichieste))
        rotto.avvia()
        advanceUntilIdle()
        rotto.aggiorna()
        advanceUntilIdle()

        assertTrue(!rotto.stato.value.inAggiornamento)
    }
}

/** Provider che si puo' spostare fra una lettura e l'altra, come un telefono vero. */
private class PosizioneMobile(iniziale: Posizione) : ProviderPosizione {
    var attuale: Posizione = iniziale
    var letture = 0
        private set

    override fun permessoConcesso() = true
    override suspend fun posizioneCorrente(): Posizione {
        letture++
        return attuale
    }
}

class PosizioneCheCambiaTest {

    @Test
    fun `al rientro nell'app, se ci si e' spostati si rifa' la ricerca`() = runTest {
        val posizioni = PosizioneMobile(MILANO)
        val sorgente = SorgenteFinta()
        val m = ModelloRicerca(sorgente, posizioni, this, PreferenzeInMemoria())
        m.avvia()
        advanceUntilIdle()
        val dopoAvvio = sorgente.ricerche

        // Tre chilometri piu' in la': i distributori vicini non sono piu' gli stessi.
        posizioni.attuale = Posizione(MILANO.lat + 0.027, MILANO.lng)
        m.alRientro()
        advanceUntilIdle()

        assertEquals(dopoAvvio + 1, sorgente.ricerche)
        assertEquals(posizioni.attuale, m.stato.value.posizione, "la mappa deve seguire")
    }

    @Test
    fun `al rientro, se non ci si e' mossi non si chiede nulla al servizio`() = runTest {
        val posizioni = PosizioneMobile(MILANO)
        val sorgente = SorgenteFinta()
        val m = ModelloRicerca(sorgente, posizioni, this, PreferenzeInMemoria())
        m.avvia()
        advanceUntilIdle()
        val dopoAvvio = sorgente.ricerche

        // Cento metri: riaprire l'app da fermi non deve costare una raffica di chiamate.
        posizioni.attuale = Posizione(MILANO.lat + 0.0009, MILANO.lng)
        m.alRientro()
        advanceUntilIdle()

        assertEquals(dopoAvvio, sorgente.ricerche)
        assertEquals(MILANO, m.stato.value.posizione)
    }

    @Test
    fun `aggiornando si rilegge anche la posizione`() = runTest {
        val posizioni = PosizioneMobile(MILANO)
        val sorgente = SorgenteFinta()
        val m = ModelloRicerca(sorgente, posizioni, this, PreferenzeInMemoria())
        m.avvia()
        advanceUntilIdle()

        val altrove = Posizione(MILANO.lat + 0.05, MILANO.lng)
        posizioni.attuale = altrove
        m.aggiorna()
        advanceUntilIdle()

        assertEquals(altrove, m.stato.value.posizione, "chi aggiorna spesso e' in movimento")
        assertEquals(1, sorgente.cacheSvuotata)
    }

    @Test
    fun `ogni aggiornamento chiede alla mappa di tornare sulla posizione`() = runTest {
        val m = modello(this)
        m.avvia()
        advanceUntilIdle()
        val prima = m.stato.value.richiesteRicentro

        m.aggiorna()
        advanceUntilIdle()

        // Anche da fermi: l'utente puo' aver trascinato la mappa altrove, e
        // "aggiorna" deve rispondere alla domanda "dove sono adesso".
        assertEquals(prima + 1, m.stato.value.richiesteRicentro)
    }

    @Test
    fun `cambiare carburante non fa saltare la vista della mappa`() = runTest {
        val m = modello(this)
        m.avvia()
        advanceUntilIdle()
        val prima = m.stato.value.richiesteRicentro

        m.cambiaPreferenza(m.stato.value.preferenza.copy(tipo = TipoCarburante.GASOLIO))
        advanceUntilIdle()

        assertEquals(prima, m.stato.value.richiesteRicentro)
    }

    @Test
    fun `cercando in un'altra zona il punto della posizione resta dov'e'`() = runTest {
        val m = modello(this)
        m.avvia()
        advanceUntilIdle()

        val altrove = Posizione(45.60, 9.40)
        m.cercaIn(altrove)
        advanceUntilIdle()

        assertEquals(altrove, m.stato.value.posizione, "la ricerca si sposta")
        assertEquals(MILANO, m.stato.value.posizioneGps, "ma l'utente e' rimasto dov'era")
    }

    @Test
    fun `aggiornando da fermi il centro non si muove`() = runTest {
        val posizioni = PosizioneMobile(MILANO)
        val m = ModelloRicerca(SorgenteFinta(), posizioni, this, PreferenzeInMemoria())
        m.avvia()
        advanceUntilIdle()

        posizioni.attuale = Posizione(MILANO.lat + 0.001, MILANO.lng)
        m.aggiorna()
        advanceUntilIdle()

        assertEquals(MILANO, m.stato.value.posizione)
    }

    @Test
    fun `senza permesso il rientro non fa nulla`() = runTest {
        val sorgente = SorgenteFinta()
        val m = modello(this, posizioni = PosizioneFissa(MILANO, concesso = false), sorgente = sorgente)

        m.alRientro()
        advanceUntilIdle()

        // Niente ricerche e niente cambi di stato: al permesso ci pensa `avvia`, che
        // e' il punto in cui l'app sa spiegare all'utente cosa manca.
        assertEquals(0, sorgente.ricerche)
        assertTrue(m.stato.value.stato is StatoSchermata.Iniziale)
    }

    @Test
    fun `il rientro prima che la schermata sia pronta non interferisce`() = runTest {
        val sorgente = SorgenteFinta()
        val m = modello(this, sorgente = sorgente)

        // L'app e' appena partita e sta ancora cercando la posizione: un rientro qui
        // non deve accodare una seconda ricerca sopra quella in corso.
        m.alRientro()
        advanceUntilIdle()

        assertEquals(0, sorgente.ricerche)
    }
}

class SenzaReteTest {

    @Test
    fun `senza rete il messaggio parla di connessione, non di errore tecnico`() = runTest {
        val caduta = ErroreRicerca.Rete(RuntimeException("connection refused"))
        val m = modello(this, sorgente = SorgenteFinta(errore = caduta))
        m.avvia()
        advanceUntilIdle()

        val stato = m.stato.value.stato as StatoSchermata.Errore
        assertTrue(stato.messaggio.contains("Connessione", ignoreCase = true), stato.messaggio)
        // Niente stack trace o nomi di classi in faccia a chi guida.
        assertTrue(!stato.messaggio.contains("Exception"))
    }

    @Test
    fun `tornata la rete, riprova rimette in piedi la schermata`() = runTest {
        var prima = true
        val sorgente = object : SorgenteImpianti {
            override fun cerca(centro: Posizione, pref: PreferenzaRicerca) =
                flow<AvanzamentoRicerca> {
                    if (prima) {
                        prima = false
                        throw ErroreRicerca.Rete(RuntimeException("offline"))
                    }
                    emit(AvanzamentoRicerca(DUE_IMPIANTI, 1, 1))
                }

            override suspend fun cercaTutto(centro: Posizione, pref: PreferenzaRicerca) = DUE_IMPIANTI
            override suspend fun dettaglio(idImpianto: Int) = throw ErroreRicerca.TroppeRichieste
            override suspend fun svuotaCache() = Unit
        }
        val m = ModelloRicerca(sorgente, PosizioneFissa(MILANO), this, PreferenzeInMemoria())
        m.avvia()
        advanceUntilIdle()
        assertTrue(m.stato.value.stato is StatoSchermata.Errore)

        m.riprova()
        advanceUntilIdle()

        assertTrue(m.stato.value.stato is StatoSchermata.Pronta)
        assertEquals(2, m.stato.value.impianti.size)
    }

    @Test
    fun `se la rete cade a meta' ricerca si tiene quel che era arrivato`() = runTest {
        val sorgente = object : SorgenteImpianti {
            override fun cerca(centro: Posizione, pref: PreferenzaRicerca) =
                flow {
                    emit(AvanzamentoRicerca(DUE_IMPIANTI, 1, 5))
                    throw ErroreRicerca.Rete(RuntimeException("caduta"))
                }

            override suspend fun cercaTutto(centro: Posizione, pref: PreferenzaRicerca) = DUE_IMPIANTI
            override suspend fun dettaglio(idImpianto: Int) = throw ErroreRicerca.TroppeRichieste
            override suspend fun svuotaCache() = Unit
        }
        val m = ModelloRicerca(sorgente, PosizioneFissa(MILANO), this, PreferenzeInMemoria())
        m.avvia()
        advanceUntilIdle()

        // Meglio una mappa parziale che una schermata di errore al posto dei risultati.
        assertTrue(m.stato.value.stato is StatoSchermata.Pronta)
        assertEquals(2, m.stato.value.impianti.size)
        assertTrue(!m.stato.value.mostraAvanzamento, "la barra non deve restare accesa")
    }
}

class CodificaPreferenzeTest {

    @Test
    fun `rilegge quel che ha scritto`() {
        val p = PreferenzaRicerca(TipoCarburante.GASOLIO, raggioKm = 25)
        val riletta = CodificaPreferenze.decodifica(p.tipo.name, p.modalita.name, p.raggioKm)
        assertEquals(p, riletta)
    }

    @Test
    fun `valori assenti o irriconoscibili tornano ai predefiniti`() {
        val base = PreferenzaRicerca()
        assertEquals(base, CodificaPreferenze.decodifica(null, null, null))
        // Un archivio scritto da una versione futura non deve impedire l'avvio.
        assertEquals(base, CodificaPreferenze.decodifica("IDROGENO", "TELEPATIA", 999))
        // Un raggio fuori dall'elenco non deve passare, uno dentro si'.
        assertEquals(5, CodificaPreferenze.decodifica(null, null, 5).raggioKm)
        assertEquals(base.raggioKm, CodificaPreferenze.decodifica(null, null, 7).raggioKm)
    }
}

class FormattazioneTest {

    @Test
    fun `il prezzo si scrive con la virgola e tre decimali`() {
        assertEquals("1,799", formattaPrezzo(1.799))
        assertEquals("2,000", formattaPrezzo(2.0))
        assertEquals("1,050", formattaPrezzo(1.05))
    }

    @Test
    fun `la distanza passa ai metri sotto il chilometro`() {
        assertEquals("450 m", formattaDistanza(0.45))
        assertEquals("1,0 km", formattaDistanza(1.0))
        assertEquals("12,3 km", formattaDistanza(12.34))
    }
}
