package net.ecorifornimenti.app.ui

import net.ecorifornimenti.app.model.PreferenzaRicerca

/**
 * Dove restano le scelte dell'utente fra un avvio e l'altro.
 *
 * Chi guida fa quasi sempre lo stesso pieno: ritrovare "gasolio self, 15 km" gia'
 * impostato e' la differenza fra aprire l'app e doverla riconfigurare ogni volta.
 */
interface ArchivioPreferenze {
    fun leggi(): PreferenzaRicerca
    fun salva(preferenza: PreferenzaRicerca)
}

/** Archivio che non ricorda nulla: per i test e le anteprime. */
class PreferenzeInMemoria(
    private var preferenza: PreferenzaRicerca = PreferenzaRicerca(),
) : ArchivioPreferenze {
    override fun leggi() = preferenza
    override fun salva(preferenza: PreferenzaRicerca) { this.preferenza = preferenza }
}

/**
 * La codifica su stringhe usata dagli archivi di piattaforma.
 *
 * Sta qui, in comune, perche' Android e iOS devono leggere e scrivere lo stesso
 * formato e perche' cosi' e' verificabile con un test normale.
 */
object CodificaPreferenze {
    const val CHIAVE_TIPO = "tipo"
    const val CHIAVE_MODALITA = "modalita"
    const val CHIAVE_RAGGIO = "raggio"

    /**
     * Ricostruisce la preferenza da quel che si e' trovato salvato, ignorando i valori
     * che non si riconoscono: un archivio scritto da una versione futura, o corrotto,
     * deve far ripartire l'app dai valori predefiniti, non impedirne l'avvio.
     */
    fun decodifica(tipo: String?, modalita: String?, raggio: Int?): PreferenzaRicerca {
        val base = PreferenzaRicerca()
        return PreferenzaRicerca(
            tipo = net.ecorifornimenti.app.model.TipoCarburante.entries
                .firstOrNull { it.name == tipo } ?: base.tipo,
            modalita = net.ecorifornimenti.app.model.ModalitaErogazione.entries
                .firstOrNull { it.name == modalita } ?: base.modalita,
            raggioKm = raggio?.takeIf { it in PreferenzaRicerca.RAGGI_KM } ?: base.raggioKm,
        )
    }
}

/**
 * La versione dell'app, per mostrarla nelle impostazioni.
 *
 * La dichiara la piattaforma: su Android sta nel `BuildConfig`, su iOS nell'Info.plist,
 * e il modulo comune non conosce ne' l'uno ne' l'altro.
 */
expect fun versioneApp(): String
