package net.ecorifornimenti.app.geo

import net.ecorifornimenti.app.model.Posizione
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Un percorso stradale: la linea spezzata che lo descrive, piu' quanto e' lungo e
 * quanto ci vuole a farlo.
 */
data class Percorso(
    val punti: List<Posizione>,
    val distanzaKm: Double,
    val durataMinuti: Int,
)

/**
 * Riduce la polilinea a un punto ogni [passoKm], tenendo sempre il primo e l'ultimo.
 *
 * Serve perche' il servizio cerca i distributori in un corridoio di appena mezzo
 * chilometro attorno ai punti che gli si passano: con meno punti restano buchi lungo
 * la strada, con troppi si manda un payload enorme per niente. Un punto al chilometro
 * copre il corridoio con un margine e tiene la richiesta sotto i 10 KB anche per
 * un viaggio di duecento chilometri.
 */
fun campionaPercorso(punti: List<Posizione>, passoKm: Double = PASSO_CAMPIONAMENTO_KM): List<Posizione> {
    if (punti.size <= 2) return punti
    val campionati = mutableListOf(punti.first())
    var accumulato = 0.0
    for (i in 1 until punti.size) {
        accumulato += distanzaKm(punti[i - 1], punti[i])
        if (accumulato >= passoKm) {
            campionati += punti[i]
            accumulato = 0.0
        }
    }
    if (campionati.last() != punti.last()) campionati += punti.last()
    return campionati
}

/**
 * Dove cade [posizione] rispetto al percorso: a che chilometro, e quanto fuori strada.
 *
 * Il calcolo e' sui **segmenti**, non sui vertici: con un punto ogni due chilometri il
 * vertice piu' vicino puo' stare a un chilometro di distanza, e un distributore a 400
 * metri dalla strada risulterebbe a 1,1 km. Proprio il numero che si vuole mostrare
 * sarebbe sbagliato di tre volte.
 */
data class PuntoSulPercorso(
    /** Quanta strada si e' fatta quando gli si arriva accanto. */
    val progressivaKm: Double,
    /** Quanto ci si allontana dal percorso per raggiungerlo. */
    val deviazioneKm: Double,
)

fun proiettaSulPercorso(percorso: List<Posizione>, posizione: Posizione): PuntoSulPercorso {
    if (percorso.isEmpty()) return PuntoSulPercorso(0.0, 0.0)
    if (percorso.size == 1) {
        return PuntoSulPercorso(0.0, distanzaKm(percorso[0], posizione))
    }
    var percorsa = 0.0
    var migliore = PuntoSulPercorso(0.0, Double.MAX_VALUE)
    for (i in 0 until percorso.size - 1) {
        val a = percorso[i]
        val b = percorso[i + 1]
        val lungo = lunghezzaSegmento(a, b)
        val (frazione, distanza) = proiezioneSuSegmento(a, b, posizione)
        if (distanza < migliore.deviazioneKm) {
            migliore = PuntoSulPercorso(
                progressivaKm = percorsa + lungo * frazione,
                deviazioneKm = distanza,
            )
        }
        percorsa += lungo
    }
    return migliore
}

/** A che chilometro del percorso si trova il punto piu' vicino a [posizione]. */
fun progressivaSulPercorso(percorso: List<Posizione>, posizione: Posizione): Double =
    proiettaSulPercorso(percorso, posizione).progressivaKm

/** Quanto si devia dalla strada per raggiungere un punto, in chilometri. */
fun deviazioneDalPercorso(percorso: List<Posizione>, posizione: Posizione): Double =
    proiettaSulPercorso(percorso, posizione).deviazioneKm

/**
 * Proietta [punto] sul segmento da [a] a [b], in un piano locale.
 *
 * Su segmenti di pochi chilometri la curvatura terrestre non si sente: si lavora in
 * chilometri est/nord rispetto ad [a], dove la proiezione e' il solito prodotto
 * scalare. Restituisce dove cade lungo il segmento (da 0 a 1) e quanto dista.
 */
