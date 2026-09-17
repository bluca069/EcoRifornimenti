package net.ecorifornimenti.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.ecorifornimenti.app.api.ErroreRicerca
import net.ecorifornimenti.app.api.SorgenteImpianti
import net.ecorifornimenti.app.geo.ProviderPosizione
import net.ecorifornimenti.app.model.DettaglioImpianto
import net.ecorifornimenti.app.model.FasciaPrezzo
import net.ecorifornimenti.app.model.Impianto
import net.ecorifornimenti.app.model.Posizione
import net.ecorifornimenti.app.model.PreferenzaRicerca
import net.ecorifornimenti.app.model.fasce

/** Cosa sta facendo la schermata, in un dato momento. */
sealed interface StatoSchermata {
    /** Prima dell'avvio, o mentre si aspetta il permesso di posizione. */
    data object Iniziale : StatoSchermata
    /** Si sta cercando il fix GPS: l'unico momento in cui la mappa e' legittimamente vuota. */
    data object AttesaPosizione : StatoSchermata
    /** C'e' una mappa da mostrare; [caricamento] dice se sta ancora arrivando altro. */
    data class Pronta(val caricamento: Boolean) : StatoSchermata
    /** Non si e' potuto mostrare nulla: il messaggio e' gia' scritto per l'utente. */
    data class Errore(val messaggio: String) : StatoSchermata
}

/**
 * La scheda di un distributore mentre si apre: prima si conosce solo quel che era gia'
 * nella lista, poi arriva il dettaglio con indirizzo, servizi e orari.
 */
data class SchedaImpianto(
    val impianto: Impianto,
    val dettaglio: DettaglioImpianto? = null,
    val caricamento: Boolean = true,
    /** Il dettaglio non e' arrivato: la scheda resta aperta con quel che si sa. */
    val erroreDettaglio: Boolean = false,
)

/**
 * Lo stato completo della schermata principale.
 *
 * [fasce] e' precalcolato qui e non dentro la mappa: dipende dall'insieme degli
 * impianti, quindi ricalcolarlo per ogni marker sarebbe sia sbagliato che costoso.
 */
data class StatoRicerca(
    val stato: StatoSchermata = StatoSchermata.Iniziale,
    /** Il centro della ricerca: di norma dove ci si trova, ma anche un punto scelto sulla mappa. */
    val posizione: Posizione? = null,
    /**
     * Dove si trova davvero l'utente, che non e' sempre il centro della ricerca: dopo
     * un "cerca in questa zona" il punto azzurro sulla mappa deve restare dove si e',
     * non spostarsi sul pezzo di mappa che si sta guardando.
     */
    val posizioneGps: Posizione? = null,
    val preferenza: PreferenzaRicerca = PreferenzaRicerca(),
    val impianti: List<Impianto> = emptyList(),
    val fasce: Map<Int, FasciaPrezzo> = emptyMap(),
    val avanzamento: Float = 0f,
    /** L'utente ha tirato giu' la lista per rileggere i prezzi. */
    val inAggiornamento: Boolean = false,
    /**
     * Cresce a ogni richiesta esplicita di aggiornamento.
     *
     * La mappa lo guarda per sapere che deve tornare sulla posizione GPS: di solito si
     * ricentra solo se il centro cambia, ma chi aggiorna vuole rivedere dove si trova
     * anche se nel frattempo aveva trascinato la vista altrove.
     */
    val richiesteRicentro: Int = 0,
    val selezionato: Impianto? = null,
    val scheda: SchedaImpianto? = null,
) {
    /** Il piu' conveniente: e' la risposta alla domanda per cui l'app esiste. */
    val migliore: Impianto? get() = impianti.firstOrNull()

    val mostraAvanzamento: Boolean
        get() = (stato as? StatoSchermata.Pronta)?.caricamento == true
}

/**
 * Coordina posizione, preferenze e ricerca, ed espone un solo stato osservabile.
 *
 * Vive fuori da Compose e da Android per restare testabile: la schermata si limita a
 * leggere [stato] e a chiamare i metodi qui sotto.
 */
