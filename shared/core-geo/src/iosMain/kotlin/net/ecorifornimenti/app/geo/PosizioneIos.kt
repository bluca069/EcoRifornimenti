package net.ecorifornimenti.app.geo

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import net.ecorifornimenti.app.model.Posizione
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyNearestTenMeters
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * La posizione su iOS, via CoreLocation.
 *
 * `CLLocationManager` ammette **un solo delegato**, quindi ce n'e' uno solo per tutta
 * la vita dell'oggetto: smista gli eventi a chi li sta aspettando in quel momento.
 * Con due delegati (uno per il permesso, uno per il fix) il secondo scalzava il primo
 * e la risposta dell'utente al permesso non arrivava a nessuno.
 */
@OptIn(ExperimentalForeignApi::class)
class PosizioneIos : ProviderPosizione {

    private var attesaFix: ((Posizione?) -> Unit)? = null
    private var alCambioPermesso: (() -> Unit)? = null

    private val delegato = object : NSObject(), CLLocationManagerDelegateProtocol {

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            if (permessoConcesso()) alCambioPermesso?.invoke()
        }

        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val posizione = (didUpdateLocations.lastOrNull() as? CLLocation)?.let {
                PosizioneDaCoordinata(it)
            }
            consegna(posizione)
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            // Un fix mancato non e' un errore da propagare: si ripiega sull'ultima
            // posizione nota o sulla ricerca manuale.
            consegna(null)
        }
    }

    private val gestore = CLLocationManager().apply {
        desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        delegate = delegato
    }

    override fun permessoConcesso(): Boolean = when (CLLocationManager.authorizationStatus()) {
        kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> true
        else -> false
    }

    /**
     * Chiede il permesso e avvisa quando l'utente ha risposto: la richiesta e'
     * asincrona, e al primo avvio arriva sempre dopo che la schermata e' partita.
     */
    fun chiediPermesso(alCambio: () -> Unit = {}) {
        alCambioPermesso = alCambio
        gestore.requestWhenInUseAuthorization()
    }

    override suspend fun posizioneCorrente(): Posizione? {
        if (!permessoConcesso()) return null
        return suspendCancellableCoroutine { cont ->
            attesaFix = { posizione -> if (cont.isActive) cont.resume(posizione) }
            gestore.requestLocation()
            cont.invokeOnCancellation {
                attesaFix = null
                gestore.stopUpdatingLocation()
            }
        }
    }

    override suspend fun ultimaPosizioneNota(): Posizione? =
        if (permessoConcesso()) gestore.location?.let { PosizioneDaCoordinata(it) } else null

    private fun consegna(posizione: Posizione?) {
        val attesa = attesaFix
        attesaFix = null
        attesa?.invoke(posizione)
    }
}
