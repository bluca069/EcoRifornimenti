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
