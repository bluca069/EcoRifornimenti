package net.ecorifornimenti.app.api

import kotlinx.coroutines.runBlocking
import net.ecorifornimenti.app.model.Posizione
import net.ecorifornimenti.app.model.PreferenzaRicerca
import net.ecorifornimenti.app.model.TipoCarburante
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Parla davvero con l'Osservaprezzi: serve a scoprire se il servizio ha cambiato
 * forma, cosa che puo' succedere senza preavviso perche' l'API non e' documentata.
 *
 * Spento di default — un test che dipende dalla rete non puo' decidere se una build
 * e' verde, e ogni esecuzione e' carico su un servizio pubblico. Si accende con:
 *
 *     ./gradlew :shared:core-api:jvmTest -Dintegrazione=true --rerun-tasks
 */
class IntegrazioneOsservaprezziTest {

    private val attivo = System.getProperty("integrazione") == "true"

    @Test
    fun `la ricerca per zona risponde ancora come ci aspettiamo`() {
        if (!attivo) return
        val client = OsservaprezziClient(engineHttpPredefinito())
        try {
            val impianti = runBlocking { client.cercaPerZona(DUOMO_MILANO, "1-1") }
            assertTrue(impianti.isNotEmpty(), "nessun impianto in centro a Milano: sospetto")
            assertTrue(impianti.all { it.prezzi.isNotEmpty() })
            assertTrue(impianti.any { it.bandiera.isNotEmpty() })
            println("[integrazione] zona: ${impianti.size} impianti, " +
                "primo = ${impianti.first().nome} ${impianti.first().prezzi.first().prezzo}")
        } finally {
            client.chiudi()
        }
    }

    @Test
    fun `il dettaglio porta l'indirizzo che la ricerca non da'`() {
        if (!attivo) return
        val client = OsservaprezziClient(engineHttpPredefinito())
        try {
            val id = runBlocking { client.cercaPerZona(DUOMO_MILANO).first().id }
            val dettaglio = runBlocking { client.dettaglio(id) }
            assertTrue(dettaglio.indirizzo.isNotEmpty(), "il dettaglio deve avere l'indirizzo")
            println("[integrazione] dettaglio $id: ${dettaglio.indirizzo}")
        } finally {
            client.chiudi()
        }
    }

    /**
     * La ricerca a raggio ampio, come la fa l'app: verifica che la griglia e il limite
     * di richieste parallele reggano sul servizio vero, senza incassare un 429.
     */
    @Test
    fun `la ricerca a venticinque km non incassa rate limit`() {
        if (!attivo) return
        val client = OsservaprezziClient(engineHttpPredefinito())
        try {
            val ricerca = RicercaImpianti(client)
            val pref = PreferenzaRicerca(TipoCarburante.GASOLIO, raggioKm = 25)
            val esito = runBlocking { ricerca.cercaTutto(DUOMO_MILANO, pref) }
            assertTrue(esito.size > 50, "attesi molti impianti su 25 km, trovati ${esito.size}")
            assertTrue(esito.all { it.distanzaKm <= 25 })
            val prezzi = esito.map { it.prezzoPer(pref)!!.prezzo }
            assertTrue(prezzi == prezzi.sorted())
            println("[integrazione] 25 km: ${esito.size} impianti, " +
                "da ${prezzi.first()} a ${prezzi.last()}")
        } finally {
            client.chiudi()
        }
    }

    private companion object {
        val DUOMO_MILANO = Posizione(45.4642, 9.19)
    }
}
