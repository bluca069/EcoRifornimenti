package net.ecorifornimenti.app.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.ecorifornimenti.app.model.Posizione
import net.ecorifornimenti.app.model.PreferenzaRicerca
import net.ecorifornimenti.app.model.TipoCarburante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val MILANO = Posizione(45.4642, 9.19)
private val JSON_HEADER = headersOf(HttpHeaders.ContentType, "application/json")

/** Conta le chiamate e risponde secondo una regola, per osservare cosa fa la ricerca. */
private class ServizioFinto(val risposta: (Int) -> Pair<HttpStatusCode, String>) {
    var chiamate = 0
        private set

    fun client(): OsservaprezziClient {
        val engine = MockEngine {
            val (stato, corpo) = risposta(chiamate)
            chiamate++
            respond(corpo, stato, JSON_HEADER)
        }
        return OsservaprezziClient(engine, attesePerRitento = listOf(1), attendi = {})
    }
}

class RicercaImpiantiTest {

    @Test
    fun `a dieci km una sola chiamata e un solo aggiornamento`() = runTest {
        val servizio = ServizioFinto { HttpStatusCode.OK to RispostaEsempio.zonaMilano }
        val ricerca = RicercaImpianti(servizio.client())

        val passi = ricerca.cerca(MILANO, PreferenzaRicerca(raggioKm = 10)).toList()

        assertEquals(1, passi.size, "il caso istantaneo non deve emettere piu' volte")
        assertEquals(1, servizio.chiamate)
        assertTrue(passi.single().completata)
        assertEquals(1f, passi.single().frazione)
    }

    @Test
    fun `a venticinque km la prima emissione arriva prima di finire`() = runTest {
        val servizio = ServizioFinto { HttpStatusCode.OK to RispostaEsempio.zonaMilano }
        val ricerca = RicercaImpianti(servizio.client())

        val passi = ricerca.cerca(MILANO, PreferenzaRicerca(raggioKm = 25)).toList()

        assertTrue(passi.size > 1, "gli anelli esterni devono aggiornare la mappa")
        // Il primo aggiornamento ha gia' dei risultati da mostrare: e' il punto di tutto.
        assertTrue(passi.first().impianti.isNotEmpty())
        assertEquals(1, passi.first().celleCompletate)
        assertTrue(!passi.first().completata)
        assertTrue(passi.last().completata)
        assertEquals(passi.last().celleTotali, passi.last().celleCompletate)
    }

    @Test
    fun `gli impianti ripetuti fra celle vicine si contano una volta sola`() = runTest {
        val servizio = ServizioFinto { HttpStatusCode.OK to RispostaEsempio.zonaMilano }
        val ricerca = RicercaImpianti(servizio.client())

        val esito = ricerca.cercaTutto(MILANO, PreferenzaRicerca(raggioKm = 25))

        assertEquals(esito.map { it.id }.distinct(), esito.map { it.id })
    }

    @Test
    fun `scarta chi e' oltre il raggio chiesto dall'utente`() = runTest {
        // La cella centrale risponde Milano, tutte le altre un impianto a 30 km.
        val servizio = ServizioFinto { n ->
            HttpStatusCode.OK to if (n == 0) RispostaEsempio.zonaMilano else RispostaEsempio.zonaLontana
        }
        val ricerca = RicercaImpianti(servizio.client())

        val esito = ricerca.cercaTutto(MILANO, PreferenzaRicerca(raggioKm = 25))

        assertTrue(esito.none { it.id == 70001 }, "l'impianto a 30 km non deve comparire")
        assertTrue(esito.all { it.distanzaKm <= 25 })
    }

    @Test
    fun `ordina per prezzo crescente del carburante scelto`() = runTest {
        val servizio = ServizioFinto { HttpStatusCode.OK to RispostaEsempio.zonaMilano }
        val ricerca = RicercaImpianti(servizio.client())
        val pref = PreferenzaRicerca(TipoCarburante.BENZINA, raggioKm = 10)

        val esito = ricerca.cercaTutto(MILANO, pref)

        val prezzi = esito.map { it.prezzoPer(pref)!!.prezzo }
        assertEquals(prezzi.sorted(), prezzi)
        assertEquals(1.765, prezzi.first(), "il piu' conveniente deve essere in testa")
    }

