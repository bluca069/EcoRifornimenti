package net.ecorifornimenti.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.ecorifornimenti.app.model.FasciaPrezzo
import net.ecorifornimenti.app.model.Impianto
import net.ecorifornimenti.app.model.Posizione
import net.ecorifornimenti.app.model.PreferenzaRicerca

/**
 * La mappa con i distributori. Ogni piattaforma la disegna con il suo motore nativo
 * (MapLibre su Android e iOS), ma l'ingresso e' questo, uguale per tutte.
 *
 * @param centro dove si trova l'utente: la mappa ci si centra e ci disegna il puntino.
 * @param raggioKm l'area cercata, disegnata come cerchio per far capire il perimetro.
 * @param fasce colore di ogni marker, gia' calcolato: qui non si decide nulla.
 */
@Composable
expect fun MappaImpianti(
    centro: Posizione,
    raggioKm: Int,
    impianti: List<Impianto>,
    fasce: Map<Int, FasciaPrezzo>,
    preferenza: PreferenzaRicerca,
    selezionato: Impianto?,
    onSeleziona: (Impianto?) -> Unit,
    modifier: Modifier = Modifier,
)

/**
 * Lo stile di mappa usato: OpenFreeMap, tile vettoriali su dati OpenStreetMap,
 * **senza chiave e senza quota**. Vale la pena ricordarlo: e' il motivo per cui l'app
 * non ha bisogno di un account di fatturazione presso nessuno.
 */
const val STILE_MAPPA = "https://tiles.openfreemap.org/styles/liberty"

/** L'attribuzione richiesta dalla licenza ODbL dei dati OpenStreetMap. */
const val ATTRIBUZIONE_MAPPA = "© OpenStreetMap contributors"
