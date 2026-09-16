package net.ecorifornimenti.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import cocoapods.MapLibre.MLNAnnotationImage
import cocoapods.MapLibre.MLNAnnotationProtocol
import cocoapods.MapLibre.MLNMapView
import cocoapods.MapLibre.MLNMapViewDelegateProtocol
import cocoapods.MapLibre.MLNCoordinateBounds
import cocoapods.MapLibre.MLNCoordinateBoundsMake
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import net.ecorifornimenti.app.geo.distanzaKm
import net.ecorifornimenti.app.model.FasciaPrezzo
import net.ecorifornimenti.app.model.Impianto
import net.ecorifornimenti.app.model.Posizione
import net.ecorifornimenti.app.model.PreferenzaRicerca
import platform.CoreGraphics.CGRectZero
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSURL
import platform.darwin.NSObject

/**
 * La mappa su iOS: MapLibre nativo dentro Compose, con `UIKitView`.
 *
 * Rispetto ad Android cambia solo il motore di disegno: stile, tile, targhette e
 * criterio di declutter sono gli stessi, perche' vengono dalle stesse costanti e dalla
 * stessa lista gia' ordinata per prezzo.
 */
@OptIn(ExperimentalForeignApi::class)
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
    val stato = remember { StatoMappaIos() }
    stato.onSeleziona = onSeleziona

    UIKitView(
        modifier = modifier,
        factory = {
            MLNMapView(frame = CGRectZero.readValue()).apply {
                styleURL = NSURL(string = STILE_MAPPA)
                // Il delegato va tenuto vivo dallo stato: MapLibre lo referenzia debole.
                delegate = stato.delegato
                stato.mappa = this
            }
        },
        update = {
            stato.disegna(impianti, fasce, preferenza, selezionato, centro, raggioKm, richiesteRicentro)
        },
    )
}

/**
 * Quel che sopravvive alle ricomposizioni. Gli aggiornamenti arrivano a raffica mentre
 * gli anelli esterni caricano, quindi si ridisegna solo quando cambia qualcosa.
 */
@OptIn(ExperimentalForeignApi::class)
private class StatoMappaIos {
    var mappa: MLNMapView? = null
    var onSeleziona: (Impianto?) -> Unit = {}
    private var disegnati: List<Int> = emptyList()
    private var ultimaSelezione: Int? = null
    /** L'ultimo centro su cui si e' inquadrata la mappa. */
    private var ultimoCentro: Posizione? = null
    private var ultimoRicentro = 0

    /**
     * Il delegato fa due cose: da' a ogni annotazione la sua immagine (targhetta o
     * pallino) e riporta la selezione alla schermata, che apre la scheda.
     */
    val delegato: MLNMapViewDelegateProtocol = object : NSObject(), MLNMapViewDelegateProtocol {

        @ObjCSignatureOverride
        override fun mapView(
            mapView: MLNMapView,
            imageForAnnotation: MLNAnnotationProtocol,
        ): MLNAnnotationImage? {
            val annotazione = imageForAnnotation as? AnnotazionePrezzo ?: return null
            mapView.dequeueReusableAnnotationImageWithIdentifier(annotazione.identificativo)
                ?.let { return it }
            val immagine = if (annotazione.conTarghetta) {
                immagineTarghetta(annotazione.prezzo, annotazione.fascia, annotazione.selezionato)
            } else {
                immaginePallino(annotazione.fascia)
            } ?: return null
            return MLNAnnotationImage.annotationImageWithImage(
                image = immagine,
                reuseIdentifier = annotazione.identificativo,
            )
        }

        @ObjCSignatureOverride
        override fun mapView(mapView: MLNMapView, didSelectAnnotation: MLNAnnotationProtocol) {
            (didSelectAnnotation as? AnnotazionePrezzo)?.let { onSeleziona(it.impianto) }
        }

        @ObjCSignatureOverride
        override fun mapView(mapView: MLNMapView, didDeselectAnnotation: MLNAnnotationProtocol) {
            onSeleziona(null)
        }
    }

