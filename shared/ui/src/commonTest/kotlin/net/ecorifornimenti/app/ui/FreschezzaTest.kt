package net.ecorifornimenti.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** 14/09/2026 alle 20:15 in Italia, che il servizio manda come 18:15 UTC. */
private val COMUNICATO = "2026-09-14T18:15:43Z"

class FreschezzaTest {

    @Test
    fun `mostra data e ora della comunicazione`() {
        val testo = formattaComunicazione(COMUNICATO, adesso = Instant.parse("2026-09-14T20:00:00Z"))
        assertTrue(testo != null)
        assertTrue(testo!!.startsWith("14/09/2026 alle "), testo)
        assertTrue(testo.endsWith("(oggi)"), testo)
    }

    @Test
    fun `dice ieri, i giorni, e avvisa quando il dato e' vecchio`() {
        fun quando(adesso: String) = formattaComunicazione(COMUNICATO, Instant.parse(adesso))!!

        assertTrue(quando("2026-09-15T20:00:00Z").endsWith("(ieri)"))
        assertTrue(quando("2026-09-17T20:00:00Z").endsWith("(3 giorni fa)"))
        assertTrue(quando("2026-09-30T20:00:00Z").endsWith("(oltre una settimana fa)"))
    }

    @Test
    fun `una data assente o illeggibile non produce una riga`() {
        assertNull(formattaComunicazione(null))
        assertNull(formattaComunicazione(""))
        // L'API non e' documentata: se cambia formato, meglio nessuna riga che una
        // data inventata.
        assertNull(formattaComunicazione("ieri pomeriggio"))
    }

    @Test
    fun `fra piu' comunicazioni vince la piu' recente`() {
        val date = listOf("2026-09-13T10:00:00Z", null, "2026-09-14T18:15:43Z", "")
        assertEquals("2026-09-14T18:15:43Z", ultimaComunicazione(date))
    }

    @Test
    fun `senza date non c'e' nulla da mostrare`() {
        assertNull(ultimaComunicazione(listOf(null, "")))
    }
}
