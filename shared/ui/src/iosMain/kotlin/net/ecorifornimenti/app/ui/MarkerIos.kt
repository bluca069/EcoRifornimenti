package net.ecorifornimenti.app.ui

import cocoapods.MapLibre.MLNPointAnnotation
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import net.ecorifornimenti.app.model.FasciaPrezzo
import net.ecorifornimenti.app.model.Impianto
import platform.CoreGraphics.CGFloat
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSString
import platform.Foundation.create
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.drawAtPoint
import platform.UIKit.sizeWithAttributes

/**
 * Un distributore sulla mappa iOS. Sottoclasse di `MLNPointAnnotation` perche' il
 * delegato, quando gli si chiede l'immagine, deve poter sapere *quale* distributore
 * sta disegnando: prezzo e fascia non stanno nel titolo, che e' testo per l'utente.
 */
@OptIn(ExperimentalForeignApi::class)
class AnnotazionePrezzo(
    val impianto: Impianto,
    val prezzo: Double,
    val fascia: FasciaPrezzo?,
    val conTarghetta: Boolean,
    val selezionato: Boolean = false,
) : MLNPointAnnotation() {

    /** Stesso prezzo, stessa fascia, stessa forma: una sola immagine, riusata. */
    val identificativo: String = when {
        selezionato -> "sel_${formattaPrezzo(prezzo)}"
        conTarghetta -> "p_${formattaPrezzo(prezzo)}_${fascia ?: "x"}"
        else -> "d_${fascia ?: "x"}"
    }
}

/** I colori delle fasce, negli stessi valori usati da Compose e da Android. */
@OptIn(ExperimentalForeignApi::class)
private fun coloreFascia(fascia: FasciaPrezzo?): UIColor = when (fascia) {
    FasciaPrezzo.CONVENIENTE -> UIColor.colorWithRed(0.106, 0.498, 0.231, 1.0)
    FasciaPrezzo.CARO -> UIColor.colorWithRed(0.702, 0.149, 0.118, 1.0)
    else -> UIColor.colorWithRed(0.604, 0.416, 0.0, 1.0)
}

/**
 * Dove si trova l'utente: e' un'annotazione a parte, cosi' il delegato sa che deve
 * disegnarci il punto azzurro e che toccandola non si apre nessuna scheda.
 */
@OptIn(ExperimentalForeignApi::class)
class AnnotazionePosizione : MLNPointAnnotation() {
    val identificativo: String = "posizione_utente"
}

/**
 * Il punto della posizione corrente: cerchio azzurro, anello bianco e un alone chiaro
 * attorno, cosi' resta visibile anche sopra le strade gialle della mappa.
 */
@OptIn(ExperimentalForeignApi::class)
fun immaginePosizione(): UIImage? {
    val lato = 38.0
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(lato, lato), false, 0.0)
    // Alone largo e un po' piu' carico: e' quello che fa trovare il punto a colpo
    // d'occhio in mezzo alle targhette, prima ancora di distinguerne il colore.
    UIColor.colorWithRed(0.102, 0.451, 0.910, 0.22).setFill()
    UIBezierPath.bezierPathWithOvalInRect(CGRectMake(0.0, 0.0, lato, lato)).fill()
    UIColor.colorWithRed(0.102, 0.451, 0.910, 0.35).setFill()
    UIBezierPath.bezierPathWithOvalInRect(CGRectMake(6.0, 6.0, lato - 12, lato - 12)).fill()
    UIColor.whiteColor.setFill()
    UIBezierPath.bezierPathWithOvalInRect(CGRectMake(8.5, 8.5, lato - 17, lato - 17)).fill()
    UIColor.colorWithRed(0.102, 0.451, 0.910, 1.0).setFill()
    UIBezierPath.bezierPathWithOvalInRect(CGRectMake(11.0, 11.0, lato - 22, lato - 22)).fill()
    val immagine = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return immagine
}

/** Il blu del distributore aperto: lo stesso di ColoriFascia.selezionato. */
@OptIn(ExperimentalForeignApi::class)
private fun coloreSelezione(): UIColor = UIColor.colorWithRed(0.102, 0.310, 0.627, 1.0)

/**
 * La targhetta con il prezzo, disegnata con UIKit: rettangolo colorato, testo bianco,
 * punta in basso — la stessa forma del marker Android, perche' l'app deve essere
 * riconoscibile come la stessa su entrambi i telefoni.
 */
@OptIn(ExperimentalForeignApi::class)
fun immagineTarghetta(prezzo: Double, fascia: FasciaPrezzo?, selezionato: Boolean = false): UIImage? {
    val testo = formattaPrezzo(prezzo)
    // Il selezionato e' anche piu' grande: colore e dimensione insieme lo staccano
    // dagli altri anche in mezzo a una decina di targhette.
    val carattere = UIFont.boldSystemFontOfSize(if (selezionato) 16.0 else 13.0)
    val attributi = mapOf<Any?, Any?>(
        NSFontAttributeName to carattere,
        NSForegroundColorAttributeName to UIColor.whiteColor,
    )
    val nsTesto = NSString.create(string = testo)
    // La larghezza dipende dal testo: "1,839" e "2,199" occupano lo stesso spazio,
    // ma il calcolo resta quello vero invece di una stima a occhio.
    val dimensioneTesto = nsTesto.sizeWithAttributes(attributi)
    val larghezza = dimensioneTesto.useContents { width } + 14.0
    val altezzaTarga = dimensioneTesto.useContents { height } + 8.0
    val punta = 5.0

    UIGraphicsBeginImageContextWithOptions(
        size = CGSizeMake(larghezza, altezzaTarga + punta),
        opaque = false,
        scale = 0.0,
    )
    (if (selezionato) coloreSelezione() else coloreFascia(fascia)).setFill()
    UIBezierPath.bezierPathWithRoundedRect(
        rect = CGRectMake(0.0, 0.0, larghezza, altezzaTarga),
        cornerRadius = 4.0,
    ).fill()
    // La punta verso il distributore.
    UIBezierPath().apply {
        moveToPoint(CGPointMake(larghezza / 2 - punta, altezzaTarga - 1))
        addLineToPoint(CGPointMake(larghezza / 2 + punta, altezzaTarga - 1))
        addLineToPoint(CGPointMake(larghezza / 2, altezzaTarga + punta))
        closePath()
        fill()
    }
    nsTesto.drawAtPoint(CGPointMake(7.0, 4.0), attributi)
    val immagine = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return immagine
}

/** Il puntino degli altri distributori: piccolo, con la sua fascia di prezzo. */
@OptIn(ExperimentalForeignApi::class)
fun immaginePallino(fascia: FasciaPrezzo?): UIImage? {
    val lato = 13.0
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(lato, lato), false, 0.0)
    UIColor.whiteColor.setFill()
    UIBezierPath.bezierPathWithOvalInRect(CGRectMake(0.0, 0.0, lato, lato)).fill()
    coloreFascia(fascia).setFill()
    UIBezierPath.bezierPathWithOvalInRect(CGRectMake(1.5, 1.5, lato - 3, lato - 3)).fill()
    val immagine = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return immagine
}
