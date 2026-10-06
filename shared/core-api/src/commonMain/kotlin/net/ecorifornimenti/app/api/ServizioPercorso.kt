package net.ecorifornimenti.app.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import net.ecorifornimenti.app.geo.Percorso
import net.ecorifornimenti.app.model.Posizione
import kotlin.math.roundToInt

/**
 * Chi sa disegnare una strada fra due punti.
 *
 * L'Osservaprezzi cerca i distributori lungo una linea spezzata ma non la calcola:
 * quella arriva da fuori, ed e' l'unico pezzo dell'app che dipende da un servizio
 * diverso dal Ministero.
 */
interface ServizioPercorso {
    suspend fun calcola(partenza: Posizione, arrivo: Posizione): Percorso
}

/**
 * Il percorso via **OSRM**, il motore di calcolo su dati OpenStreetMap.
 *
 * Si usa l'istanza della **FOSSGIS**, la fondazione che ospita i servizi pubblici di
 * OpenStreetMap: niente chiavi, risposta misurata in 83 ms su Firenze-Siena. Non e'
 * il server `project-osrm.org`, che e' dichiaratamente solo dimostrativo.
 *
 * Resta pur sempre **infrastruttura donata alla comunita', senza SLA e con un uso
 * equo atteso**: per un'app sugli store, prima o poi, serve un'istanza propria o un
 * fornitore con contratto. Per questo l'indirizzo e' un parametro e non una costante
 * sepolta nel codice.
 */
class PercorsoOsrm(
    engine: HttpClientEngine,
    private val baseUrl: String = OSRM_FOSSGIS,
) : ServizioPercorso {

    private val http = HttpClient(engine) {
        install(ContentNegotiation) { json(OsservaprezziClient.json) }
        defaultRequest { header("User-Agent", OsservaprezziClient.USER_AGENT) }
        expectSuccess = false
    }

    override suspend fun calcola(partenza: Posizione, arrivo: Posizione): Percorso {
        // OSRM vuole le coordinate al contrario, longitudine prima: sbagliarle non
        // da' errore, da' un percorso in mezzo al mare.
        val coordinate = "${partenza.lng},${partenza.lat};${arrivo.lng},${arrivo.lat}"
        val risposta: HttpResponse = try {
            http.get("$baseUrl/route/v1/driving/$coordinate?overview=full&geometries=geojson")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw ErroreRicerca.Rete(e)
        }
        if (risposta.status.value !in 200..299) {
            throw ErroreRicerca.RispostaInattesa(risposta.status.toString())
        }
        val corpo: OsrmRispostaDto = try {
            risposta.body()
        } catch (e: Throwable) {
            throw ErroreRicerca.RispostaInattesa(e.message ?: "percorso illeggibile")
        }
        val rotta = corpo.routes.firstOrNull()
            ?: throw ErroreRicerca.PercorsoNonTrovato
        return Percorso(
            punti = rotta.geometry.coordinates.mapNotNull { it.toPosizione() },
            distanzaKm = rotta.distance / 1000.0,
            durataMinuti = (rotta.duration / 60.0).roundToInt(),
        )
    }

    companion object {
        /** L'istanza pubblica della FOSSGIS, profilo automobile. */
        const val OSRM_FOSSGIS = "https://routing.openstreetmap.de/routed-car"
    }
}

@Serializable
internal data class OsrmRispostaDto(
    val code: String = "",
    val routes: List<OsrmRottaDto> = emptyList(),
)

@Serializable
internal data class OsrmRottaDto(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val geometry: OsrmGeometriaDto = OsrmGeometriaDto(),
)

/** GeoJSON: ogni coordinata e' `[longitudine, latitudine]`, in quest'ordine. */
@Serializable
internal data class OsrmGeometriaDto(
    val coordinates: List<List<Double>> = emptyList(),
)

internal fun List<Double>.toPosizione(): Posizione? =
    if (size < 2) null else Posizione(lat = this[1], lng = this[0])
