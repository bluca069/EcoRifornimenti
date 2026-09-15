package net.ecorifornimenti.app.geo

import net.ecorifornimenti.app.model.Posizione
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val MILANO = Posizione(45.4642, 9.19)

class GrigliaRicercaTest {

    @Test
    fun `entro dieci km basta una sola chiamata`() {
        assertEquals(listOf(MILANO), grigliaRicerca(MILANO, 10))
        assertEquals(1, grigliaRicerca(MILANO, 5).size)
    }

    @Test
    fun `la prima cella e' sempre quella dell'utente`() {
        val g = grigliaRicerca(MILANO, 25)
        assertTrue(distanzaKm(MILANO, g.first()) < 0.001, "la prima cella deve essere il centro")
    }

    /**
     * Il vincolo che conta: nessun punto dell'area cercata deve restare scoperto,
     * altrimenti sulla mappa mancherebbero dei distributori senza che si veda.
     */
    @Test
    fun `la griglia copre tutto il disco richiesto`() {
        for (raggio in listOf(15, 25)) {
            val celle = grigliaRicerca(MILANO, raggio)
            var scoperti = 0
            // Campionamento a maglia di 1 km su tutto il quadrato che contiene il disco.
            var nord = -raggio.toDouble()
            while (nord <= raggio) {
                var est = -raggio.toDouble()
                while (est <= raggio) {
                    val p = MILANO.spostato(nord, est)
                    if (distanzaKm(MILANO, p) <= raggio) {
                        val coperto = celle.any { distanzaKm(it, p) <= RAGGIO_MAX_API_KM }
                        if (!coperto) scoperti++
                    }
                    est += 1.0
                }
                nord += 1.0
            }
            assertEquals(0, scoperti, "raggio $raggio km: $scoperti punti scoperti")
        }
    }

    /**
     * Ogni cella e' una chiamata alla rete: se il conteggio si gonfia, l'attesa e il
     * carico sul servizio pubblico crescono con lui. I limiti sono quelli misurati.
     */
    @Test
    fun `il numero di chiamate resta contenuto`() {
        assertTrue(grigliaRicerca(MILANO, 15).size <= 8, "15 km: ${grigliaRicerca(MILANO, 15).size} celle")
        assertTrue(grigliaRicerca(MILANO, 25).size <= 20, "25 km: ${grigliaRicerca(MILANO, 25).size} celle")
    }

    @Test
    fun `le celle sono ordinate dal centro verso l'esterno`() {
        val celle = grigliaRicerca(MILANO, 25)
        val distanze = celle.map { distanzaKm(MILANO, it) }
        assertEquals(distanze.sorted(), distanze)
    }
}

class DistanzaTest {

    @Test
    fun `distanza nota fra Milano e Torino`() {
        val torino = Posizione(45.0703, 7.6869)
        val d = distanzaKm(MILANO, torino)
        assertTrue(d in 118.0..128.0, "attesi ~123 km, ottenuti $d")
    }

    @Test
    fun `lo spostamento e' coerente con la distanza`() {
        val p = MILANO.spostato(nordKm = 10.0, estKm = 0.0)
        assertTrue(distanzaKm(MILANO, p) in 9.9..10.1)
        val q = MILANO.spostato(nordKm = 0.0, estKm = 7.0)
        assertTrue(distanzaKm(MILANO, q) in 6.9..7.1)
    }

    @Test
    fun `distanza nulla su se stesso`() {
        assertEquals(0.0, distanzaKm(MILANO, MILANO))
    }
}
