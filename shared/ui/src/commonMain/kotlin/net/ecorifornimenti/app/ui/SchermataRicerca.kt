package net.ecorifornimenti.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import net.ecorifornimenti.app.model.Impianto
import kotlinx.coroutines.delay
import net.ecorifornimenti.app.geo.distanzaKm
import net.ecorifornimenti.app.model.ModalitaErogazione
import net.ecorifornimenti.app.model.Posizione
import net.ecorifornimenti.app.model.PreferenzaRicerca
import net.ecorifornimenti.app.model.TipoCarburante

/**
 * La schermata unica dell'app.
 *
 * In verticale la mappa prende tutto e i risultati stanno in fondo; in orizzontale la
 * mappa resta a sinistra e i risultati passano in una colonna stretta a destra, dove
 * non le rubano meta' schermo. La sostanza non cambia: la stessa mappa, la stessa
 * classifica, la stessa scheda.
 *
 * L'utente arriva qui subito dopo l'avvio e vede gia' dei distributori: e' il motivo
 * per cui la ricerca emette risultati parziali invece di aspettare di finire.
 */
@Composable
fun SchermataRicerca(
    modello: ModelloRicerca,
    modifier: Modifier = Modifier,
) {
    val stato by modello.stato.collectAsState()
    var impostazioniAperte by remember { mutableStateOf(false) }

    if (impostazioniAperte) {
        ImpostazioniRicerca(
            preferenza = stato.preferenza,
            onCambia = modello::cambiaPreferenza,
            onChiudi = { impostazioniAperte = false },
        )
    }

    Surface(modifier = modifier.fillMaxSize()) {
        when (val s = stato.stato) {
            is StatoSchermata.Errore -> Messaggio(
                testo = s.messaggio,
                azione = "Riprova",
                onAzione = modello::riprova,
            )
            StatoSchermata.Iniziale, StatoSchermata.AttesaPosizione -> Attesa()
            is StatoSchermata.Pronta -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val centro = stato.posizione
                var centroMappa by remember { mutableStateOf<Posizione?>(null) }
                var proponiRicerca by remember { mutableStateOf(false) }

                // Dopo ogni ricerca la proposta non ha piu' senso: si sta gia'
                // guardando il risultato di quella zona.
                LaunchedEffect(centro) {
                    centroMappa = null
                    proponiRicerca = false
                }

                // Il pulsante non compare subito: si aspetta che la mano si sia fermata
                // per un secondo, altrimenti apparirebbe e sparirebbe a ogni scatto
                // mentre si sta ancora scorrendo.
                LaunchedEffect(centroMappa) {
                    val portataA = centroMappa
                    if (portataA == null || centro == null) {
                        proponiRicerca = false
                        return@LaunchedEffect
                    }
                    delay(ATTESA_PROPOSTA_MS)
                    proponiRicerca = distanzaKm(centro, portataA) > SPOSTAMENTO_PROPOSTA_KM
                }

                val proposta = PropostaRicerca(
                    visibile = proponiRicerca,
                    onSpostata = { centroMappa = it },
                    onCerca = {
                        centroMappa?.let { modello.cercaIn(it) }
                        proponiRicerca = false
                    },
                )
                // Non "e' un tablet" o "e' un telefono": conta solo se lo schermo e'
                // piu' largo che alto, perche' e' li' che una lista a tutta larghezza
                // sprecherebbe lo spazio che serve alla mappa.
                val orizzontale = maxWidth > maxHeight
                if (orizzontale) {
                    DisposizioneOrizzontale(
                        stato = stato,
                        modello = modello,
                        proposta = proposta,
                        onApriImpostazioni = { impostazioniAperte = true },
                    )
                } else {
                    DisposizioneVerticale(
                        stato = stato,
                        modello = modello,
                        proposta = proposta,
                        onApriImpostazioni = { impostazioniAperte = true },
                    )
                }
            }
        }
    }
}

/** Mappa a tutto schermo, filtri sovrapposti in alto, risultati in fondo. */
@Composable
private fun DisposizioneVerticale(
    stato: StatoRicerca,
    modello: ModelloRicerca,
    proposta: PropostaRicerca,
    onApriImpostazioni: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Mappa(stato, modello, proposta.onSpostata, Modifier.fillMaxSize())

        // I filtri e i risultati stanno sopra la mappa, che invece va a tutto schermo:
        // le aree di sistema (notch, Dynamic Island, barra home) le rispetta solo il
        // contenuto sovrapposto.
        Column(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
        ) {
            BarraFiltri(stato.preferenza, modello::cambiaPreferenza, onApriImpostazioni)
            Avanzamento(stato)
            PulsanteCercaQui(proposta)
        }

        Pannello(
            stato = stato,
            modello = modello,
            modifier = Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        )
    }
}