    fun disegna(
        impianti: List<Impianto>,
        fasce: Map<Int, FasciaPrezzo>,
        preferenza: PreferenzaRicerca,
        selezionato: Impianto?,
        centro: Posizione,
        raggioKm: Int,
        richiesteRicentro: Int,
    ) {
        val mappa = mappa ?: return
        inquadra(mappa, centro, raggioKm, richiesteRicentro)
        val idOra = impianti.map { it.id }
        if (idOra == disegnati && selezionato?.id == ultimaSelezione) return

        mappa.annotations?.let { mappa.removeAnnotations(it) }
        val conTarghetta = impiantiConTarghetta(impianti, raggioKm)
        impianti.forEach { impianto ->
            val prezzo = impianto.prezzoPer(preferenza) ?: return@forEach
            val annotazione = AnnotazionePrezzo(
                impianto = impianto,
                prezzo = prezzo.prezzo,
                fascia = fasce[impianto.id],
                conTarghetta = impianto.id in conTarghetta || impianto.id == selezionato?.id,
                selezionato = impianto.id == selezionato?.id,
            ).apply {
                setCoordinate(
                    CLLocationCoordinate2DMake(impianto.posizione.lat, impianto.posizione.lng)
                )
                setTitle(impianto.nome)
                setSubtitle("${formattaPrezzo(prezzo.prezzo)} € · ${formattaDistanza(impianto.distanzaKm)}")
            }
            mappa.addAnnotation(annotazione)
        }
        disegnati = idOra
        ultimaSelezione = selezionato?.id
    }

    /**
     * Inquadra l'area cercata. Si aspetta che la vista abbia una dimensione: calcolata
     * su un riquadro di larghezza zero — com'e' appena creata — MapLibre produce uno
     * zoom che mostra mezza regione.
     *
     * Dopo la prima volta comanda l'utente, tranne quando il centro cambia davvero:
     * se ci si e' spostati, o si e' chiesto un aggiornamento da un altro posto, la
     * mappa deve seguire, altrimenti mostrerebbe i distributori di dove si era prima.
     */
    private fun inquadra(
        mappa: MLNMapView,
        centro: Posizione,
        raggioKm: Int,
        richiesteRicentro: Int,
    ) {
        val precedente = ultimoCentro
        // Un aggiornamento chiesto dall'utente riporta sempre la vista sulla posizione
        // GPS, anche se il centro e' lo stesso: nel frattempo puo' aver trascinato la
        // mappa altrove.
        val ricentroChiesto = richiesteRicentro != ultimoRicentro
        ultimoRicentro = richiesteRicentro
        if (!ricentroChiesto && precedente != null &&
            distanzaKm(precedente, centro) <= SPOSTAMENTO_MAPPA_KM
        ) return
        val larghezza = mappa.bounds.useContents { size.width }
        if (larghezza <= 0.0) return
        mappa.setVisibleCoordinateBounds(riquadro(centro, raggioKm), animated = precedente != null)
        ultimoCentro = centro
    }

    private companion object {
        /**
         * Quanto deve spostarsi il centro perche' valga la pena ricentrare: sotto i
         * 300 metri il movimento darebbe piu' fastidio che informazione.
         */
        const val SPOSTAMENTO_MAPPA_KM = 0.3
    }
}


/** Il riquadro che contiene il cerchio di ricerca. */
@OptIn(ExperimentalForeignApi::class)
private fun riquadro(centro: Posizione, raggioKm: Int): CValue<MLNCoordinateBounds> {
    val dLat = raggioKm / 111.32
    val dLng = raggioKm / (111.32 * kotlin.math.cos(centro.lat * kotlin.math.PI / 180))
    return MLNCoordinateBoundsMake(
        sw = CLLocationCoordinate2DMake(centro.lat - dLat, centro.lng - dLng),
        ne = CLLocationCoordinate2DMake(centro.lat + dLat, centro.lng + dLng),
    )
}
