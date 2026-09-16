package net.ecorifornimenti.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import net.ecorifornimenti.app.geo.distanzaKm
import net.ecorifornimenti.app.model.FasciaPrezzo
import net.ecorifornimenti.app.model.Impianto
import net.ecorifornimenti.app.model.Posizione
import net.ecorifornimenti.app.model.PreferenzaRicerca
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

/**
 * La mappa su Android: MapLibre GL, che e' una `View` classica, incastonata in Compose
 * con `AndroidView`.
 *
 * I distributori sono disegnati come **simboli con il prezzo gia' dentro l'immagine**
 * invece che come marker con etichetta: con qualche centinaio di impianti a schermo un
 * bitmap per fascia di prezzo, riusato da tutti, costa molto meno di altrettante viste.
 */
@Composable
actual fun MappaImpianti(
    centro: Posizione,
    raggioKm: Int,
    impianti: List<Impianto>,
    fasce: Map<Int, FasciaPrezzo>,
    preferenza: PreferenzaRicerca,
    selezionato: Impianto?,
    onSeleziona: (Impianto?) -> Unit,
    richiesteRicentro: Int,
    modifier: Modifier,
) {
    val contesto = LocalContext.current
    // MapLibre va inizializzato una volta prima di creare la MapView. Non serve
    // nessuna chiave: lo stile e i tile arrivano da OpenFreeMap.
    remember { MapLibre.getInstance(contesto) }

    val stato = remember { StatoMappa() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).also { vista ->
                stato.vista = vista
                vista.onCreate(null)
                vista.getMapAsync { mappa ->
                    mappa.setStyle(Style.Builder().fromUri(STILE_MAPPA)) { stile ->
                        val gestore = SymbolManager(vista, mappa, stile).apply {
                            // Niente sovrapposizioni: in citta' ci sono centinaia di
                            // distributori nel raggio piu' stretto, e lasciarli
                            // disegnare tutti produce un tappeto di targhette
                            // illeggibile. MapLibre tiene quelli a priorita' piu'
                            // alta e nasconde gli altri finche' non si ingrandisce.
                            iconAllowOverlap = false
                            iconIgnorePlacement = false
                        }
                        stato.mappa = mappa
                        stato.gestore = gestore
                        gestore.addClickListener { simbolo ->
                            stato.perSimbolo[simbolo.id]?.let(onSeleziona)
                            true
                        }
                        // Un tocco sulla mappa nuda chiude la scheda aperta.
                        mappa.addOnMapClickListener { onSeleziona(null); false }
                        mappa.uiSettings.isRotateGesturesEnabled = false
                        mappa.uiSettings.setAttributionMargins(16, 0, 0, 16)
                        stato.disegna(centro, raggioKm, impianti, fasce, preferenza, selezionato, richiesteRicentro)
                    }
                }
                vista.onStart()
                vista.onResume()
            }
        },
        update = {
            stato.disegna(centro, raggioKm, impianti, fasce, preferenza, selezionato, richiesteRicentro)
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            stato.gestore?.onDestroy()
            stato.vista?.let { it.onPause(); it.onStop(); it.onDestroy() }
            stato.vista = null
        }
    }
}

/**
 * Quel che va ricordato fra una ricomposizione e l'altra: la mappa, il gestore dei
 * simboli e cosa e' gia' disegnato, per non ridisegnare tutto a ogni aggiornamento
 * (gli anelli esterni ne producono uno ogni pochi decimi di secondo).
 */
private class StatoMappa {
    var vista: MapView? = null
    var mappa: MapLibreMap? = null
    var gestore: SymbolManager? = null

    val perSimbolo = mutableMapOf<Long, Impianto>()
    private var disegnati: List<Int> = emptyList()
    private var iconeCaricate = mutableSetOf<String>()
    /** L'ultimo centro su cui si e' inquadrata la mappa. */
    private var ultimoCentro: Posizione? = null
    private var ultimoRicentro = 0
    private var ultimaSelezione: Int? = null

    fun disegna(
        centro: Posizione,
        raggioKm: Int,
        impianti: List<Impianto>,
        fasce: Map<Int, FasciaPrezzo>,
        preferenza: PreferenzaRicerca,
        selezionato: Impianto?,
        richiesteRicentro: Int,
    ) {
        val mappa = mappa ?: return
        val gestore = gestore ?: return
        val stile = mappa.style ?: return

        val idOra = impianti.map { it.id }
        if (idOra != disegnati || selezionato?.id != ultimaSelezione) {
            gestore.deleteAll()
            perSimbolo.clear()
            val conTarghetta = impiantiConTarghetta(impianti, raggioKm)
            impianti.forEach { impianto ->
                val prezzo = impianto.prezzoPer(preferenza) ?: return@forEach
                val fascia = fasce[impianto.id]
                val evidenziato = impianto.id == selezionato?.id
                val targhetta = evidenziato || impianto.id in conTarghetta
                val nomeIcona = nomeIcona(prezzo.prezzo, fascia, evidenziato, targhetta)
                if (iconeCaricate.add(nomeIcona)) {
                    stile.addImage(
                        nomeIcona,
                        if (targhetta) etichettaPrezzo(prezzo.prezzo, fascia, evidenziato)
                        else pallino(fascia),
                    )
                }
                val simbolo: Symbol = gestore.create(
                    SymbolOptions()
                        .withLatLng(LatLng(impianto.posizione.lat, impianto.posizione.lng))
                        .withIconImage(nomeIcona)
                        .withIconAnchor(if (targhetta) "bottom" else "center")
                        // Chi ha la chiave piu' bassa viene piazzato per primo e
                        // vince i conflitti: quindi si ordina per prezzo, e quando
                        // lo spazio non basta restano visibili i piu' convenienti,
                        // che sono quelli che l'utente sta cercando. Il selezionato
                        // passa davanti a tutti.
                        .withSymbolSortKey(if (evidenziato) -1f else prezzo.prezzo.toFloat())
                )
                perSimbolo[simbolo.id] = impianto
            }
            disegnati = idOra
            ultimaSelezione = selezionato?.id
        }

        // Di norma l'inquadratura si imposta una volta sola: dopo comanda l'utente, e
        // una mappa che si ricentra da sola mentre la si sta guardando e'
        // insopportabile. Ma se il centro cambia — ci si e' spostati, o si e' chiesto
        // un aggiornamento da un altro posto — la mappa deve seguire, altrimenti
        // mostrerebbe i distributori di dove si era prima.
        val centroCambiato = ultimoCentro?.let { distanzaKm(it, centro) > SPOSTAMENTO_MAPPA_KM } ?: true
        // Un aggiornamento chiesto dall'utente riporta sempre la vista sulla posizione
        // GPS, anche se il centro e' lo stesso: nel frattempo puo' aver trascinato la
        // mappa altrove, e "aggiorna" deve rispondere alla domanda "dove sono adesso".
        val ricentroChiesto = richiesteRicentro != ultimoRicentro
        if (centroCambiato || ricentroChiesto) {
            ultimoRicentro = richiesteRicentro
            val inquadratura = CameraUpdateFactory.newLatLngBounds(riquadro(centro, raggioKm), 80)
            // La prima volta si salta subito al posto giusto; gli spostamenti
            // successivi si animano, cosi' si capisce che la mappa si e' mossa e
            // perche' — un salto secco sembrerebbe un errore.
            if (ultimoCentro == null) mappa.moveCamera(inquadratura)
            else mappa.animateCamera(inquadratura)
            ultimoCentro = centro
        }
    }

