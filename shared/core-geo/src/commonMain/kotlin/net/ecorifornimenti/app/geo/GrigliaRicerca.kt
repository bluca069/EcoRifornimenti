package net.ecorifornimenti.app.geo

import net.ecorifornimenti.app.model.Posizione
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sqrt

/** Il raggio massimo, in km, che l'Osservaprezzi accetta per una singola ricerca. */
const val RAGGIO_MAX_API_KM = 10.0

/**
 * I centri da interrogare per coprire un disco di raggio [raggioKm] attorno a [centro],
 * dato che ogni chiamata all'API copre al massimo [RAGGIO_MAX_API_KM].
 *
 * I centri sono disposti a reticolo esagonale — il modo piu' economico di coprire un
 * piano con dischi uguali — e **ordinati per distanza crescente dal centro**: cosi' la
 * prima cella e' sempre quella dell'utente e i risultati piu' vicini arrivano per primi,
 * mentre gli anelli esterni riempiono la mappa man mano.
 *
 * Per un raggio entro i 10 km restituisce una sola cella: il caso istantaneo.
 */
fun grigliaRicerca(
    centro: Posizione,
    raggioKm: Int,
    raggioCellaKm: Double = RAGGIO_MAX_API_KM,
): List<Posizione> {
    if (raggioKm <= raggioCellaKm) return listOf(centro)

    // Reticolo triangolare: righe distanti r*1.5, colonne distanti r*sqrt(3), righe
    // dispari sfalsate di mezza colonna. Cosi' due centri adiacenti distano esattamente
    // r*sqrt(3) e i dischi si compenetrano quanto basta a non lasciare buchi, senza
    // sovrapporsi piu' del necessario: ogni cella in piu' e' una chiamata in piu'.
    val passoRighe = raggioCellaKm * 1.5
    val passoColonne = raggioCellaKm * sqrt(3.0)
    val estensione = raggioKm + raggioCellaKm
    val righe = ceil(estensione / passoRighe).toInt()
    val colonne = ceil(estensione / passoColonne).toInt() + 1

    val celle = mutableListOf<Posizione>()
    for (riga in -righe..righe) {
        val y = riga * passoRighe
        val sfalsamento = if (riga % 2 == 0) 0.0 else passoColonne / 2
        for (colonna in -colonne..colonne) {
            val x = colonna * passoColonne + sfalsamento
            // Una cella serve solo se il suo disco tocca ancora l'area cercata.
            if (hypot(x, y) <= raggioKm + raggioCellaKm) {
                celle += centro.spostato(nordKm = y, estKm = x)
            }
        }
    }
    // Ordinate sulla distanza reale, la stessa con cui la UI misura tutto il resto.
    return celle.sortedBy { distanzaKm(centro, it) }
}
