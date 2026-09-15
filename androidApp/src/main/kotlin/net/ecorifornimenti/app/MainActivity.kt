package net.ecorifornimenti.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import net.ecorifornimenti.app.api.OsservaprezziClient
import net.ecorifornimenti.app.api.RicercaImpianti
import net.ecorifornimenti.app.api.osservaprezziClient
import net.ecorifornimenti.app.geo.PosizioneAndroid
import net.ecorifornimenti.app.ui.ModelloRicerca
import net.ecorifornimenti.app.ui.PreferenzeAndroid
import net.ecorifornimenti.app.ui.SchermataRicerca

/**
 * L'unica schermata dell'app.
 *
 * Il permesso di posizione si chiede subito all'avvio: l'app non ha nulla da mostrare
 * prima di sapere dove ci si trova, quindi non avrebbe senso rimandarlo a un tocco.
 */
class MainActivity : ComponentActivity() {

    private lateinit var client: OsservaprezziClient
    private lateinit var modello: ModelloRicerca

    private val chiediPermesso = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Concesso o negato, decide il modello cosa mostrare: se e' negato mette il
        // messaggio con il pulsante per riprovare.
        modello.avvia()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        client = osservaprezziClient()
        val posizioni = PosizioneAndroid(applicationContext)
        modello = ModelloRicerca(
            ricerca = RicercaImpianti(client),
            posizioni = posizioni,
            scope = lifecycleScope,
            preferenze = PreferenzeAndroid(applicationContext),
        )

        setContent {
            MaterialTheme { SchermataRicerca(modello) }
        }

        if (posizioni.permessoConcesso()) {
            modello.avvia()
        } else {
            chiediPermesso.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    override fun onDestroy() {
        client.chiudi()
        super.onDestroy()
    }
}
