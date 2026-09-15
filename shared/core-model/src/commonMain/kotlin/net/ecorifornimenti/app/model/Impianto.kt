package net.ecorifornimenti.app.model

/**
 * Il prezzo di un carburante presso un impianto, come lo comunica il gestore.
 *
 * [nome] e' la descrizione commerciale ("Blue Diesel", "Hi-Q Diesel"): utile da
 * mostrare, ma per capire *quale* carburante sia va usato [fuelId], che e' l'unico
 * dato stabile.
 */
data class PrezzoCarburante(
    val fuelId: Int,
    val nome: String,
    val prezzo: Double,
    val self: Boolean,
    /**
     * Quando il gestore ha comunicato questo prezzo, in ISO 8601 e in UTC come lo
     * manda il servizio. Serve a dire all'utente quanto e' fresco il dato: un prezzo
     * di tre giorni fa e' un'informazione diversa da uno di stamattina.
     */
    val comunicatoIso: String? = null,
)

/**
 * Un distributore con i suoi prezzi, per come torna dalla ricerca per zona.
 *
 * `address` non e' popolato dalla ricerca (l'API lo restituisce sempre nullo):
 * arriva solo dal dettaglio, quindi qui e' opzionale e viene riempito dopo.
 */
data class Impianto(
    val id: Int,
    val nome: String,
    val bandiera: String,
    val posizione: Posizione,
    val prezzi: List<PrezzoCarburante>,
    /** Quando il gestore ha comunicato i prezzi. Alimenta il badge di freschezza. */
    val comunicatoIso: String? = null,
    val indirizzo: String? = null,
    /** Distanza in km dal punto di ricerca, calcolata da noi: vedi core-geo. */
    val distanzaKm: Double = 0.0,
) {
    /**
     * Il prezzo pertinente alla preferenza dell'utente, o `null` se l'impianto non
     * eroga quel carburante in quella modalita'.
     *
     * A parita' di carburante si sceglie il piu' basso: un impianto puo' esporre piu'
     * varianti dello stesso fuelId (self e servito, o prodotti premium).
     */
    fun prezzoPer(pref: PreferenzaRicerca): PrezzoCarburante? =
        prezzi.filter { it.fuelId == pref.tipo.fuelId && pref.modalita.accetta(it) }
            .minByOrNull { it.prezzo }
}

/** Fascia di convenienza rispetto al resto della zona: colora il marker sulla mappa. */
enum class FasciaPrezzo { CONVENIENTE, MEDIA, CARO }

/**
 * Assegna a ogni impianto la sua fascia confrontandolo con la **mediana** della zona
 * (piu' robusta della media: pochi impianti fuori scala non spostano il giudizio).
 * Sotto il 2% dalla mediana e' conveniente, sopra il 2% e' caro.
 */
fun fasce(
    impianti: List<Impianto>,
    pref: PreferenzaRicerca,
    sogliaRelativa: Double = 0.02,
): Map<Int, FasciaPrezzo> {
    val prezzi = impianti.mapNotNull { it.prezzoPer(pref)?.prezzo }.sorted()
    if (prezzi.isEmpty()) return emptyMap()
    val mediana = if (prezzi.size % 2 == 1) prezzi[prezzi.size / 2]
        else (prezzi[prezzi.size / 2 - 1] + prezzi[prezzi.size / 2]) / 2
    val basso = mediana * (1 - sogliaRelativa)
    val alto = mediana * (1 + sogliaRelativa)
    return impianti.mapNotNull { imp ->
        val p = imp.prezzoPer(pref)?.prezzo ?: return@mapNotNull null
        imp.id to when {
            p <= basso -> FasciaPrezzo.CONVENIENTE
            p >= alto -> FasciaPrezzo.CARO
            else -> FasciaPrezzo.MEDIA
        }
    }.toMap()
}
