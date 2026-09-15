package net.ecorifornimenti.app.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import net.ecorifornimenti.app.model.DettaglioImpianto
import net.ecorifornimenti.app.model.Impianto
import net.ecorifornimenti.app.model.Posizione

/** Un errore che ha senso mostrare all'utente, invece di una eccezione qualunque. */
sealed class ErroreRicerca(message: String) : Exception(message) {
    /** Il servizio ci sta chiedendo di rallentare e i tentativi sono finiti. */
    object TroppeRichieste : ErroreRicerca("Il servizio e' momentaneamente occupato")
    /** Rete assente o server irraggiungibile. */
    class Rete(cause: Throwable) : ErroreRicerca("Connessione non disponibile") {
        init { addSuppressed(cause) }
    }
    /** L'API ha risposto qualcosa di inatteso: e' non documentata, puo' cambiare. */
    class RispostaInattesa(dettaglio: String) : ErroreRicerca("Risposta non riconosciuta: $dettaglio")
}

/**
 * Il client dell'Osservaprezzi Carburanti del MIMIT.
 *
 * Non esiste un contratto pubblico per queste chiamate: sono quelle che usa il sito
 * ufficiale. Da qui due scelte che attraversano tutta la classe — parsing tollerante
 * e cortesia verso il servizio (vedi [eseguiConRitento]).
 */
class OsservaprezziClient(
    engine: HttpClientEngine,
    private val baseUrl: String = BASE_URL,
    /** Attese fra un tentativo e l'altro dopo un 429. Iniettabili per i test. */
    private val attesePerRitento: List<Long> = listOf(2_000, 4_000, 6_000),
    private val attendi: suspend (Long) -> Unit = { delay(it) },
) {
    private val http = HttpClient(engine) { configura() }

    /**
     * Gli impianti entro [RAGGIO_MAX_API_KM_INT] km da [centro], eventualmente filtrati
     * per carburante. Il raggio non e' un parametro: il servizio taglia comunque a 10 km
     * e chiedere di piu' restituisce lo stesso identico insieme (misurato).
     * Per coprire aree piu' ampie si usa [RicercaImpianti], che interroga piu' centri.
     */
    suspend fun cercaPerZona(centro: Posizione, fuelType: String? = null): List<Impianto> {
        val risposta: RicercaResponse = eseguiConRitento {
            http.post("$baseUrl/search/zone") {
                contentType(ContentType.Application.Json)
                setBody(
                    RicercaZonaRequest(
                        points = listOf(centro),
                        radius = RAGGIO_MAX_API_KM_INT,
                        fuelType = fuelType,
                    )
                )
            }
        }
        if (!risposta.success) throw ErroreRicerca.RispostaInattesa("success=false")
        return risposta.results.mapNotNull { it.toModel() }
    }

    /** La scheda completa di un impianto: l'unico posto da cui arriva l'indirizzo. */
    suspend fun dettaglio(idImpianto: Int): DettaglioImpianto {
        val dto: DettaglioDto = eseguiConRitento {
            http.get("$baseUrl/registry/servicearea/$idImpianto")
        }
        return dto.toModel()
    }

    /**
     * Esegue la chiamata rispettando il rate limit del servizio: sul **429** aspetta e
     * riprova secondo [attesePerRitento], poi si arrende con un errore parlante.
     * Misurato sul campo: 8 richieste in parallelo bastano a farlo scattare.
     */
    private suspend inline fun <reified T> eseguiConRitento(
        chiamata: () -> HttpResponse,
    ): T {
        var ultimo: HttpStatusCode? = null
        var ultimaCaduta: Throwable? = null
        for (tentativo in 0..attesePerRitento.size) {
            val risposta = try {
                chiamata()
            } catch (e: ErroreRicerca) {
                throw e
            } catch (e: CancellationException) {
                // Una ricerca annullata — l'utente ha cambiato carburante, o e' arrivato
                // un fix GPS migliore — non e' una rete caduta: deve risalire com'e',
                // altrimenti la schermata mostra "Connessione non disponibile" per un
                // annullamento che abbiamo chiesto noi.
                throw e
            } catch (e: Throwable) {
                // Anche una connessione caduta merita un altro tentativo: il servizio
                // sta dietro a un filtro che ogni tanto chiude l'handshake TLS a freddo
                // (su iOS si vedeva come errore -1200), e su un telefono la rete va e
                // viene per conto suo. Arrendersi al primo colpo lascerebbe l'utente
                // davanti a "Connessione non disponibile" con la rete perfettamente viva.
                ultimaCaduta = e
                if (tentativo < attesePerRitento.size) {
                    attendi(attesePerRitento[tentativo])
                    continue
                }
                throw ErroreRicerca.Rete(e)
            }
            when {
                risposta.status.isSuccess() -> return try {
                    risposta.body()
                } catch (e: Throwable) {
                    throw ErroreRicerca.RispostaInattesa(e.message ?: risposta.status.toString())
                }
                risposta.status == HttpStatusCode.TooManyRequests -> {
                    ultimo = risposta.status
                    if (tentativo < attesePerRitento.size) attendi(attesePerRitento[tentativo])
                }
                else -> throw ErroreRicerca.RispostaInattesa(risposta.status.toString())
            }
        }
        throw when {
            ultimo == HttpStatusCode.TooManyRequests -> ErroreRicerca.TroppeRichieste
            ultimaCaduta != null -> ErroreRicerca.Rete(ultimaCaduta)
            else -> ErroreRicerca.RispostaInattesa(ultimo?.toString() ?: "nessuna risposta")
        }
    }

    fun chiudi() = http.close()

    companion object {
        const val BASE_URL = "https://carburanti.mise.gov.it/ospzApi"
        /** Vedi RAGGIO_MAX_API_KM in core-geo: qui serve la forma intera per il body. */
        const val RAGGIO_MAX_API_KM_INT = 10

        /**
         * Ci si identifica invece di spacciarsi per un browser: se il gestore del
         * servizio dovesse avere qualcosa da ridire, deve poter capire chi chiama.
         */
        const val USER_AGENT = "EcoRifornimenti/1.0 (app mobile; dati Osservaprezzi MIMIT)"

        internal val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
    }

    private fun HttpClientConfig<*>.configura() {
        install(ContentNegotiation) { json(json) }
        defaultRequest { header("User-Agent", USER_AGENT) }
        // Un 429 e' una risposta da leggere, non una eccezione da propagare.
        expectSuccess = false
    }
}

private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299