class ModelloRicerca(
    private val ricerca: SorgenteImpianti,
    private val posizioni: ProviderPosizione,
    private val scope: CoroutineScope,
    /** Le scelte dell'utente, ritrovate cosi' come le aveva lasciate. */
    private val preferenze: ArchivioPreferenze = PreferenzeInMemoria(),
) {
    private val _stato = MutableStateFlow(StatoRicerca(preferenza = preferenze.leggi()))
    val stato: StateFlow<StatoRicerca> = _stato.asStateFlow()

    /** La ricerca in corso: cambiare carburante o raggio deve annullarla, non accodarne un'altra. */
    private var ricercaInCorso: Job? = null

    /** Il caricamento della scheda aperta: cambiare distributore lo annulla. */
    private var dettaglioInCorso: Job? = null

    /**
     * Il flusso di avvio: posizione e poi ricerca.
     *
     * Se c'e' un'ultima posizione nota si parte da quella, cosi' la mappa e' gia'
     * centrata e popolata mentre il fix preciso arriva; quando arriva, se si e'
     * spostata in modo significativo, la ricerca viene rifatta.
     */
    fun avvia() {
        if (!posizioni.permessoConcesso()) {
            _stato.value = _stato.value.copy(
                stato = StatoSchermata.Errore("Serve il permesso di posizione per trovare i distributori vicini")
            )
            return
        }
        scope.launch {
            _stato.value = _stato.value.copy(stato = StatoSchermata.AttesaPosizione)
            val nota = posizioni.ultimaPosizioneNota()
            if (nota != null) cercaDaPosizioneGps(nota)

            val precisa = posizioni.posizioneCorrente()
            when {
                precisa == null && nota == null -> _stato.value = _stato.value.copy(
                    stato = StatoSchermata.Errore("Posizione non disponibile: controlla che il GPS sia acceso")
                )
                precisa != null && (nota == null || distanteDa(nota, precisa)) -> cercaDaPosizioneGps(precisa)
            }
        }
    }

    /** Ricerca su una posizione scelta a mano (citta' cercata, o mappa spostata). */
    fun cercaIn(posizione: Posizione) = cercaDa(posizione)

    /**
     * Rilegge i prezzi da capo, buttando via la cache: e' il gesto di chi tira giu' la
     * lista perche' vuole sapere quanto costa *adesso*, non venti minuti fa.
     *
     * Insieme ai prezzi rilegge anche **dove si trova**: chi aggiorna spesso lo fa
     * mentre e' in movimento, e aggiornare i prezzi di dove si era prima non
     * servirebbe a nulla.
     */
    fun aggiorna() {
        if (_stato.value.posizione == null) return avvia()
        ricercaInCorso?.cancel()
        _stato.value = _stato.value.copy(
            inAggiornamento = true,
            richiesteRicentro = _stato.value.richiesteRicentro + 1,
        )
        ricercaInCorso = scope.launch {
            ricerca.svuotaCache()
            val centro = posizioneAggiornata() ?: _stato.value.posizione ?: return@launch
            _stato.value = _stato.value.copy(posizione = centro, posizioneGps = centro)
            eseguiRicerca(centro, _stato.value.preferenza)
        }
    }

    /**
     * Da chiamare quando l'app torna in primo piano.
     *
     * Chi la riapre spesso l'ha chiusa in un posto e riaperta in un altro: se ci si e'
     * spostati si rifa' la ricerca e la mappa segue, altrimenti non si tocca nulla —
     * riaprire l'app non deve costare una raffica di chiamate al servizio per niente.
     */
    fun alRientro() {
        if (!posizioni.permessoConcesso()) return
        if (_stato.value.stato !is StatoSchermata.Pronta) return
        scope.launch {
            val precedente = _stato.value.posizione ?: return@launch
            val attuale = posizioni.posizioneCorrente() ?: return@launch
            if (distanteDa(precedente, attuale)) cercaDaPosizioneGps(attuale)
        }
    }

    /** La posizione di adesso, se e' cambiata abbastanza da contare. */
    private suspend fun posizioneAggiornata(): Posizione? {
        if (!posizioni.permessoConcesso()) return null
        val attuale = posizioni.posizioneCorrente() ?: return null
        val precedente = _stato.value.posizioneGps
        return if (precedente == null || distanteDa(precedente, attuale)) attuale else precedente
    }

    fun cambiaPreferenza(nuova: PreferenzaRicerca) {
        if (nuova == _stato.value.preferenza) return
        preferenze.salva(nuova)
        _stato.value = _stato.value.copy(preferenza = nuova)
        _stato.value.posizione?.let { cercaDa(it) }
    }

    /**
     * Apre (o chiude, con `null`) la scheda di un distributore.
     *
     * La scheda compare subito con i dati che si hanno gia' — nome, prezzo, distanza —
     * e il dettaglio la completa quando arriva: aspettare la rete per mostrare qualcosa
     * farebbe sembrare il tocco ignorato.
     */
    fun seleziona(impianto: Impianto?) {
        dettaglioInCorso?.cancel()
        if (impianto == null) {
            _stato.value = _stato.value.copy(selezionato = null, scheda = null)
            return
        }
        _stato.value = _stato.value.copy(
            selezionato = impianto,
            scheda = SchedaImpianto(impianto),
        )
        dettaglioInCorso = scope.launch {
            val esito = try {
                ricerca.dettaglio(impianto.id)
            } catch (e: ErroreRicerca) {
                null
            }
            // Nel frattempo l'utente puo' aver chiuso la scheda o aperto un altro
            // distributore: in quel caso il risultato non serve piu' a nessuno.
            val corrente = _stato.value.scheda
            if (corrente?.impianto?.id == impianto.id) {
                _stato.value = _stato.value.copy(
                    scheda = corrente.copy(
                        dettaglio = esito,
                        caricamento = false,
                        erroreDettaglio = esito == null,
                    )
                )
            }
        }
    }

    fun riprova() {
        val da = _stato.value.posizione
        if (da != null) cercaDa(da) else avvia()
    }

    /** Come [cercaDa], ma segnala che quel centro e' anche la posizione dell'utente. */
    private fun cercaDaPosizioneGps(centro: Posizione) {
        _stato.value = _stato.value.copy(posizioneGps = centro)
        cercaDa(centro)
    }

    private fun cercaDa(centro: Posizione) {
        ricercaInCorso?.cancel()
        val pref = _stato.value.preferenza
        _stato.value = _stato.value.copy(
            posizione = centro,
            stato = StatoSchermata.Pronta(caricamento = true),
            avanzamento = 0f,
            selezionato = null,
            scheda = null,
        )
        ricercaInCorso = scope.launch { eseguiRicerca(centro, pref) }
    }

    private suspend fun eseguiRicerca(centro: Posizione, pref: PreferenzaRicerca) {
        try {
            ricerca.cerca(centro, pref).collect { passo ->
                _stato.value = _stato.value.copy(
                    stato = StatoSchermata.Pronta(caricamento = !passo.completata),
                    impianti = passo.impianti,
                    fasce = fasce(passo.impianti, pref),
                    avanzamento = passo.frazione,
                    inAggiornamento = !passo.completata && _stato.value.inAggiornamento,
                )
            }
        } catch (e: ErroreRicerca) {
            // Se qualcosa era gia' sulla mappa si tiene: meglio un dato parziale
            // che una schermata di errore al posto di una mappa funzionante.
            _stato.value = if (_stato.value.impianti.isNotEmpty()) {
                _stato.value.copy(stato = StatoSchermata.Pronta(caricamento = false))
            } else {
                _stato.value.copy(stato = StatoSchermata.Errore(e.message ?: "Ricerca non riuscita"))
            }
        } finally {
            _stato.value = _stato.value.copy(inAggiornamento = false)
        }
    }

    private fun distanteDa(a: Posizione, b: Posizione): Boolean =
        net.ecorifornimenti.app.geo.distanzaKm(a, b) > SPOSTAMENTO_SIGNIFICATIVO_KM

    private companion object {
        /**
         * Sotto questa soglia rifare la ricerca non cambierebbe la classifica: si
         * evita una raffica di chiamate per pochi metri di scarto. Mezzo chilometro
         * e' il punto in cui le distanze mostrate iniziano a essere sbagliate per chi
         * cammina o guida.
         */
        const val SPOSTAMENTO_SIGNIFICATIVO_KM = 0.5
    }
}