private fun proiezioneSuSegmento(a: Posizione, b: Posizione, punto: Posizione): Pair<Double, Double> {
    val (bx, by) = scostamentoKm(a, b)
    val (px, py) = scostamentoKm(a, punto)
    val lunghezzaQuadra = bx * bx + by * by
    if (lunghezzaQuadra < 1e-9) return 0.0 to distanzaKm(a, punto)
    val frazione = ((px * bx + py * by) / lunghezzaQuadra).coerceIn(0.0, 1.0)
    val dx = px - bx * frazione
    val dy = py - by * frazione
    return frazione to sqrt(dx * dx + dy * dy)
}

private fun lunghezzaSegmento(a: Posizione, b: Posizione): Double = distanzaKm(a, b)

/** Scostamento in chilometri (est, nord) di [b] rispetto ad [a]. */
private fun scostamentoKm(a: Posizione, b: Posizione): Pair<Double, Double> {
    val kmPerGradoLat = 111.32
    val est = (b.lng - a.lng) * kmPerGradoLat * cos(gradi(a.lat))
    val nord = (b.lat - a.lat) * kmPerGradoLat
    return est to nord
}

/**
 * Un punto ogni due chilometri.
 *
 * Misurato su Firenze-Siena (75 km): a questo passo si inviano 38 punti e tornano 41
 * impianti in 1,8 s, contro 88 punti / 44 impianti / 5,4 s a 0,8 km. **Il 93% dei
 * risultati in un terzo del tempo**: il corridoio reale del servizio e' di circa un
 * chilometro per lato, non mezzo, quindi due chilometri di passo non lasciano buchi.
 */
const val PASSO_CAMPIONAMENTO_KM = 2.0

/**
 * Il tetto di lunghezza di un percorso.
 *
 * Oltre non ha senso: chi fa piu' strada non sceglie il distributore alla partenza. E'
 * anche cio' che tiene la richiesta in una chiamata sola — a passo di due chilometri
 * sono ~26 punti, contro gli 88 che il servizio ha gia' accettato senza storie.
 */
const val PERCORSO_MASSIMO_KM = 50.0

/**
 * Se [punto] e' gia' sotto gli occhi di chi guarda la mappa.
 *
 * Non basta che stia dentro il riquadro inquadrato: sopra c'e' la barra dei filtri e
 * sotto la classifica, che ne coprono due fasce. Un marker finito li' sotto e'
 * inquadrato ma invisibile, quindi conta come fuori.
 *
 * Serve a decidere se **non** muovere la mappa: spostarla quando il marker e' gia'
 * visibile e' solo disorientante, perche' chi guarda perde il riferimento di dove si
 * trovava senza guadagnare nulla.
 *
 * @param sudOvest l'angolo in basso a sinistra del riquadro inquadrato.
 * @param nordEst l'angolo in alto a destra.
 * @param coperturaAlta quanta parte dell'altezza e' nascosta dai filtri.
 * @param coperturaBassa quanta ne nasconde la classifica.
 */
fun giaVisibile(
    sudOvest: Posizione,
    nordEst: Posizione,
    punto: Posizione,
    coperturaAlta: Double = COPERTURA_FILTRI,
    coperturaBassa: Double = COPERTURA_CLASSIFICA,
): Boolean {
    val altezza = nordEst.lat - sudOvest.lat
    val larghezza = nordEst.lng - sudOvest.lng
    if (altezza <= 0.0 || larghezza <= 0.0) return false
    val latMin = sudOvest.lat + altezza * coperturaBassa
    val latMax = nordEst.lat - altezza * coperturaAlta
    // Un margine anche ai lati: un marker a filo del bordo si vede a meta'.
    val lngMin = sudOvest.lng + larghezza * MARGINE_LATERALE
    val lngMax = nordEst.lng - larghezza * MARGINE_LATERALE
    return punto.lat in latMin..latMax && punto.lng in lngMin..lngMax
}

/** Quanto schermo si prendono la barra dei filtri e la classifica dei prezzi. */
private const val COPERTURA_FILTRI = 0.14
private const val COPERTURA_CLASSIFICA = 0.30
private const val MARGINE_LATERALE = 0.08
