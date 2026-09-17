package net.ecorifornimenti.app.model

/**
 * I carburanti fra cui l'utente sceglie la sua preferenza.
 *
 * [fuelId] e' l'identificativo usato dall'Osservaprezzi dentro ogni prezzo;
 * le sigle composte (`"2-1"`) le costruisce [ModalitaErogazione.codice].
 */
enum class TipoCarburante(val fuelId: Int, val etichetta: String) {
    BENZINA(1, "Benzina"),
    GASOLIO(2, "Gasolio"),
    METANO(3, "Metano"),
    GPL(4, "GPL"),
}

/** Self, servito, o indifferente: incide molto sul prezzo, quindi e' una scelta esplicita. */
enum class ModalitaErogazione(private val suffisso: String, val etichetta: String) {
    INDIFFERENTE("x", "Self o servito"),
    SELF("1", "Self"),
    SERVITO("0", "Servito");

    /** Il valore del campo `fuelType` dell'API: es. `"2-1"` = gasolio self. */
    fun codice(tipo: TipoCarburante): String = "${tipo.fuelId}-$suffisso"

    /** Filtro applicato ai prezzi di un impianto. */
    fun accetta(prezzo: PrezzoCarburante): Boolean = when (this) {
        INDIFFERENTE -> true
        SELF -> prezzo.self
        SERVITO -> !prezzo.self
    }
}

/**
 * Cosa cerca l'utente: il carburante, come lo vuole erogato e fin dove guardare.
 *
 * Il raggio e' limitato ai tre valori offerti dalla UI perche' oltre i 10 km ogni
 * scatto costa un anello di chiamate in piu' all'API (vedi `docs/analisi_fonti_dati.md`).
 */
data class PreferenzaRicerca(
    val tipo: TipoCarburante = TipoCarburante.BENZINA,
    // Alla prima apertura: self e 10 km. Il self e' come fa il pieno la maggior parte
    // di chi guarda i prezzi, e 10 km e' l'unico raggio che il servizio copre con una
    // sola chiamata, quindi la mappa compare subito.
    val modalita: ModalitaErogazione = ModalitaErogazione.SELF,
    val raggioKm: Int = 10,
) {
    val fuelType: String get() = modalita.codice(tipo)

    companion object {
        /**
         * I raggi selezionabili. Fino a 10 km il servizio risponde con una sola
         * chiamata, quindi 5 e 10 km sono istantanei; oltre servono piu'
         * interrogazioni e i distributori lontani compaiono poco per volta.
         */
        val RAGGI_KM = listOf(5, 10, 15, 25)
    }
}
