package net.ecorifornimenti.app.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

/** Risposta vera di Nominatim per "Bologna", accorciata. */
/** Risposta vera di Photon per "siena", accorciata. */
private const val RISPOSTA = """
{"features":[
 {"properties":{"name":"Siena","county":"Siena","state":"Toscana","countrycode":"IT"},
  "geometry":{"coordinates":[11.3317, 43.3186]}},
 {"properties":{"name":"Siena","county":"Siena","state":"Toscana","countrycode":"IT"},
  "geometry":{"coordinates":[11.4676, 43.1672]}},
 {"properties":{"name":"Montalcino","county":"Siena","state":"Toscana"},
  "geometry":{"coordinates":[11.4894, 43.0567]}},
 {"properties":{"name":"Senza coordinate","state":"Toscana"},"geometry":{"coordinates":[]}},
 {"properties":{"state":"Toscana"},"geometry":{"coordinates":[11.0, 43.0]}}
]}
"""

class ServizioLuoghiTest {

    private fun servizio(corpo: String = RISPOSTA, stato: HttpStatusCode = HttpStatusCode.OK) =
        LuoghiPhoton(MockEngine { respond(corpo, stato, JSON_HEADERS) })

    @Test
    fun `trova un luogo, le sue coordinate e dove si trova`() = runTest {
        val trovati = servizio().cerca("siena")

        // Due risultati: i due "Siena" sono doppioni per chi sceglie una
        // destinazione; scartati quello senza coordinate e quello senza nome, che
        // sulla mappa finirebbero al largo dell'Africa.
        assertEquals(2, trovati.size)
        assertEquals("Siena", trovati.first().nome)
        // Il "dove" non ripete il nome: per Siena-citta' resta la sola regione.
        assertEquals("Toscana", trovati.first().dove)
        assertEquals("Siena, Toscana", trovati[1].dove, "per Montalcino serve la provincia")
        // GeoJSON mette la longitudine per prima: invertirle porterebbe in Somalia.
        assertEquals(43.3186, trovati.first().posizione.lat)
        assertEquals(11.3317, trovati.first().posizione.lng)
        assertEquals("Montalcino", trovati[1].nome)
    }

    @Test
    fun `non cerca per due lettere`() = runTest {
        var chiamate = 0
        val servizio = LuoghiPhoton(
            MockEngine { chiamate++; respond(RISPOSTA, HttpStatusCode.OK, JSON_HEADERS) }
        )

        assertTrue(servizio.cerca("Si").isEmpty())
        assertTrue(servizio.cerca("  ").isEmpty())

        // Photon e' infrastruttura pubblica a uso equo: non la si interroga per nulla.
        assertEquals(0, chiamate)
    }

    @Test
    fun `una caduta di rete resta una caduta di rete`() = runTest {
        val servizio = LuoghiPhoton(MockEngine { throw RuntimeException("offline") })
        assertFailsWith<ErroreRicerca.Rete> { servizio.cerca("Siena") }
    }

    @Test
    fun `un errore del servizio non passa per risultato vuoto`() = runTest {
        assertFailsWith<ErroreRicerca.RispostaInattesa> {
            servizio(corpo = "", stato = HttpStatusCode.ServiceUnavailable).cerca("Siena")
        }
    }
}
