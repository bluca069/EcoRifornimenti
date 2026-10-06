package net.ecorifornimenti.app.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.ecorifornimenti.app.model.Impianto
import net.ecorifornimenti.app.model.Posizione
import kotlin.math.round

/**
 * Ricorda i distributori gia' trovati lungo una tratta.
 *
 * E' la gemella di [CacheRicerca], con la chiave presa dai punti campionati invece che
 * dal centro: la stessa strada, con lo stesso carburante, non va richiesta due volte in
 * venti minuti — ne' all'Osservaprezzi ne' a chi calcola il percorso.
 */
class CacheTratte(
    private val durataMs: Long = CacheRicerca.DURATA_PREDEFINITA_MS,
    private val adesso: () -> Long = orologioMonotono(),
) {
    private data class Voce(val impianti: List<Impianto>, val scadenza: Long)

    private val mutex = Mutex()
    private val voci = mutableMapOf<String, Voce>()

    suspend fun oppure(
        punti: List<Posizione>,
        fuelType: String?,
        calcola: suspend () -> List<Impianto>,
    ): List<Impianto> {
        val k = chiave(punti, fuelType)
        mutex.withLock {
            voci[k]?.let { if (it.scadenza > adesso()) return it.impianti else voci.remove(k) }
        }
        // Fuori dal lock: e' una chiamata di rete, e tenerlo bloccato fermerebbe
        // qualunque altra ricerca nel frattempo.
        val esito = calcola()
        mutex.withLock { voci[k] = Voce(esito, adesso() + durataMs) }
        return esito
    }

    suspend fun svuota() = mutex.withLock { voci.clear() }

    /**
     * La tratta si riconosce dai suoi estremi, dalla lunghezza e da qualche punto di
     * mezzo: confrontare tutta la polilinea costerebbe senza distinguere di piu'.
     */
    private fun chiave(punti: List<Posizione>, fuelType: String?): String {
        if (punti.isEmpty()) return "vuota,${fuelType ?: "*"}"
        val campione = listOf(punti.first(), punti[punti.size / 2], punti.last())
        return campione.joinToString(";") { "${arrotonda(it.lat)},${arrotonda(it.lng)}" } +
            "|${punti.size}|${fuelType ?: "*"}"
    }

    private fun arrotonda(v: Double): Double = round(v * 1_000) / 1_000
}
