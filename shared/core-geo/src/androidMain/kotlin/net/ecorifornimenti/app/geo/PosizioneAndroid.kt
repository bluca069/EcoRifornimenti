package net.ecorifornimenti.app.geo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import net.ecorifornimenti.app.model.Posizione
import kotlin.coroutines.resume

/**
 * La posizione via Google Play Services (fused provider): mette insieme GPS, rete e
 * sensori, ed e' quello che sul campo da' un fix in tempi decenti anche in citta'.
 */
class PosizioneAndroid(private val contesto: Context) : ProviderPosizione {

    private val client = LocationServices.getFusedLocationProviderClient(contesto)

    override fun permessoConcesso(): Boolean =
        ContextCompat.checkSelfPermission(contesto, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(contesto, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun posizioneCorrente(): Posizione? {
        if (!permessoConcesso()) return null
        val richiesta = CurrentLocationRequest.Builder()
            // Alta precisione: il prezzo del distributore piu' vicino dipende da dove si e'.
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(ATTESA_FIX_MS)
            .build()
        val annulla = CancellationTokenSource()
        return try {
            attendi(client.getCurrentLocation(richiesta, annulla.token))?.toPosizione()
        } catch (e: SecurityException) {
            // Il permesso puo' essere revocato fra il controllo e la chiamata.
            null
        } finally {
            annulla.cancel()
        }
    }

    override suspend fun ultimaPosizioneNota(): Posizione? {
        if (!permessoConcesso()) return null
        return try {
            attendi(client.lastLocation)?.toPosizione()
        } catch (e: SecurityException) {
            null
        }
    }

    /** Da Task di Play Services a sospensione, senza fallire: un errore qui vale null. */
    private suspend fun attendi(task: Task<Location?>): Location? =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
                .addOnCanceledListener { cont.resume(null) }
        }

    private fun Location.toPosizione() = Posizione(latitude, longitude)

    private companion object {
        /** Oltre questa attesa si ripiega sull'ultima posizione nota. */
        const val ATTESA_FIX_MS = 10_000L
    }
}
