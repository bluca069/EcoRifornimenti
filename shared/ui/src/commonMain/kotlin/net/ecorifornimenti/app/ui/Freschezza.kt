package net.ecorifornimenti.app.ui

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Quando e' stato comunicato un prezzo, scritto per chi guarda.
 *
 * Il servizio manda le date in UTC ("2026-09-14T18:15:43Z"): vanno portate nel fuso
 * di chi sta leggendo, altrimenti un prezzo delle 20:15 italiane sembra delle 18:15.
 *
 * Restituisce `null` se la data manca o non si riesce a leggere: l'API non e'
 * documentata, e una riga in meno e' meglio di una data sbagliata.
 */
fun formattaComunicazione(iso: String?, adesso: Instant = Clock.System.now()): String? {
    val istante = leggiIstante(iso) ?: return null
    val locale = istante.toLocalDateTime(TimeZone.currentSystemDefault())
    val data = "${due(locale.day)}/${due(locale.monthNumber)}/${locale.year}"
    val ora = "${due(locale.hour)}:${due(locale.minute)}"
    return "$data alle $ora${anzianita(istante, adesso)}"
}

/** "oggi" e "ieri" dicono piu' di una data, quando la data e' recente. */
private fun anzianita(comunicato: Instant, adesso: Instant): String {
    val ore = (adesso - comunicato).inWholeHours
    return when {
        ore < 0 -> ""
        ore < 24 -> " (oggi)"
        ore < 48 -> " (ieri)"
        // Oltre la settimana il dato e' vecchio abbastanza da meritare un avviso.
        ore >= 24 * 7 -> " (oltre una settimana fa)"
        else -> " (${ore / 24} giorni fa)"
    }
}

private fun leggiIstante(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return try {
        Instant.parse(iso)
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun due(valore: Int): String = if (valore < 10) "0$valore" else "$valore"

/**
 * La comunicazione piu' recente fra quelle dei prezzi passati.
 *
 * I gestori comunicano tutti i carburanti insieme, quindi le date coincidono quasi
 * sempre; quando non coincidono, quella che conta e' l'ultima.
 */
fun ultimaComunicazione(date: List<String?>): String? =
    date.filterNotNull().filter { it.isNotBlank() }.maxOrNull()