    /** Il riquadro che contiene il cerchio di ricerca, per inquadrare tutta l'area. */
    private fun riquadro(centro: Posizione, raggioKm: Int): LatLngBounds {
        val dLat = raggioKm / 111.32
        val dLng = raggioKm / (111.32 * kotlin.math.cos(centro.lat * kotlin.math.PI / 180))
        return LatLngBounds.from(
            centro.lat + dLat, centro.lng + dLng,
            centro.lat - dLat, centro.lng - dLng,
        )
    }

    /**
     * Il nome dell'icona e' la sua descrizione: stesso prezzo e stessa fascia = stessa
     * immagine, generata una volta e riusata da tutti i distributori che la condividono.
     */
    private companion object {
        /**
         * Quanto deve spostarsi il centro perche' valga la pena ricentrare: sotto i
         * 300 metri il movimento della mappa darebbe piu' fastidio che informazione.
         */
        const val SPOSTAMENTO_MAPPA_KM = 0.3
    }

    private fun nomeIcona(
        prezzo: Double,
        fascia: FasciaPrezzo?,
        evidenziato: Boolean,
        targhetta: Boolean,
    ): String =
        if (targhetta) "p_${formattaPrezzo(prezzo)}_${fascia ?: "x"}_${if (evidenziato) "s" else "n"}"
        else "d_${fascia ?: "x"}"


}

/** Il puntino dei distributori fuori dalla testa della classifica: piccolo, ma con la
 *  sua fascia di prezzo, cosi' si vede a colpo d'occhio dove conviene fermarsi. */
private fun pallino(fascia: FasciaPrezzo?): Bitmap {
    val lato = 26
    val bitmap = Bitmap.createBitmap(lato, lato, Bitmap.Config.ARGB_8888)
    val tela = Canvas(bitmap)
    val raggio = lato / 2f
    tela.drawCircle(raggio, raggio, raggio - 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    })
    tela.drawCircle(raggio, raggio, raggio - 4, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColoriFascia[fascia].toArgb()
    })
    return bitmap
}

/** Disegna la targhetta con il prezzo: rettangolo colorato, testo bianco, punta in basso. */
private fun etichettaPrezzo(prezzo: Double, fascia: FasciaPrezzo?, evidenziato: Boolean): Bitmap {
    val testo = formattaPrezzo(prezzo)
    val scala = if (evidenziato) 1.25f else 1f
    val dimensioneTesto = 34f * scala
    val pennelloTesto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = dimensioneTesto
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    val margine = 14f * scala
    val larghezza = pennelloTesto.measureText(testo) + margine * 2
    val altezzaTarga = dimensioneTesto + margine * 1.6f
    val punta = 10f * scala

    val bitmap = Bitmap.createBitmap(
        larghezza.toInt().coerceAtLeast(1),
        (altezzaTarga + punta).toInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val tela = Canvas(bitmap)
    val pennelloSfondo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (if (evidenziato) ColoriFascia.selezionato else ColoriFascia[fascia]).toArgb()
    }
    tela.drawRoundRect(RectF(0f, 0f, larghezza, altezzaTarga), 8f, 8f, pennelloSfondo)
    // La punta che indica il distributore, sotto la targhetta.
    val percorso = android.graphics.Path().apply {
        moveTo(larghezza / 2 - punta, altezzaTarga - 1)
        lineTo(larghezza / 2 + punta, altezzaTarga - 1)
        lineTo(larghezza / 2, altezzaTarga + punta)
        close()
    }
    tela.drawPath(percorso, pennelloSfondo)
    if (evidenziato) {
        val bordo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        tela.drawRoundRect(RectF(1.5f, 1.5f, larghezza - 1.5f, altezzaTarga - 1.5f), 8f, 8f, bordo)
    }
    val baseTesto = altezzaTarga / 2 - (pennelloTesto.descent() + pennelloTesto.ascent()) / 2
    tela.drawText(testo, larghezza / 2, baseTesto, pennelloTesto)
    return bitmap
}