/**
 * Mappa a sinistra, risultati in una colonna stretta a destra.
 *
 * La colonna e' larga quanto basta a leggere nome, distanza e prezzo su una riga:
 * allargarla toglierebbe alla mappa proprio la parte che si guarda, cioe' dove sono i
 * distributori attorno.
 */
@Composable
private fun DisposizioneOrizzontale(
    stato: StatoRicerca,
    modello: ModelloRicerca,
    proposta: PropostaRicerca,
    onApriImpostazioni: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Mappa(stato, modello, proposta.onSpostata, Modifier.fillMaxSize())
            Column(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Start
                        )
                    )
                    .padding(12.dp)
            ) {
                BarraFiltri(stato.preferenza, modello::cambiaPreferenza, onApriImpostazioni)
                Avanzamento(stato)
                PulsanteCercaQui(proposta)
            }
        }
        Pannello(
            stato = stato,
            modello = modello,
            modifier = Modifier.width(LARGHEZZA_PANNELLO)
                .fillMaxHeight()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom
                    )
                ),
            aTuttaAltezza = true,
        )
    }
}

private val LARGHEZZA_PANNELLO = 260.dp

/**
 * Quel che serve per proporre "cerca qui": se mostrarlo, dove l'utente ha portato la
 * mappa e cosa fare quando accetta.
 */
private data class PropostaRicerca(
    val visibile: Boolean,
    val onSpostata: (Posizione) -> Unit,
    val onCerca: () -> Unit,
)

@Composable
private fun Mappa(
    stato: StatoRicerca,
    modello: ModelloRicerca,
    onSpostataDallUtente: (Posizione) -> Unit,
    modifier: Modifier,
) {
    val centro = stato.posizione ?: return
    MappaImpianti(
        centro = centro,
        posizioneGps = stato.posizioneGps,
        raggioKm = stato.preferenza.raggioKm,
        impianti = stato.impianti,
        fasce = stato.fasce,
        preferenza = stato.preferenza,
        selezionato = stato.selezionato,
        onSeleziona = modello::seleziona,
        richiesteRicentro = stato.richiesteRicentro,
        onSpostataDallUtente = onSpostataDallUtente,
        modifier = modifier,
    )
}

/**
 * La proposta di cercare dove si sta guardando. Sta sotto i filtri e non in fondo allo
 * schermo, dove finirebbe dietro la classifica.
 */
@Composable
private fun PulsanteCercaQui(proposta: PropostaRicerca) {
    if (!proposta.visibile) return
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Button(onClick = proposta.onCerca) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Cerca in questa zona")
        }
    }
}

/** Quanto si aspetta, a mano ferma, prima di proporre la ricerca. */
private const val ATTESA_PROPOSTA_MS = 1_000L

/**
 * Sotto questo spostamento la proposta non compare: si vedrebbero quasi gli stessi
 * distributori, e un pulsante che appare per un trascinamento di un centimetro e' solo
 * un ingombro sulla mappa.
 */
private const val SPOSTAMENTO_PROPOSTA_KM = 1.0

@Composable
private fun Avanzamento(stato: StatoRicerca) {
    if (!stato.mostraAvanzamento) return
    LinearProgressIndicator(
        progress = { stato.avanzamento },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

/**
 * La scheda del distributore aperto, oppure la classifica: sono due risposte alla
 * stessa domanda, e mostrarle insieme lascerebbe alla mappa una striscia inutilizzabile.
 */
@Composable
private fun Pannello(
    stato: StatoRicerca,
    modello: ModelloRicerca,
    modifier: Modifier = Modifier,
    aTuttaAltezza: Boolean = false,
) {
    val scheda = stato.scheda
    if (scheda != null) {
        SchedaImpiantoUi(
            scheda = scheda,
            preferenza = stato.preferenza,
            onChiudi = { modello.seleziona(null) },
            modifier = modifier,
        )
    } else {
        ElencoRisultati(
            stato = stato,
            onSeleziona = modello::seleziona,
            onAggiorna = modello::aggiorna,
            modifier = modifier,
            aTuttaAltezza = aTuttaAltezza,
        )
    }
}

/**
 * In alto resta solo il carburante — la scelta che si cambia davvero spesso — con a
 * fianco il pulsante che apre le altre impostazioni.
 *
 * I chip scorrono in orizzontale invece di andare a capo: con lo schermo stretto un
 * `Row` che va a capo spezza le etichette in verticale ("1 5 k m").
 */
@Composable
private fun BarraFiltri(
    preferenza: PreferenzaRicerca,
    onCambia: (PreferenzaRicerca) -> Unit,
    onApriImpostazioni: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TipoCarburante.entries.forEach { tipo ->
                    ChipFiltro(
                        selezionato = preferenza.tipo == tipo,
                        etichetta = tipo.etichetta,
                        onClick = { onCambia(preferenza.copy(tipo = tipo)) },
                    )
                }
            }
            IconButton(onClick = onApriImpostazioni) {
                Icon(Icons.Filled.Tune, contentDescription = "Impostazioni di ricerca")
            }
        }
    }
}

