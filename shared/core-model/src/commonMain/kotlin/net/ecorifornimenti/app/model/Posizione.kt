package net.ecorifornimenti.app.model

import kotlinx.serialization.Serializable

/** Un punto sulla mappa. I nomi dei campi sono quelli attesi dall'API MIMIT. */
@Serializable
data class Posizione(
    val lat: Double,
    val lng: Double,
)
