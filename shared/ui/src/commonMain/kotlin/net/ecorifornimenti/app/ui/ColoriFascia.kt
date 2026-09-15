package net.ecorifornimenti.app.ui

import androidx.compose.ui.graphics.Color
import net.ecorifornimenti.app.model.FasciaPrezzo

/**
 * I colori delle tre fasce di prezzo, definiti una volta sola perche' marker della
 * mappa e righe della lista devono raccontare la stessa cosa.
 *
 * Sono tonalita' scure: il testo sopra e' bianco, e su una mappa chiara restano
 * leggibili anche in pieno sole.
 */
object ColoriFascia {
    val conveniente = Color(0xFF1B7F3B)
    val media = Color(0xFF9A6A00)
    val caro = Color(0xFFB3261E)

    /**
     * Il distributore aperto sulla mappa. E' un blu, fuori dalla scala verde-giallo-rosso
     * dei prezzi apposta: chi guarda deve capire a colpo d'occhio che quel marker e'
     * quello selezionato, non che costa poco o tanto. L'ingrandimento da solo si perde
     * in mezzo agli altri.
     */
    val selezionato = Color(0xFF1A4FA0)

    operator fun get(fascia: FasciaPrezzo?): Color = when (fascia) {
        FasciaPrezzo.CONVENIENTE -> conveniente
        FasciaPrezzo.CARO -> caro
        else -> media
    }
}

/** Il prezzo come si scrive su un marker: tre decimali, virgola all'italiana. */
fun formattaPrezzo(prezzo: Double): String {
    val millesimi = kotlin.math.round(prezzo * 1000).toInt()
    val interi = millesimi / 1000
    val decimali = (millesimi % 1000).toString().padStart(3, '0')
    return "$interi,$decimali"
}

/**
 * Lo scarto dal prezzo piu' basso, in centesimi: "+12 cent".
 *
 * Non in euro con tre decimali come il prezzo: "+0,124 €" costringe a contare gli zeri
 * per capire se la differenza conta, mentre in centesimi si legge di colpo.
 */
fun formattaDifferenza(differenzaEuro: Double): String {
    val centesimi = kotlin.math.round(differenzaEuro * 100).toInt()
    return if (centesimi <= 0) "0 cent" else "$centesimi cent"
}

/** La distanza come si legge in lista: sotto il chilometro in metri. */
fun formattaDistanza(km: Double): String =
    if (km < 1.0) "${kotlin.math.round(km * 1000).toInt()} m"
    else "${formattaUnDecimale(km)} km"

private fun formattaUnDecimale(v: Double): String {
    val decimi = kotlin.math.round(v * 10).toInt()
    return "${decimi / 10},${decimi % 10}"
}
