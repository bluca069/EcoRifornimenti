package net.ecorifornimenti.app.api

import io.ktor.client.engine.HttpClientEngine

/**
 * Il motore HTTP della piattaforma: OkHttp su Android e JVM, Darwin su iOS.
 * Isolato qui perche' e' l'unica parte del livello di rete che non e' comune.
 */
expect fun engineHttpPredefinito(): HttpClientEngine

/** Il client pronto all'uso, con il motore giusto per la piattaforma corrente. */
fun osservaprezziClient(): OsservaprezziClient = OsservaprezziClient(engineHttpPredefinito())
