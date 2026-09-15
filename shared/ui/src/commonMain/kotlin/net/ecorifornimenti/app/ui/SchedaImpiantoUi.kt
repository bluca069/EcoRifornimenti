package net.ecorifornimenti.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.ecorifornimenti.app.model.PreferenzaRicerca

/**
 * La scheda di un distributore: quel che serve per decidere se vale la deviazione —
 * tutti i prezzi, dove si trova, cosa offre — e il pulsante per andarci.
 */
@Composable
fun SchedaImpiantoUi(
    scheda: SchedaImpianto,
    preferenza: PreferenzaRicerca,
    onChiudi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val impianto = scheda.impianto
    val dettaglio = scheda.dettaglio
    val avviaNavigazione = ricordaAvvioNavigazione()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = impianto.nome,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            impianto.bandiera.ifEmpty { null },
                            formattaDistanza(impianto.distanzaKm),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onChiudi) { Text("Chiudi") }
            }

            when {
                scheda.caricamento -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.padding(2.dp))
                    Text("Carico i dettagli…", style = MaterialTheme.typography.bodySmall)
                }
                // Senza dettaglio la scheda resta utile: prezzi e distanza ci sono gia'.
                scheda.erroreDettaglio -> Text(
                    "Dettagli non disponibili ora",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                dettaglio != null && dettaglio.indirizzo.isNotEmpty() -> Text(
                    dettaglio.indirizzo,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // Tutti i prezzi, non solo quello cercato: capita di cambiare idea davanti
            // alla differenza fra self e servito, o di viaggiare con due mezzi diversi.
            val prezzi = dettaglio?.prezzi?.takeIf { it.isNotEmpty() } ?: impianto.prezzi
            prezzi.sortedWith(compareBy({ it.fuelId }, { !it.self })).forEach { p ->
                val cercato = p.fuelId == preferenza.tipo.fuelId && preferenza.modalita.accetta(p)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${p.nome.ifEmpty { "Carburante ${p.fuelId}" }} · ${if (p.self) "self" else "servito"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (cercato) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        text = "${formattaPrezzo(p.prezzo)} €",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (cercato) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }

            if (dettaglio != null) {
                if (dettaglio.servizi.isNotEmpty()) {
                    Text(
                        dettaglio.servizi.joinToString(" · ") { it.descrizione },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (dettaglio.orari.isNotEmpty()) {
                    Text(
                        dettaglio.orari.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = { avviaNavigazione(impianto.posizione, impianto.nome) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text("Naviga") }
        }
    }
}
