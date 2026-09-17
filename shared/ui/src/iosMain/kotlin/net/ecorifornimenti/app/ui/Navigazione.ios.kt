package net.ecorifornimenti.app.ui

import androidx.compose.runtime.Composable
import net.ecorifornimenti.app.model.Posizione
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * Su iOS si apre Apple Maps con il punto d'arrivo e il nome del distributore.
 * Niente scelta fra app di mappe: iOS non offre un intento generico come Android.
 */
@Composable
actual fun ricordaAvvioNavigazione(): (Posizione, String) -> Unit = { posizione, nome ->
    val etichetta = nome.replace(" ", "+")
    val url = NSURL(string = "http://maps.apple.com/?daddr=${posizione.lat},${posizione.lng}&q=$etichetta")
    UIApplication.sharedApplication.openURL(url)
}

@Composable
actual fun ricordaAperturaLink(): (String) -> Unit = { indirizzo ->
    UIApplication.sharedApplication.openURL(NSURL(string = indirizzo))
}
