package net.ecorifornimenti.app.api

import net.ecorifornimenti.app.model.DettaglioImpianto
import net.ecorifornimenti.app.model.Impianto
import net.ecorifornimenti.app.model.PrezzoCarburante
import net.ecorifornimenti.app.model.Servizio

/**
 * Da DTO a modello. Un impianto senza posizione o senza prezzi non e' mostrabile
 * su una mappa di prezzi, quindi viene scartato invece che propagato a meta'.
 */
internal fun ImpiantoDto.toModel(): Impianto? {
    val pos = location ?: return null
    val prezzi = fuels.mapNotNull { it.toModel() }
    if (prezzi.isEmpty()) return null
    return Impianto(
        id = id,
        nome = name?.trim().orEmpty().ifEmpty { "Distributore $id" },
        bandiera = brand?.trim().orEmpty(),
        posizione = pos,
        prezzi = prezzi,
        comunicatoIso = insertDate,
        indirizzo = address?.trim()?.ifEmpty { null },
    )
}

/** Un prezzo assente o non positivo e' un dato mancante, non un prezzo di zero euro. */
internal fun PrezzoDto.toModel(): PrezzoCarburante? {
    val p = price ?: return null
    if (p <= 0.0) return null
    return PrezzoCarburante(
        fuelId = fuelId,
        nome = name?.trim().orEmpty(),
        prezzo = p,
        self = isSelf,
        comunicatoIso = insertDate,
    )
}

internal fun DettaglioDto.toModel(): DettaglioImpianto = DettaglioImpianto(
    id = id,
    nome = (nomeImpianto ?: name)?.trim().orEmpty().ifEmpty { "Distributore $id" },
    indirizzo = address?.trim().orEmpty(),
    bandiera = brand?.trim().orEmpty(),
    societa = company?.trim().orEmpty(),
    telefono = phoneNumber?.trim().orEmpty(),
    email = email?.trim().orEmpty(),
    sitoWeb = website?.trim().orEmpty(),
    prezzi = fuels.mapNotNull { it.toModel() },
    servizi = services.mapNotNull { s ->
        val d = s.description?.trim().orEmpty()
        if (d.isEmpty()) null else Servizio(s.id.orEmpty(), d)
    },
    orari = orari.mapNotNull { it.leggibile() },
)

/** I giorni, per esteso: l'API li numera da 1 (lunedi'). */
private val GIORNI = listOf(
    "Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica",
)

/**
 * Una riga di orario come la leggerebbe una persona, o `null` quando non c'e' niente
 * da dire: la maggior parte degli impianti non comunica gli orari, e una lista di
 * sette "non comunicato" occuperebbe la scheda senza informare nessuno.
 */
internal fun OrarioDto.leggibile(): String? {
    if (flagNonComunicato) return null
    val giorno = giornoSettimanaId?.let { GIORNI.getOrNull(it - 1) } ?: return null
    return when {
        flagChiusura -> "$giorno: chiuso"
        flagH24 -> "$giorno: aperto 24 ore"
        flagOrarioContinuato -> {
            val da = oraAperturaOrarioContinuato ?: return null
            val a = oraChiusuraOrarioContinuato ?: return null
            "$giorno: $da - $a"
        }
        else -> {
            val fasce = listOfNotNull(
                fascia(oraAperturaMattina, oraChiusuraMattina),
                fascia(oraAperturaPomeriggio, oraChiusuraPomeriggio),
            )
            if (fasce.isEmpty()) null else "$giorno: ${fasce.joinToString(" / ")}"
        }
    }
}

private fun fascia(da: String?, a: String?): String? =
    if (da.isNullOrBlank() || a.isNullOrBlank()) null else "$da - $a"
