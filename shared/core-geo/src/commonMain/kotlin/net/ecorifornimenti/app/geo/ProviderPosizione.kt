package net.ecorifornimenti.app.geo

import net.ecorifornimenti.app.model.Posizione

/**
 * Da dove arriva la posizione dell'utente.
 *
 * E' un'interfaccia comune con implementazioni per piattaforma invece di un
 * `expect/actual`: cosi' la UI puo' ricevere un provider finto nei test e nelle
 * anteprime senza tirarsi dietro nulla di Android.
 */
interface ProviderPosizione {

    /** Se l'utente ha gia' concesso il permesso di posizione. */
    fun permessoConcesso(): Boolean

    /**
     * La posizione corrente, o `null` se non e' ottenibile (permesso negato, GPS
     * spento, nessun fix). Non lancia: il chiamante deve poter ripiegare sulla
     * ricerca manuale senza gestire eccezioni.
     */
    suspend fun posizioneCorrente(): Posizione?

    /**
     * L'ultima posizione nota, se il sistema ne ha una in cache. Molto piu' veloce di
     * un fix nuovo: serve a centrare la mappa subito, mentre il fix vero arriva.
     */
    suspend fun ultimaPosizioneNota(): Posizione? = null
}

/** Provider fisso, per test e anteprime. Milano se non si dice altro. */
class PosizioneFissa(
    private val posizione: Posizione = Posizione(45.4642, 9.19),
    private val concesso: Boolean = true,
) : ProviderPosizione {
    override fun permessoConcesso() = concesso
    override suspend fun posizioneCorrente() = if (concesso) posizione else null
    override suspend fun ultimaPosizioneNota() = posizioneCorrente()
}
