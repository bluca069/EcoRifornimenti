package net.ecorifornimenti.app.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.ecorifornimenti.app.model.Posizione

/*
 * I DTO ricalcano le risposte dell'Osservaprezzi cosi' come sono, comprese le
 * incoerenze (campi nulli, prezzi ripetuti). La traduzione nei modelli dell'app
 * avviene in Mappatura.kt: qui dentro nessuna logica.
 *
 * L'API non e' documentata e puo' cambiare: il Json del client ha
 * ignoreUnknownKeys, e ogni campo non essenziale e' nullable con un default.
 */

@Serializable
internal data class RicercaZonaRequest(
    val points: List<Posizione>,
    val radius: Int,
    val fuelType: String? = null,
    val priceOrder: String = "asc",
)

@Serializable
internal data class RicercaResponse(
    val success: Boolean = true,
    val center: Posizione? = null,
    val results: List<ImpiantoDto> = emptyList(),
)

@Serializable
internal data class ImpiantoDto(
    val id: Int,
    val name: String? = null,
    val brand: String? = null,
    val location: Posizione? = null,
    val fuels: List<PrezzoDto> = emptyList(),
    val insertDate: String? = null,
    // Sempre null nella ricerca per zona; presente solo nel dettaglio.
    val address: String? = null,
)

@Serializable
internal data class PrezzoDto(
    val fuelId: Int,
    val name: String? = null,
    val price: Double? = null,
    val isSelf: Boolean = false,
    /** Presente nel dettaglio, assente nella ricerca per zona. */
    val insertDate: String? = null,
)

@Serializable
internal data class DettaglioDto(
    val id: Int,
    val name: String? = null,
    val nomeImpianto: String? = null,
    val address: String? = null,
    val brand: String? = null,
    val company: String? = null,
    val phoneNumber: String? = null,
    val email: String? = null,
    val website: String? = null,
    val fuels: List<PrezzoDto> = emptyList(),
    val services: List<ServizioDto> = emptyList(),
    @SerialName("orariapertura") val orari: List<OrarioDto> = emptyList(),
)

/**
 * L'orario di un giorno, come lo comunica il gestore.
 *
 * Quasi tutti i campi sono nulli e i casi si distinguono con i flag: h24, chiusura,
 * orario continuato, oppure mattina e pomeriggio separati. `flagNonComunicato` e' il
 * caso piu' frequente — moltissimi impianti l'orario non lo dichiarano affatto.
 */
@Serializable
internal data class OrarioDto(
    val giornoSettimanaId: Int? = null,
    val oraAperturaMattina: String? = null,
    val oraChiusuraMattina: String? = null,
    val oraAperturaPomeriggio: String? = null,
    val oraChiusuraPomeriggio: String? = null,
    val flagOrarioContinuato: Boolean = false,
    val oraAperturaOrarioContinuato: String? = null,
    val oraChiusuraOrarioContinuato: String? = null,
    val flagH24: Boolean = false,
    val flagChiusura: Boolean = false,
    val flagNonComunicato: Boolean = true,
    val flagSelf: Boolean = false,
    val flagServito: Boolean = false,
)

@Serializable
internal data class ServizioDto(
    val id: String? = null,
    val description: String? = null,
)