    @Test
    fun `la distanza e' quella dall'utente non quella dichiarata dall'API`() = runTest {
        val servizio = ServizioFinto { HttpStatusCode.OK to RispostaEsempio.zonaMilano }
        val ricerca = RicercaImpianti(servizio.client())

        val tamoil = ricerca.cercaTutto(MILANO, PreferenzaRicerca(raggioKm = 10))
            .first { it.id == 33860 }

        // L'API dichiarava 4.19 km rispetto al centro della sua cella; da MILANO sono ~3.7.
        assertTrue(tamoil.distanzaKm in 3.0..4.0, "distanza ricalcolata: ${tamoil.distanzaKm}")
    }

    @Test
    fun `se cade un anello esterno la mappa resta utilizzabile`() = runTest {
        val servizio = ServizioFinto { n ->
            if (n == 0) HttpStatusCode.OK to RispostaEsempio.zonaMilano
            else HttpStatusCode.InternalServerError to ""
        }
        val ricerca = RicercaImpianti(servizio.client())

        val esito = ricerca.cercaTutto(MILANO, PreferenzaRicerca(raggioKm = 25))

        assertEquals(2, esito.size, "restano i risultati della cella centrale")
    }

    @Test
    fun `se cade la prima cella l'errore arriva all'utente`() = runTest {
        val servizio = ServizioFinto { HttpStatusCode.InternalServerError to "" }
        val ricerca = RicercaImpianti(servizio.client())

        assertFailsWith<ErroreRicerca.RispostaInattesa> {
            ricerca.cercaTutto(MILANO, PreferenzaRicerca(raggioKm = 25))
        }
    }

    @Test
    fun `nessun risultato non e' un errore`() = runTest {
        val servizio = ServizioFinto { HttpStatusCode.OK to RispostaEsempio.zonaVuota }
        val ricerca = RicercaImpianti(servizio.client())

        assertTrue(ricerca.cercaTutto(MILANO, PreferenzaRicerca(raggioKm = 10)).isEmpty())
    }

    @Test
    fun `ripetere la stessa ricerca non richiama il servizio`() = runTest {
        val servizio = ServizioFinto { HttpStatusCode.OK to RispostaEsempio.zonaMilano }
        val ricerca = RicercaImpianti(servizio.client())
        val pref = PreferenzaRicerca(raggioKm = 10)

        ricerca.cercaTutto(MILANO, pref)
        val dopoLaPrima = servizio.chiamate
        ricerca.cercaTutto(MILANO, pref)

        assertEquals(dopoLaPrima, servizio.chiamate, "la seconda ricerca deve venire dalla cache")
    }

    @Test
    fun `cambiare carburante rifa' la ricerca`() = runTest {
        val servizio = ServizioFinto { HttpStatusCode.OK to RispostaEsempio.zonaMilano }
        val ricerca = RicercaImpianti(servizio.client())

        ricerca.cercaTutto(MILANO, PreferenzaRicerca(TipoCarburante.BENZINA, raggioKm = 10))
        val dopoBenzina = servizio.chiamate
        ricerca.cercaTutto(MILANO, PreferenzaRicerca(TipoCarburante.GASOLIO, raggioKm = 10))

        assertTrue(servizio.chiamate > dopoBenzina, "il filtro fa parte della chiave di cache")
    }
}

class CacheRicercaTest {

    @Test
    fun `la voce scade e viene ricalcolata`() = runTest {
        var adesso = 0L
        val cache = CacheRicerca(durataMs = 1_000, adesso = { adesso })
        var calcoli = 0
        suspend fun leggi() = cache.oppure(MILANO, "1-1") { calcoli++; emptyList() }

        leggi(); leggi()
        assertEquals(1, calcoli)

        adesso = 1_001
        leggi()
        assertEquals(2, calcoli, "scaduta la voce, il calcolo va rifatto")
    }

    @Test
    fun `centri quasi coincidenti condividono la voce`() = runTest {
        val cache = CacheRicerca()
        var calcoli = 0
        cache.oppure(Posizione(45.46420, 9.19000), null) { calcoli++; emptyList() }
        // ~1 metro di scarto: e' la stessa cella, non una nuova.
        cache.oppure(Posizione(45.464201, 9.190001), null) { calcoli++; emptyList() }
        assertEquals(1, calcoli)
    }
}
