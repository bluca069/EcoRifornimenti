package net.ecorifornimenti.app.model

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Da quanti giorni e' stato comunicato il prezzo di questo impianto.
 *
 * `null` quando la data manca o non si riesce a leggere: l'API non e' documentata, e
 * un impianto senza data **non si scarta** — non sapere quando e' stato comunicato un
 * prezzo e' diverso dal sapere che e' vecchio.
 */
fun Impianto.giorniDallaComunicazione(adesso: Instant = Clock.System.now()): Int? {
    val comunicato = leggiIstante(comunicatoIso) ?: return null
    val giorni = (adesso - comunicato).inWholeDays
    // Una data nel futuro (orologi storti, fusi sbagliati) vale come "appena fatto".
    return if (giorni < 0) 0 else giorni.toInt()
}

/**
 * Se il prezzo e' abbastanza recente da essere mostrato con la soglia scelta.
 *
 * Chi non dichiara la data passa: vedi sopra, l'assenza del dato non e' una prova di
 * vecchiaia, e scartarli vorrebbe dire nascondere distributori che magari hanno il
 * prezzo migliore.
 */
fun Impianto.comunicatoDaMenoDi(giorni: Int, adesso: Instant = Clock.System.now()): Boolean {
    val eta = giorniDallaComunicazione(adesso) ?: return true
    return eta <= giorni
}

private fun leggiIstante(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return try {
        Instant.parse(iso)
    } catch (e: IllegalArgumentException) {
        null
    }
}
