package net.ecorifornimenti.app.geo

import net.ecorifornimenti.app.model.Posizione
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

private const val RAGGIO_TERRA_KM = 6371.0

internal fun gradi(v: Double): Double = v * PI / 180.0

/**
 * Distanza in km fra due punti (formula dell'emisenoverso).
 *
 * Serve perche' la ricerca per zona restituisce la distanza solo dal centro della
 * *sua* chiamata: quando i risultati arrivano da piu' celle della griglia, l'unica
 * distanza sensata e' quella ricalcolata rispetto alla posizione dell'utente.
 */
fun distanzaKm(a: Posizione, b: Posizione): Double {
    val dLat = gradi(b.lat - a.lat)
    val dLng = gradi(b.lng - a.lng)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(gradi(a.lat)) * cos(gradi(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
    // min(1.0, ...) protegge da un asin fuori dominio per errore di arrotondamento.
    return 2 * RAGGIO_TERRA_KM * asin(min(1.0, sqrt(h)))
}

/** Sposta un punto di [nordKm] verso nord e [estKm] verso est. */
fun Posizione.spostato(nordKm: Double, estKm: Double): Posizione {
    val kmPerGradoLat = 111.32
    val kmPerGradoLng = kmPerGradoLat * cos(gradi(lat))
    return Posizione(
        lat = lat + nordKm / kmPerGradoLat,
        lng = lng + estKm / kmPerGradoLng,
    )
}
