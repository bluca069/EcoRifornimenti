package net.ecorifornimenti.app.ui

/**
 * Su Android la versione arriva dal `BuildConfig` del modulo applicazione, che qui non
 * e' visibile: la inietta `MainActivity` all'avvio.
 */
private var versione: String = ""

fun impostaVersioneApp(nome: String, codice: Int) {
    versione = "$nome ($codice)"
}

actual fun versioneApp(): String = versione