/**
 * Le impostazioni che si toccano di rado: come si vuole il rifornimento, fin dove
 * cercare, e da dove arrivano i dati. Stanno in un modale perche' in alto rubavano
 * spazio alla mappa a ogni apertura, pur cambiando quasi mai.
 *
 * Il riquadro e' largo e squadrato, con i contenuti su **due colonne** quando c'e'
 * spazio: a telefono ruotato lo schermo e' basso e stretto in altezza, e una colonna
 * sola costringeva a scorrere per arrivare alla fonte dei dati.
 */
@Composable
private fun ImpostazioniRicerca(
    preferenza: PreferenzaRicerca,
    onCambia: (PreferenzaRicerca) -> Unit,
    onChiudi: () -> Unit,
) {
    Dialog(
        onDismissRequest = onChiudi,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(0.94f).padding(vertical = 12.dp)) {
            val dueColonne = maxWidth >= 520.dp
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = "Impostazioni di ricerca",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(14.dp))

                    if (dueColonne) {
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            Column(Modifier.weight(1f)) { Scelte(preferenza, onCambia) }
                            // In due colonne la linea non serve: a separare ci pensa
                            // gia' lo spazio fra le colonne.
                            Column(Modifier.weight(1f)) { InformazioniApp(conSeparatore = false) }
                        }
                    } else {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Scelte(preferenza, onCambia)
                            InformazioniApp()
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(onClick = onChiudi) { Text("Fatto") }
                    }
                }
            }
        }
    }
}

