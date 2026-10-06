package net.ecorifornimenti.app.geo

import net.ecorifornimenti.app.model.Posizione
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val MILANO = Posizione(45.4642, 9.19)

/** Una finta strada dritta verso nord, un punto ogni 100 metri. */
private fun stradaDritta(km: Double): List<Posizione> =
    (0..(km * 10).toInt()).map { MILANO.spostato(nordKm = it * 0.1, estKm = 0.0) }

class CampionamentoPercorsoTest {

    @Test
    fun `tiene un punto ogni due chilometri`() {
        val campionati = campionaPercorso(stradaDritta(20.0))
        // Venti chilometri a passo di due, piu' partenza e arrivo.
        assertTrue(campionati.size in 10..12, "punti: ${campionati.size}")
    }

    @Test
    fun `partenza e arrivo non si perdono mai`() {
        val strada = stradaDritta(7.3)
        val campionati = campionaPercorso(strada)
        assertEquals(strada.first(), campionati.first())
        assertEquals(strada.last(), campionati.last())
    }

    @Test
    fun `nessun buco piu' largo del corridoio cercato`() {
        val campionati = campionaPercorso(stradaDritta(50.0))
        val salti = campionati.zipWithNext { a, b -> distanzaKm(a, b) }
        // Il corridoio misurato e' di circa un chilometro per lato: con salti entro i
        // due chilometri e mezzo la copertura resta continua.
        assertTrue(salti.all { it <= 2.5 }, "salto massimo: ${salti.max()}")
    }

    @Test
    fun `un percorso di due punti resta com'e'`() {
        val minimo = listOf(MILANO, MILANO.spostato(30.0, 0.0))
        assertEquals(minimo, campionaPercorso(minimo))
    }

    @Test
    fun `il passo si puo' stringere`() {
        val fitti = campionaPercorso(stradaDritta(20.0), passoKm = 0.5)
        val radi = campionaPercorso(stradaDritta(20.0), passoKm = 5.0)
        assertTrue(fitti.size > radi.size)
    }

    @Test
    fun `cinquanta chilometri stanno in una ventina di punti`() {
        // E' il dimensionamento che tiene la ricerca in una chiamata sola: il servizio
        // ne ha gia' accettati 88 senza storie.
        val campionati = campionaPercorso(stradaDritta(50.0))
        assertTrue(campionati.size <= 30, "punti per 50 km: ${campionati.size}")
    }
}

class DeviazioneDalPercorsoTest {

    @Test
    fun `dice quanto si esce dalla strada`() {
        val strada = campionaPercorso(stradaDritta(50.0))
        val impianto = MILANO.spostato(nordKm = 20.0, estKm = 0.4)

        val deviazione = deviazioneDalPercorso(strada, impianto)

        assertTrue(deviazione in 0.35..0.45, "deviazione calcolata: $deviazione")
    }

    @Test
    fun `la misura e' sui segmenti, non sui vertici`() {
        // Punto a 400 metri dalla strada, a meta' fra due vertici campionati: sui soli
        // vertici risulterebbe a oltre un chilometro, cioe' tre volte tanto.
        val strada = campionaPercorso(stradaDritta(50.0))
        val impianto = MILANO.spostato(nordKm = 21.0, estKm = 0.4)

        val punto = proiettaSulPercorso(strada, impianto)

        assertTrue(punto.deviazioneKm in 0.35..0.45, "deviazione: ${punto.deviazioneKm}")
        assertTrue(punto.progressivaKm in 20.5..21.5, "progressiva: ${punto.progressivaKm}")
    }

    @Test
    fun `sulla strada la deviazione e' quasi nulla`() {
        val strada = campionaPercorso(stradaDritta(50.0))
        assertTrue(deviazioneDalPercorso(strada, MILANO.spostato(20.0, 0.0)) < 0.1)
    }

    @Test
    fun `un percorso vuoto non fa danni`() {
        assertEquals(0.0, deviazioneDalPercorso(emptyList(), MILANO))
    }
}

class ProgressivaSulPercorsoTest {

    @Test
    fun `dice a che chilometro si trova un distributore`() {
        val strada = campionaPercorso(stradaDritta(100.0))
        // Un distributore a 300 metri dalla strada, all'altezza del km 40.
        val impianto = MILANO.spostato(nordKm = 40.0, estKm = 0.3)

        val km = progressivaSulPercorso(strada, impianto)

        assertTrue(km in 39.0..41.0, "progressiva calcolata: $km")
    }

    @Test
    fun `all'inizio del percorso la progressiva e' zero`() {
        val strada = campionaPercorso(stradaDritta(50.0))
        assertTrue(progressivaSulPercorso(strada, MILANO) < 1.0)
    }

    @Test
    fun `in fondo al percorso vale quasi tutta la lunghezza`() {
        val strada = campionaPercorso(stradaDritta(50.0))
        val km = progressivaSulPercorso(strada, MILANO.spostato(49.5, 0.0))
        assertTrue(km > 48.0, "progressiva calcolata: $km")
    }

    @Test
    fun `un percorso vuoto non fa danni`() {
        assertEquals(0.0, progressivaSulPercorso(emptyList(), MILANO))
    }
}
