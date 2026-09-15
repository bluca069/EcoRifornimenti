package net.ecorifornimenti.app.model

/** Un servizio offerto dall'impianto (officina, bancomat, servizi per disabili...). */
data class Servizio(val id: String, val descrizione: String)

/**
 * La scheda completa, caricata solo quando l'utente apre un impianto:
 * e' una chiamata in piu' all'API, quindi non si fa per tutta la lista.
 */
data class DettaglioImpianto(
    val id: Int,
    val nome: String,
    val indirizzo: String,
    val bandiera: String,
    val societa: String,
    val telefono: String,
    val email: String,
    val sitoWeb: String,
    val prezzi: List<PrezzoCarburante>,
    val servizi: List<Servizio>,
    val orari: List<String>,
)