/** Rifornimento e raggio: le due scelte che governano la ricerca. */
@Composable
private fun Scelte(preferenza: PreferenzaRicerca, onCambia: (PreferenzaRicerca) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Rifornimento", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ModalitaErogazione.entries.forEach { modalita ->
                    ChipFiltro(
                        selezionato = preferenza.modalita == modalita,
                        etichetta = modalita.etichetta,
                        onClick = { onCambia(preferenza.copy(modalita = modalita)) },
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Raggio di ricerca", style = MaterialTheme.typography.labelLarge)
            // Da quattro raggi in poi i chip non entrano in larghezza: la riga scorre
            // invece di tagliare l'ultima etichetta.
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PreferenzaRicerca.RAGGI_KM.forEach { raggio ->
                    ChipFiltro(
                        selezionato = preferenza.raggioKm == raggio,
                        etichetta = "$raggio km",
                        onClick = { onCambia(preferenza.copy(raggioKm = raggio)) },
                    )
                }
            }
            Text(
                text = if (preferenza.raggioKm <= 10) {
                    "Ricerca immediata."
                } else {
                    "Oltre i 10 km i distributori più lontani compaiono poco per volta."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Da dove arrivano i numeri, per esteso e con il rimando all'originale: chi vuole
 * verificare un prezzo, o capire perche' un distributore non c'e', deve poter arrivare
 * alla fonte invece di fidarsi e basta.
 *
 * C'e' anche la versione, perche' la prima domanda a chi segnala un problema e' sempre
 * "che versione hai?" e questo e' l'unico posto dove guardare.
 */
@Composable
private fun InformazioniApp(conSeparatore: Boolean = true) {
    val apriLink = ricordaAperturaLink()
    val versione = versioneApp()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (conSeparatore) HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text(
            text = "Prezzi e distributori: Osservaprezzi Carburanti, Ministero delle " +
                "Imprese e del Made in Italy.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = SITO_OSSERVAPREZZI,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { apriLink(SITO_OSSERVAPREZZI) },
        )
        Text(
            text = "Mappa: MapLibre su dati $ATTRIBUZIONE_MAPPA.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (versione.isNotEmpty()) {
            Text(
                text = "EcoRifornimenti $versione",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Un filtro: compatto, su una riga sola, senza spuntine che rubano larghezza. */
@Composable
private fun ChipFiltro(selezionato: Boolean, etichetta: String, onClick: () -> Unit) {
    FilterChip(
        selected = selezionato,
        onClick = onClick,
        label = { Text(etichetta, maxLines = 1, softWrap = false) },
        leadingIcon = null,
    )
}

/**
 * I risultati in fondo allo schermo, ordinati dal piu' conveniente.
 * Toccarne uno lo seleziona sulla mappa, e viceversa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElencoRisultati(
    stato: StatoRicerca,
    onSeleziona: (Impianto?) -> Unit,
    onAggiorna: () -> Unit,
    modifier: Modifier = Modifier,
    /** Nella colonna laterale la classifica puo' scorrere per tutta l'altezza. */
    aTuttaAltezza: Boolean = false,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = if (aTuttaAltezza) RoundedCornerShape(0.dp)
            else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        if (stato.impianti.isEmpty()) {
            Text(
                text = if (stato.mostraAvanzamento) "Ricerca in corso…"
                    else "Nessun distributore con questo carburante nel raggio scelto",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Card
        }
        val scorrimento = rememberLazyListState()
        // Quando arriva un distributore piu' conveniente di quello in testa, la lista
        // deve tornare in cima: con le chiavi, LazyColumn resta ancorato all'elemento
        // che si sta guardando e i nuovi risultati migliori finiscono sopra il bordo,
        // invisibili — la mappa li mostra e la classifica no.
        LaunchedEffect(stato.migliore?.id) {
            if (stato.impianti.isNotEmpty()) scorrimento.scrollToItem(0)
        }
        // Tirare giu' la classifica rilegge i prezzi, buttando via la cache: e' il
        // gesto che tutti si aspettano su una lista, e qui ha un senso vero, perche'
        // i risultati restano in tasca per venti minuti.
        PullToRefreshBox(
            isRefreshing = stato.inAggiornamento,
            onRefresh = onAggiorna,
            modifier = if (aTuttaAltezza) Modifier.fillMaxHeight()
                else Modifier.heightIn(max = 260.dp),
        ) {
        LazyColumn(
            state = scorrimento,
            modifier = Modifier.fillMaxSize(),
        ) {
            val prezzoMigliore = stato.migliore?.prezzoPer(stato.preferenza)?.prezzo
            itemsIndexed(stato.impianti, key = { _, imp -> imp.id }) { posizione, impianto ->
                RigaImpianto(
                    posizione = posizione + 1,
                    impianto = impianto,
                    preferenza = stato.preferenza,
                    colore = ColoriFascia[stato.fasce[impianto.id]],
                    prezzoMigliore = prezzoMigliore,
                    selezionato = impianto.id == stato.selezionato?.id,
                    onClick = { onSeleziona(impianto) },
                )
                if (posizione < stato.impianti.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun RigaImpianto(
    posizione: Int,
    impianto: Impianto,
    preferenza: PreferenzaRicerca,
    colore: Color,
    prezzoMigliore: Double?,
    selezionato: Boolean,
    onClick: () -> Unit,
) {
    val prezzo = impianto.prezzoPer(preferenza) ?: return
    val differenza = prezzoMigliore?.let { prezzo.prezzo - it } ?: 0.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selezionato) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Il posto in classifica al posto del vecchio pallino: dice la stessa cosa sul
        // colore, e in piu' risponde alla domanda "quanti ce ne sono prima di questo".
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(colore),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$posizione",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = impianto.nome,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                // Nella colonna laterale i nomi lunghi non ci stanno: meglio i puntini
                // che una parola tagliata a meta' senza segnalarlo.
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    impianto.bandiera.ifEmpty { null },
                    formattaDistanza(impianto.distanzaKm),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            // Il prezzo in una pastiglia colorata: e' il dato per cui si apre l'app,
            // e va trovato senza doverlo cercare fra il resto della riga.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colore)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = formattaPrezzo(prezzo.prezzo),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            // Quanto costa in piu' del migliore: al distributore ci si va per la
            // differenza, non per il prezzo assoluto.
            Text(
                text = if (differenza < 0.0005) "il più basso"
                    else "+${formattaDifferenza(differenza)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Attesa() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            "Cerco la tua posizione…",
            Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Messaggio(testo: String, azione: String, onAzione: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(testo, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onAzione, modifier = Modifier.padding(top = 16.dp)) { Text(azione) }
    }
}
