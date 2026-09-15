package net.ecorifornimenti.app.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource
import net.ecorifornimenti.app.model.Impianto
import net.ecorifornimenti.app.model.Posizione
import kotlin.math.round

/**
 * Ricorda il risultato di ogni cella gia' interrogata, per un po'.
 *
 * I gestori comunicano i prezzi al massimo qualche volta al giorno: rifare la stessa
 * ricerca dopo un minuto non porterebbe un dato diverso, porterebbe solo carico a un
 * servizio pubblico. Tornare indietro e riaprire la mappa, o cambiare raggio, qui
 * diventa istantaneo.
 */
class CacheRicerca(
    private val durataMs: Long = DURATA_PREDEFINITA_MS,
    private val adesso: () -> Long = orologioMonotono(),
) {
    private data class Voce(val impianti: List<Impianto>, val scadenza: Long)

    private val mutex = Mutex()
    private val voci = mutableMapOf<String, Voce>()

    /** Il valore in cache se ancora valido, altrimenti calcola, memorizza e restituisce. */
    suspend fun oppure(
        centro: Posizione,
        fuelType: String?,
        calcola: suspend () -> List<Impianto>,
    ): List<Impianto> {
        val k = chiave(centro, fuelType)
        mutex.withLock {
            voci[k]?.let { if (it.scadenza > adesso()) return it.impianti else voci.remove(k) }
        }
        // Il calcolo sta fuori dal lock: e' una chiamata di rete, bloccare la cache
        // per tutta la sua durata serializzerebbe le celle che vogliamo in parallelo.
        val esito = calcola()
        mutex.withLock { voci[k] = Voce(esito, adesso() + durataMs) }
        return esito
    }

    suspend fun svuota() = mutex.withLock { voci.clear() }

    /**
     * I centri di griglia sono calcolati, non digitati: arrotondarli a ~11 metri li
     * rende stabili fra una ricerca e l'altra senza accorpare celle diverse.
     */
    private fun chiave(p: Posizione, fuelType: String?): String =
        "${arrotonda(p.lat)},${arrotonda(p.lng)},${fuelType ?: "*"}"

    private fun arrotonda(v: Double): Double = round(v * 10_000) / 10_000

    companion object {
        const val DURATA_PREDEFINITA_MS = 20 * 60 * 1000L
    }
}

/**
 * Millisecondi trascorsi dall'avvio dell'app. Monotono per scelta: la scadenza di una
 * voce non deve poter saltare se l'orologio di sistema viene corretto o cambia fuso.
 * Nei test si passa una lambda al posto suo.
 */
internal fun orologioMonotono(): () -> Long {
    val avvio = TimeSource.Monotonic.markNow()
    return { avvio.elapsedNow().inWholeMilliseconds }
}
