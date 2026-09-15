package net.ecorifornimenti.app.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import net.ecorifornimenti.app.model.ModalitaErogazione
import net.ecorifornimenti.app.model.Posizione
import net.ecorifornimenti.app.model.PreferenzaRicerca
import net.ecorifornimenti.app.model.TipoCarburante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val MILANO = Posizione(45.46, 9.19)
private val JSON_HEADER = headersOf(HttpHeaders.ContentType, "application/json")

private fun clientCon(
    vararg risposte: Pair<HttpStatusCode, String>,
    attese: MutableList<Long> = mutableListOf(),
): OsservaprezziClient {
    var i = 0
    val engine = MockEngine {
        val (stato, corpo) = risposte[minOf(i, risposte.size - 1)]
        i++
        respond(corpo, stato, JSON_HEADER)
    }
    return OsservaprezziClient(
        engine = engine,
        attesePerRitento = listOf(1, 2, 3),
        attendi = { attese += it },
    )
}

class OsservaprezziClientTest {

    @Test
    fun `legge gli impianti da una risposta reale`() = runTest {
        val client = clientCon(HttpStatusCode.OK to RispostaEsempio.zonaMilano)
        val impianti = client.cercaPerZona(MILANO)

        // I due impianti buoni; scartati quello senza posizione e quello senza prezzi.
        assertEquals(listOf(33860, 28102), impianti.map { it.id })
        assertEquals("Tamoil", impianti.first().bandiera)
        assertEquals("2026-09-14T21:47:04+02:00", impianti.first().comunicatoIso)
        // L'indirizzo dalla ricerca non arriva mai: deve restare esplicitamente assente.
        assertNull(impianti.first().indirizzo)
    }

    @Test
    fun `sceglie il prezzo giusto fra self servito e prodotti premium`() = runTest {
        val client = clientCon(HttpStatusCode.OK to RispostaEsempio.zonaMilano)
        val eni = client.cercaPerZona(MILANO).first { it.id == 28102 }

        val self = PreferenzaRicerca(TipoCarburante.GASOLIO, ModalitaErogazione.SELF)
        val servito = PreferenzaRicerca(TipoCarburante.GASOLIO, ModalitaErogazione.SERVITO)
        val indifferente = PreferenzaRicerca(TipoCarburante.GASOLIO, ModalitaErogazione.INDIFFERENTE)

        assertEquals(2.039, eni.prezzoPer(self)?.prezzo)
        assertEquals(2.259, eni.prezzoPer(servito)?.prezzo)
        // Indifferente = il migliore dei due, non il primo che capita.
        assertEquals(2.039, eni.prezzoPer(indifferente)?.prezzo)
        // Il "Blue Diesel" ha un suo fuelId: non deve inquinare il prezzo del gasolio.
        assertTrue(eni.prezzi.any { it.nome == "Blue Diesel" })
    }

    @Test
    fun `un carburante non erogato non produce un prezzo`() = runTest {
        val client = clientCon(HttpStatusCode.OK to RispostaEsempio.zonaMilano)
        val tamoil = client.cercaPerZona(MILANO).first { it.id == 33860 }
        assertNull(tamoil.prezzoPer(PreferenzaRicerca(TipoCarburante.METANO)))
    }

    @Test
    fun `sul 429 aspetta e riprova`() = runTest {
        val attese = mutableListOf<Long>()
        val client = clientCon(
            HttpStatusCode.TooManyRequests to "",
            HttpStatusCode.OK to RispostaEsempio.zonaMilano,
            attese = attese,
        )
        // Il MockEngine ripete l'ultima risposta: la prima e' 429, poi va a buon fine.
        assertEquals(2, client.cercaPerZona(MILANO).size)
        assertEquals(listOf(1L), attese, "deve aver atteso una sola volta")
    }

    @Test
    fun `se il 429 non passa mai si arrende con un errore parlante`() = runTest {
        val attese = mutableListOf<Long>()
        val client = clientCon(HttpStatusCode.TooManyRequests to "", attese = attese)
        assertFailsWith<ErroreRicerca.TroppeRichieste> { client.cercaPerZona(MILANO) }
        assertEquals(listOf(1L, 2L, 3L), attese, "deve esaurire i tentativi previsti")
    }

