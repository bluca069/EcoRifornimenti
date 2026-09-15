package net.ecorifornimenti.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.ecorifornimenti.app.api.RicercaImpianti
import net.ecorifornimenti.app.api.osservaprezziClient
import net.ecorifornimenti.app.geo.PosizioneIos
import platform.UIKit.UIViewController

/**
 * L'app su iOS: la stessa schermata di Android, con le implementazioni di casa per
 * posizione, preferenze e mappa.
 *
 * Il permesso di posizione si chiede qui all'avvio, come su Android: prima di saperlo
 * non c'e' nulla da mostrare.
 */
fun MainViewController(): UIViewController {
    val posizioni = PosizioneIos()
    val modello = ModelloRicerca(
        ricerca = RicercaImpianti(osservaprezziClient()),
        posizioni = posizioni,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        preferenze = PreferenzeIos(),
    )
    // Al primo avvio il permesso non c'e' ancora: si chiede, e si parte davvero
    // quando l'utente ha risposto. Se era gia' concesso si parte subito.
    posizioni.chiediPermesso(alCambio = { modello.avvia() })
    if (posizioni.permessoConcesso()) modello.avvia()
    return ComposeUIViewController {
        MaterialTheme { SchermataRicerca(modello) }
    }
}
