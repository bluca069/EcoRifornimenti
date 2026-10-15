package net.ecorifornimenti.app.ui

import net.ecorifornimenti.app.geo.distanzaKm
import net.ecorifornimenti.app.model.Impianto

/**
 * Quali distributori meritano la targhetta con il prezzo scritto sopra.
 *
 * Si parte dai piu' convenienti — la lista arriva gia' ordinata — ma si scarta chi
 * cadrebbe addosso a una targhetta gia' assegnata: due prezzi sovrapposti non se ne
 * leggono ne' uno ne' l'altro. Gli scartati restano sulla mappa come pallini.
 *
 * Il criterio e' geografico e non di pixel perche' deve valere uguale su Android e su
 * iOS, dove i due motori di mappa gestiscono le collisioni in modo diverso (e iOS, con
 * le annotazioni classiche, non le gestisce affatto).
 */
fun impiantiConTarghetta(
    impianti: List<Impianto>,
    raggioKm: Int,
    massime: Int = MASSIME_TARGHETTE,
): Set<Int> {
    val separazione = separazioneMinimaKm(raggioKm)
    val scelti = mutableListOf<Impianto>()
    for (impianto in impianti) {
        if (scelti.size >= massime) break
        val troppoVicino = scelti.any {
            distanzaKm(it.posizione, impianto.posizione) < separazione
        }
        if (!troppoVicino) scelti += impianto
    }
    return scelti.map { it.id }.toSet()
}

/**
 * Quanto devono distare due targhette per restare leggibili.
 *
 * Dipende da quanta mappa si sta guardando: a 25 km lo schermo copre piu' terreno,
 * quindi due distributori a un chilometro l'uno dall'altro finiscono appiccicati.
 * Il quinto del raggio e' il valore misurato sullo schermo del telefono: con un ottavo
 * due targhette vicine si toccavano ancora.
 */
private fun separazioneMinimaKm(raggioKm: Int): Double = raggioKm / 5.0

/**
 * Quante targhette al massimo. Una dozzina copre la parte alta della classifica, che e'
 * quello che si sta cercando; per gli altri basta sapere dove sono.
 */
const val MASSIME_TARGHETTE = 12

/**
 * Chi resta visibile quando due marker si sovrappongono.
 *
 * MapLibre piazza i simboli in ordine di chiave crescente e nasconde quelli che non
 * trovano piu' posto: **chi ha la chiave piu' bassa vince**. Le tre costanti qui sotto
 * sono quindi una scala di precedenza, e stanno in comune — invece che dentro la mappa
 * di Android — perche' l'ordine fra loro e' una decisione di prodotto, non un
 * dettaglio del motore di disegno, e perche' cosi' si verifica con un test.
 */
object PrecedenzaMarker {

    /**
     * Il distributore aperto batte tutto, **compreso il punto della posizione**.
     *
     * Non e' un dettaglio: i primi della classifica sono quasi sempre a pochi
     * chilometri da chi guarda, quindi la loro targhetta cade proprio sopra il
     * pallino azzurro. Lasciando vincere la posizione, aprire il distributore piu'
     * conveniente non mostrava nulla sulla mappa — piu' era vicino e vantaggioso,
     * meno si vedeva.
     */
    const val SELEZIONATO = -3f

    /** Dove si trova l'utente: cede solo al distributore che si sta guardando. */
    const val POSIZIONE = -2f

    /**
     * Gli altri si ordinano per prezzo, cosi' quando lo spazio non basta restano
     * visibili i piu' convenienti, che sono quelli che si sta cercando.
     */
    fun perPrezzo(prezzo: Double): Float = prezzo.toFloat()
}
