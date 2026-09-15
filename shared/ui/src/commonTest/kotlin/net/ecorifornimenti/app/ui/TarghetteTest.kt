package net.ecorifornimenti.app.ui

import net.ecorifornimenti.app.geo.spostato
import net.ecorifornimenti.app.model.Impianto
import net.ecorifornimenti.app.model.Posizione
import net.ecorifornimenti.app.model.PrezzoCarburante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val MILANO = Posizione(45.4642, 9.19)

private fun impiantoA(id: Int, prezzo: Double, nordKm: Double, estKm: Double = 0.0) = Impianto(
    id = id,
    nome = "Impianto $id",
    bandiera = "",
    posizione = MILANO.spostato(nordKm, estKm),
    prezzi = listOf(PrezzoCarburante(1, "Benzina", prezzo, true)),
)

class TargletteTest {

    @Test
    fun `i piu' convenienti hanno la precedenza`() {
        // Ben distanziati: passano tutti, nell'ordine in cui arrivano (per prezzo).
        val impianti = (0..4).map { impiantoA(it, 1.8 + it * 0.01, nordKm = it * 4.0) }
        val scelti = impiantiConTarghetta(impianti, raggioKm = 10)
        assertEquals(setOf(0, 1, 2, 3, 4), scelti)
    }

    @Test
    fun `due distributori troppo vicini non prendono due targhette`() {
        val impianti = listOf(
            impiantoA(1, 1.800, nordKm = 0.0),
            // A 200 metri: le targhette si sovrapporrebbero e non si leggerebbe nessuna.
            impiantoA(2, 1.850, nordKm = 0.2),
            impiantoA(3, 1.900, nordKm = 5.0),
        )
        val scelti = impiantiConTarghetta(impianti, raggioKm = 10)
        assertTrue(1 in scelti, "il piu' conveniente dei due vicini resta")
        assertTrue(2 !in scelti, "il vicino piu' caro diventa un pallino")
        assertTrue(3 in scelti)
    }

    @Test
    fun `a raggio largo serve piu' distanza fra le targhette`() {
        val impianti = listOf(
            impiantoA(1, 1.800, nordKm = 0.0),
            impiantoA(2, 1.850, nordKm = 3.0),
        )
        // A 10 km di raggio tre km bastano; a 25, sullo schermo, sarebbero appiccicate.
        assertEquals(setOf(1, 2), impiantiConTarghetta(impianti, raggioKm = 10))
        assertEquals(setOf(1), impiantiConTarghetta(impianti, raggioKm = 25))
    }

    @Test
    fun `non si superano mai le targhette previste`() {
        val impianti = (0..40).map { impiantoA(it, 1.8 + it * 0.001, nordKm = it * 4.0) }
        assertTrue(impiantiConTarghetta(impianti, raggioKm = 10).size <= MASSIME_TARGHETTE)
    }

    @Test
    fun `lista vuota non produce targhette`() {
        assertTrue(impiantiConTarghetta(emptyList(), raggioKm = 10).isEmpty())
    }
}
