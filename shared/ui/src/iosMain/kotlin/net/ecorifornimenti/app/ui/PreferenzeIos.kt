package net.ecorifornimenti.app.ui

import net.ecorifornimenti.app.model.PreferenzaRicerca
import platform.Foundation.NSUserDefaults

/** Le preferenze su iOS: gli `NSUserDefaults`, che sono l'equivalente di casa. */
class PreferenzeIos(
    private val archivio: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ArchivioPreferenze {

    override fun leggi(): PreferenzaRicerca = CodificaPreferenze.decodifica(
        tipo = archivio.stringForKey(CodificaPreferenze.CHIAVE_TIPO),
        modalita = archivio.stringForKey(CodificaPreferenze.CHIAVE_MODALITA),
        raggio = archivio.integerForKey(CodificaPreferenze.CHIAVE_RAGGIO).toInt().takeIf { it > 0 },
    )

    override fun salva(preferenza: PreferenzaRicerca) {
        archivio.setObject(preferenza.tipo.name, CodificaPreferenze.CHIAVE_TIPO)
        archivio.setObject(preferenza.modalita.name, CodificaPreferenze.CHIAVE_MODALITA)
        archivio.setInteger(preferenza.raggioKm.toLong(), CodificaPreferenze.CHIAVE_RAGGIO)
    }
}
