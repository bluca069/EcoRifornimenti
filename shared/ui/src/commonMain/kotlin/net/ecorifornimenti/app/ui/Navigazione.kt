package net.ecorifornimenti.app.ui

import androidx.compose.runtime.Composable
import net.ecorifornimenti.app.model.Posizione

/**
 * Apre la navigazione verso un distributore nell'app di mappe del telefono.
 *
 * E' l'ultimo passo naturale dopo aver scelto dove fare il pieno, e nessuno si aspetta
 * che sia questa app a dargli le indicazioni stradali.
 */
@Composable
expect fun ricordaAvvioNavigazione(): (Posizione, String) -> Unit

/**
 * Apre un indirizzo nel browser del telefono. Serve al rimando alla fonte dei dati:
 * chi vuole controllare un prezzo deve poter arrivare all'originale.
 */
@Composable
expect fun ricordaAperturaLink(): (String) -> Unit

/** L'indirizzo pubblico dell'Osservaprezzi, quello che vede chiunque dal browser. */
const val SITO_OSSERVAPREZZI = "https://carburanti.mise.gov.it/ospzSearch/zona"
