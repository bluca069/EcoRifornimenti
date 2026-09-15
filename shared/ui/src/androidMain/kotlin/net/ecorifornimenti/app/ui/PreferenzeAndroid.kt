package net.ecorifornimenti.app.ui

import android.content.Context
import net.ecorifornimenti.app.model.PreferenzaRicerca

/** Le preferenze su Android: tre valori, quindi bastano le SharedPreferences. */
class PreferenzeAndroid(contesto: Context) : ArchivioPreferenze {

    private val archivio = contesto.getSharedPreferences("preferenze", Context.MODE_PRIVATE)

    override fun leggi(): PreferenzaRicerca = CodificaPreferenze.decodifica(
        tipo = archivio.getString(CodificaPreferenze.CHIAVE_TIPO, null),
        modalita = archivio.getString(CodificaPreferenze.CHIAVE_MODALITA, null),
        raggio = archivio.getInt(CodificaPreferenze.CHIAVE_RAGGIO, 0).takeIf { it > 0 },
    )

    override fun salva(preferenza: PreferenzaRicerca) {
        archivio.edit()
            .putString(CodificaPreferenze.CHIAVE_TIPO, preferenza.tipo.name)
            .putString(CodificaPreferenze.CHIAVE_MODALITA, preferenza.modalita.name)
            .putInt(CodificaPreferenze.CHIAVE_RAGGIO, preferenza.raggioKm)
            .apply()
    }
}
