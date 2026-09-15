package net.ecorifornimenti.app.geo

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import net.ecorifornimenti.app.model.Posizione
import platform.CoreLocation.CLLocation

/**
 * Estrae latitudine e longitudine da una `CLLocation`.
 *
 * La coordinata e' una struct C: va letta dentro `useContents`, che ne garantisce la
 * validita' per la durata del blocco.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun PosizioneDaCoordinata(posizione: CLLocation): Posizione =
    posizione.coordinate.useContents { Posizione(latitude, longitude) }
