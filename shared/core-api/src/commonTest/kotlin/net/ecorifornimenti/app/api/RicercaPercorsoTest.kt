package net.ecorifornimenti.app.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import net.ecorifornimenti.app.geo.Percorso
import net.ecorifornimenti.app.model.Posizione
import net.ecorifornimenti.app.model.PreferenzaRicerca
import net.ecorifornimenti.app.model.TipoCarburante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val MILANO = Posizione(45.4642, 9.19)
private val BOLOGNA = Posizione(44.4949, 11.3426)
private val JSON = headersOf(HttpHeaders.ContentType, "application/json")

/** Percorso finto: una strada dritta verso sud, dentro il tetto dei 50 km. */
private fun percorsoFinto(km: Double = 40.0) = Percorso(
    punti = (0..40).map { Posizione(45.4642 - it * 0.009, 9.19) },
    distanzaKm = km,
    durataMinuti = 45,
)

private class PercorsoFisso(private val percorso: Percorso = percorsoFinto()) : ServizioPercorso {
    var chiamate = 0
        private set

    override suspend fun calcola(partenza: Posizione, arrivo: Posizione): Percorso {
        chiamate++
        return percorso
    }
}

class RicercaSuPercorsoTest {

    private fun ricerca(
        percorsi: ServizioPercorso? = PercorsoFisso(),
        risposta: String = RispostaEsempio.zonaMilano,
        conta: (() -> Unit)? = null,
    ): RicercaImpianti {
        val engine = MockEngine {
            conta?.invoke()
            respond(risposta, HttpStatusCode.OK, JSON)
        }
        val client = OsservaprezziClient(engine, attesePerRitento = listOf(1), attendi = {})
        return RicercaImpianti(client, percorsi = percorsi)
    }

    @Test
    fun `tutto il viaggio in una sola chiamata al servizio`() = runTest {
        var chiamate = 0
        val r = ricerca(conta = { chiamate++ })

        val esito = r.cercaSuPercorso(MILANO, BOLOGNA, PreferenzaRicerca(TipoCarburante.BENZINA))

        // E' il vantaggio della ricerca su percorso: duecento chilometri non costano
        // una griglia di richieste, ne costano una.
        assertEquals(1, chiamate)
        assertTrue(esito.impianti.isNotEmpty())
        assertEquals(40.0, esito.percorso.distanzaKm)
    }

    @Test
    fun `i distributori sono ordinati dal piu' economico`() = runTest {
        val pref = PreferenzaRicerca(TipoCarburante.BENZINA)
        val esito = ricerca().cercaSuPercorso(MILANO, BOLOGNA, pref)

        val prezzi = esito.impianti.map { it.impianto.prezzoPer(pref)!!.prezzo }
        assertEquals(prezzi.sorted(), prezzi)
    }

    @Test
    fun `ogni distributore dice a che chilometro si trova`() = runTest {
        val esito = ricerca().cercaSuPercorso(MILANO, BOLOGNA, PreferenzaRicerca())

        // Sono tutti sulla strada o nei pressi: la progressiva sta dentro la tratta.
        assertTrue(esito.impianti.all { it.kmDallaPartenza >= 0.0 })
        assertTrue(esito.impianti.all { it.kmDallaPartenza <= esito.percorso.distanzaKm })
    }

    @Test
    fun `chi non eroga il carburante cercato non compare`() = runTest {
        val esito = ricerca().cercaSuPercorso(MILANO, BOLOGNA, PreferenzaRicerca(TipoCarburante.METANO))
        assertTrue(esito.impianti.isEmpty())
    }

    @Test
    fun `oltre i cinquanta chilometri si ferma e lo dice`() = runTest {
        // Il tetto si verifica sulla distanza stradale, non sulla linea d'aria.
        val lungo = PercorsoFisso(percorsoFinto(km = 51.0))
        assertFailsWith<ErroreRicerca.PercorsoTroppoLungo> {
            ricerca(percorsi = lungo).cercaSuPercorso(MILANO, BOLOGNA, PreferenzaRicerca())
        }
    }

    @Test
    fun `la stessa tratta non si richiede due volte`() = runTest {
        var chiamate = 0
        val r = ricerca(conta = { chiamate++ })
        val pref = PreferenzaRicerca(TipoCarburante.BENZINA)

        r.cercaSuPercorso(MILANO, BOLOGNA, pref)
        r.cercaSuPercorso(MILANO, BOLOGNA, pref)

        // Una strada non cambia in venti minuti, e nemmeno i prezzi: la seconda
        // ricerca deve venire dalla cache.
        assertEquals(1, chiamate)
    }

    @Test
    fun `ogni distributore dice anche quanto si esce dalla strada`() = runTest {
        val esito = ricerca().cercaSuPercorso(MILANO, BOLOGNA, PreferenzaRicerca())
        assertTrue(esito.impianti.all { it.deviazioneKm >= 0.0 })
        // Il corridoio del servizio e' di circa un chilometro per lato.
        assertTrue(esito.impianti.all { it.deviazioneKm < 50.0 })
    }

    @Test
    fun `senza un servizio di percorsi lo dice invece di fingere`() = runTest {
        assertFailsWith<ErroreRicerca.PercorsoNonTrovato> {
            ricerca(percorsi = null).cercaSuPercorso(MILANO, BOLOGNA, PreferenzaRicerca())
        }
    }

    @Test
    fun `la strada si chiede una volta sola per ricerca`() = runTest {
        val percorsi = PercorsoFisso()
        ricerca(percorsi = percorsi).cercaSuPercorso(MILANO, BOLOGNA, PreferenzaRicerca())
        assertEquals(1, percorsi.chiamate)
    }
}

class ServizioPercorsoTest {

    private val rispostaOsrm = """
    {"code":"Ok","routes":[{"distance":211100.5,"duration":8400.0,
      "geometry":{"coordinates":[[9.189,45.463],[9.5,45.2],[11.342,44.494]]}}]}
    """.trimIndent()

    @Test
    fun `legge distanza, durata e spezzata`() = runTest {
        val engine = MockEngine { respond(rispostaOsrm, HttpStatusCode.OK, JSON) }
        val percorso = PercorsoOsrm(engine).calcola(MILANO, BOLOGNA)

        assertEquals(211.1, percorso.distanzaKm, absoluteTolerance = 0.1)
        assertEquals(140, percorso.durataMinuti)
        assertEquals(3, percorso.punti.size)
        // GeoJSON mette la longitudine per prima: invertirle darebbe un percorso
        // in mezzo al mare, senza alcun errore.
        assertEquals(45.463, percorso.punti.first().lat, absoluteTolerance = 0.001)
        assertEquals(9.189, percorso.punti.first().lng, absoluteTolerance = 0.001)
    }

    @Test
    fun `se non c'e' strada lo dice`() = runTest {
        val engine = MockEngine { respond("""{"code":"NoRoute","routes":[]}""", HttpStatusCode.OK, JSON) }
        assertFailsWith<ErroreRicerca.PercorsoNonTrovato> { PercorsoOsrm(engine).calcola(MILANO, BOLOGNA) }
    }

    @Test
    fun `una caduta di rete resta una caduta di rete`() = runTest {
        val engine = MockEngine { throw RuntimeException("offline") }
        assertFailsWith<ErroreRicerca.Rete> { PercorsoOsrm(engine).calcola(MILANO, BOLOGNA) }
    }
}
