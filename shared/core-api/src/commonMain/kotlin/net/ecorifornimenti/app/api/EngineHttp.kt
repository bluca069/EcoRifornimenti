package net.ecorifornimenti.app.api

import io.ktor.client.engine.HttpClientEngine

/**
 * Il motore HTTP della piattaforma: OkHttp su Android e JVM, Darwin su iOS.
 * Isolato qui perche' e' l'unica parte del livello di rete che non e' comune.
 */
expect fun engineHttpPredefinito(): HttpClientEngine

/*
 * Le tre fabbriche qui sotto esistono perche' le app non debbano conoscere Ktor: il
 * motore HTTP e' un dettaglio di questo modulo, e il suo tipo non e' nemmeno visibile
 * da fuori.
 */

/** Il client dell'Osservaprezzi, con il motore giusto per la piattaforma corrente. */
fun osservaprezziClient(): OsservaprezziClient = OsservaprezziClient(engineHttpPredefinito())

/** Chi calcola la strada fra due punti (OSRM su dati OpenStreetMap). */
fun servizioPercorsoPredefinito(): ServizioPercorso = PercorsoOsrm(engineHttpPredefinito())

/** Chi trasforma un nome di luogo in coordinate (Photon su dati OpenStreetMap). */
fun servizioLuoghiPredefinito(): ServizioLuoghi = LuoghiPhoton(engineHttpPredefinito())