    @Test
    fun `un errore del server non viene scambiato per un rate limit`() = runTest {
        val client = clientCon(HttpStatusCode.InternalServerError to "")
        assertFailsWith<ErroreRicerca.RispostaInattesa> { client.cercaPerZona(MILANO) }
    }

    @Test
    fun `legge il dettaglio di un impianto`() = runTest {
        val client = clientCon(HttpStatusCode.OK to RispostaEsempio.dettaglio)
        val d = client.dettaglio(15532)

        assertEquals("VIA SENESE 150 - 50124 FIRENZE (FI)", d.indirizzo)
        assertEquals("Q8", d.bandiera)
        assertEquals(2, d.servizi.size)
        assertEquals("Servizi per disabili", d.servizi.first().descrizione)
        // I campi vuoti dell'API restano vuoti, non diventano "null" stampato a video.
        assertEquals("", d.telefono)
    }

    /**
     * Gli orari arrivano come oggetti con una decina di flag, non come frasi: il caso
     * e' stato scoperto dal test di integrazione, perche' l'impianto di esempio che
     * avevamo registrato li aveva vuoti e il parsing dell'intera scheda falliva.
     */
    @Test
    fun `traduce gli orari in righe leggibili`() = runTest {
        val client = clientCon(HttpStatusCode.OK to RispostaEsempio.dettaglio)
        val orari = client.dettaglio(15532).orari

        assertEquals(
            listOf(
                "Lunedì: 07:00 - 12:30 / 15:00 - 19:30",
                "Martedì: 07:00 - 19:30",
                "Domenica: chiuso",
            ),
            orari,
            "il giorno non comunicato non deve comparire",
        )
    }
}

class AnnullamentoTest {

    /**
     * Cambiare carburante annulla la ricerca in corso: quell'annullamento deve
     * risalire come tale, non travestirsi da rete caduta e far comparire all'utente
     * un errore che non c'e'.
     */
    @Test
    fun `una ricerca annullata non diventa un errore di rete`() = runTest {
        val engine = MockEngine {
            // Non risponde mai: la chiamata resta appesa finche' non la si annulla.
            awaitCancellation()
        }
        val client = OsservaprezziClient(engine, attesePerRitento = listOf(1), attendi = {})

        val lavoro = async { client.cercaPerZona(MILANO) }
        yield()
        lavoro.cancel()

        assertFailsWith<CancellationException> { lavoro.await() }
    }
}

class RitentoDiReteTest {

    /**
     * Il servizio sta dietro a un filtro che ogni tanto chiude la connessione durante
     * l'handshake: un tentativo solo basterebbe a mostrare all'utente "Connessione non
     * disponibile" con la rete perfettamente funzionante.
     */
    @Test
    fun `una connessione caduta viene ritentata`() = runTest {
        val attese = mutableListOf<Long>()
        var chiamate = 0
        val engine = MockEngine {
            chiamate++
            if (chiamate == 1) throw RuntimeException("TLS handshake fallito")
            respond(RispostaEsempio.zonaMilano, HttpStatusCode.OK, JSON_HEADER)
        }
        val client = OsservaprezziClient(
            engine = engine,
            attesePerRitento = listOf(1, 2, 3),
            attendi = { attese += it },
        )

        assertEquals(2, client.cercaPerZona(MILANO).size)
        assertEquals(2, chiamate, "deve aver riprovato una volta")
        assertEquals(listOf(1L), attese)
    }

    @Test
    fun `se la rete non torna si arrende dicendo che manca la connessione`() = runTest {
        val engine = MockEngine { throw RuntimeException("rete assente") }
        val client = OsservaprezziClient(engine, attesePerRitento = listOf(1, 2), attendi = {})

        val errore = assertFailsWith<ErroreRicerca.Rete> { client.cercaPerZona(MILANO) }
        assertTrue(errore.message!!.contains("Connessione"))
    }
}
