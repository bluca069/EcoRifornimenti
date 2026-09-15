package net.ecorifornimenti.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import net.ecorifornimenti.app.model.Posizione

/**
 * Su Android l'intento `geo:` lascia scegliere all'utente la sua app di mappe, invece
 * di imporne una. Il nome del distributore va come etichetta del punto.
 */
@Composable
actual fun ricordaAvvioNavigazione(): (Posizione, String) -> Unit {
    val contesto = LocalContext.current
    return { posizione, nome ->
        val etichetta = Uri.encode(nome)
        val uri = Uri.parse("geo:${posizione.lat},${posizione.lng}?q=${posizione.lat},${posizione.lng}($etichetta)")
        try {
            contesto.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            // Nessuna app di mappe installata: non c'e' nulla di sensato da fare,
            // e far cadere l'app per questo sarebbe peggio.
        }
    }
}
