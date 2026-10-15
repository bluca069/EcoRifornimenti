package net.ecorifornimenti.app.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import net.ecorifornimenti.app.model.Posizione

/** Un posto trovato cercandone il nome: come si chiama, dove si trova e dov'e'. */
data class Luogo(
    val nome: String,
    /** Comune, provincia o regione: serve a distinguere gli omonimi. */
    val dove: String,
    val posizione: Posizione,
)

/** Chi sa trasformare "Siena" o "via Roma 1, Cremona" in coordinate. */
interface ServizioLuoghi {
    /**
     * @param vicinoA la posizione di chi cerca, usata per mettere davanti i luoghi
     *   vicini: senza, "Siena" puo' rendere prima una frazione omonima lontana.
     */
    suspend fun cerca(testo: String, vicinoA: Posizione? = null): List<Luogo>
}

/**
 * I luoghi via **Photon**, il geocoder su dati OpenStreetMap.
 *
 * Scelto al posto di Nominatim perche' risponde in meno di duecento millisecondi, e'
 * pensato per l'autocompletamento mentre si scrive e accetta un **bias geografico**
 * che mette davanti i posti vicini a chi cerca.
 *
 * Come OSRM e' infrastruttura pubblica a uso equo: chi chiama deve identificarsi e
 * non deve interrogarla a ogni tasto. Il parametro `lang=it` non esiste — le lingue
 * accettate sono `default`, `de`, `en`, `fr` — e `default` rende comunque i nomi
 * locali presenti in OSM.
 */
class LuoghiPhoton(
    engine: HttpClientEngine,
    private val baseUrl: String = PHOTON,
) : ServizioLuoghi {

    private val http = HttpClient(engine) {
        install(ContentNegotiation) { json(OsservaprezziClient.json) }
        defaultRequest { header("User-Agent", OsservaprezziClient.USER_AGENT) }
        expectSuccess = false
    }

    override suspend fun cerca(testo: String, vicinoA: Posizione?): List<Luogo> {
        val query = testo.trim()
        if (query.length < 3) return emptyList()
        val risposta: HttpResponse = try {
            http.get("$baseUrl/api/") {
                parameter("q", query)
                parameter("limit", "6")
                if (vicinoA != null) {
                    parameter("lat", vicinoA.lat)
                    parameter("lon", vicinoA.lng)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw ErroreRicerca.Rete(e)
        }
        if (risposta.status.value !in 200..299) {
            throw ErroreRicerca.RispostaInattesa(risposta.status.toString())
        }
        val trovati: PhotonRispostaDto = try {
            risposta.body()
        } catch (e: Throwable) {
            throw ErroreRicerca.RispostaInattesa(e.message ?: "luoghi illeggibili")
        }
        return trovati.features.mapNotNull { it.toModel() }
            // Photon rende volentieri lo stesso posto piu' volte (il comune, il
            // confine, la stazione): per chi sceglie una destinazione sono doppioni.
            .distinctBy { it.nome to it.dove }
    }

    companion object {
        const val PHOTON = "https://photon.komoot.io"
    }
}

@Serializable
internal data class PhotonRispostaDto(
    val features: List<PhotonLuogoDto> = emptyList(),
)

@Serializable
internal data class PhotonLuogoDto(
    val properties: PhotonProprietaDto = PhotonProprietaDto(),
    val geometry: PhotonGeometriaDto = PhotonGeometriaDto(),
)

@Serializable
internal data class PhotonProprietaDto(
    val name: String? = null,
    val city: String? = null,
    val county: String? = null,
    val state: String? = null,
    val countrycode: String? = null,
)

/** GeoJSON: `[longitudine, latitudine]`, in quest'ordine. */
@Serializable
internal data class PhotonGeometriaDto(
    val coordinates: List<Double> = emptyList(),
)

/**
 * Un risultato senza nome o senza coordinate leggibili si scarta invece di finire
 * sulla mappa a coordinate zero, in mezzo al Golfo di Guinea.
 */
internal fun PhotonLuogoDto.toModel(): Luogo? {
    val nome = properties.name?.trim().orEmpty()
    if (nome.isEmpty()) return null
    if (geometry.coordinates.size < 2) return null
    val posizione = Posizione(lat = geometry.coordinates[1], lng = geometry.coordinates[0])
    val dove = listOfNotNull(
        properties.city?.takeIf { it != nome },
        properties.county?.takeIf { it != nome },
        properties.state,
    ).distinct().take(2).joinToString(", ")
    return Luogo(nome = nome, dove = dove, posizione = posizione)
}

/**
 * Ricorda quel che si e' gia' chiesto, finche' l'app vive.
 *
 * I suggerimenti si cercano mentre si scrive, e chi scrive torna di continuo sulle
 * stesse lettere: cancella, riprova, riscrive lo stesso nome il giorno dopo. Tenere
 * in **memoria volatile** le risposte evita di ridomandare a un servizio pubblico
 * quello che ci ha gia' detto, e fa comparire la tendina all'istante.
 *
 * La zona entra nella chiave: lo stesso nome cercato da due province diverse e' una
 * domanda diversa, perche' il suggerimento tiene conto di dove ci si trova.
 */
class LuoghiRicordati(
    private val servizio: ServizioLuoghi,
) : ServizioLuoghi {

    private val mutex = Mutex()
    private val ricordati = LinkedHashMap<String, List<Luogo>>()

    override suspend fun cerca(testo: String, vicinoA: Posizione?): List<Luogo> {
        val query = testo.trim()
        if (query.length < 3) return emptyList()
        val chiave = chiave(query, vicinoA)
        mutex.withLock { ricordati[chiave] }?.let { return it }

        val esito = servizio.cerca(query, vicinoA)
        ricorda(chiave, esito)
        return esito
    }

    private suspend fun ricorda(chiave: String, luoghi: List<Luogo>) = mutex.withLock {
        // Un tetto c'e': chi scrive e cancella molte volte riempirebbe la mappa di
        // query morte. Si butta la piu' vecchia, che e' quella che non si ricerchera'.
        if (ricordati.size >= MASSIME_RICORDATE) {
            ricordati.keys.firstOrNull()?.let { ricordati.remove(it) }
        }
        ricordati[chiave] = luoghi
    }

    /** Arrotondata al decimo di grado (~11 km), la scala a cui il suggerimento cambia. */
    private fun chiave(query: String, vicinoA: Posizione?): String {
        val zona = vicinoA?.let {
            val lat = kotlin.math.round(it.lat * 10) / 10
            val lng = kotlin.math.round(it.lng * 10) / 10
            "$lat,$lng"
        } ?: "*"
        return "${query.lowercase()}@$zona"
    }

    companion object {
        const val MASSIME_RICORDATE = 50
    }
}
