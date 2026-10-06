package net.ecorifornimenti.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val ADESSO = Instant.parse("2026-10-06T12:00:00Z")

private fun impianto(comunicato: String?) = Impianto(
    id = 1,
    nome = "Prova",
    bandiera = "",
    posizione = Posizione(45.0, 9.0),
    prezzi = listOf(PrezzoCarburante(1, "Benzina", 1.899, true)),
    comunicatoIso = comunicato,
)

class FreschezzaTest {

    @Test
    fun `conta i giorni dalla comunicazione`() {
        assertEquals(0, impianto("2026-10-06T07:00:00Z").giorniDallaComunicazione(ADESSO))
        assertEquals(3, impianto("2026-10-03T10:00:00Z").giorniDallaComunicazione(ADESSO))
        // Il fuso va letto: le 14:00 italiane sono le 12:00 UTC, non il giorno prima.
        assertEquals(0, impianto("2026-10-06T14:00:00+02:00").giorniDallaComunicazione(ADESSO))
    }

    @Test
    fun `una data assente o illeggibile non si conta`() {
        assertNull(impianto(null).giorniDallaComunicazione(ADESSO))
        assertNull(impianto("l'altro ieri").giorniDallaComunicazione(ADESSO))
    }

    @Test
    fun `il filtro tiene i prezzi entro la soglia`() {
        val recente = impianto("2026-10-04T12:00:00Z")
        assertTrue(recente.comunicatoDaMenoDi(10, ADESSO))
        assertTrue(recente.comunicatoDaMenoDi(2, ADESSO))
        assertTrue(!recente.comunicatoDaMenoDi(1, ADESSO))
    }

    @Test
    fun `chi non dichiara la data resta in elenco`() {
        // Non sapere quando e' stato comunicato un prezzo e' diverso dal sapere che e'
        // vecchio: scartarlo nasconderebbe distributori che magari costano meno.
        assertTrue(impianto(null).comunicatoDaMenoDi(1, ADESSO))
    }

    @Test
    fun `una data nel futuro vale come appena comunicata`() {
        // Orologi storti e fusi sbagliati capitano: meglio mostrarlo che perderlo.
        assertEquals(0, impianto("2026-10-07T12:00:00Z").giorniDallaComunicazione(ADESSO))
        assertTrue(impianto("2026-10-07T12:00:00Z").comunicatoDaMenoDi(1, ADESSO))
    }

    @Test
    fun `il predefinito e' dieci giorni`() {
        assertEquals(10, PreferenzaRicerca().freschezzaGiorni)
        assertEquals(1, PreferenzaRicerca.FRESCHEZZA_MINIMA_GIORNI)
        assertEquals(10, PreferenzaRicerca.FRESCHEZZA_MASSIMA_GIORNI)
    }
}
