package net.ecorifornimenti.app.api

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

/**
 * Il motore HTTP su iOS: Darwin, cioe' `NSURLSession`.
 *
 * **Limite noto, verificato il 15/09/2026.** Il servizio dell'Osservaprezzi sta dietro
 * a un filtro che esamina l'handshake TLS: dal **simulatore** iOS la connessione viene
 * chiusa a freddo (`NSURLErrorDomain -1200`, `errSSLClosedAbort`) mentre, dalla stessa
 * macchina e nello stesso momento, `curl` risponde 10 volte su 10 e l'app Android
 * (OkHttp) funziona senza un intoppo. Anche `openssl s_client` viene resettato.
 *
 * Provati senza esito: TLS 1.2 forzato su NSURLSession e il motore CIO, che porta un
 * proprio stack TLS. Il filtro e' fuori dal nostro controllo, quindi si resta su
 * Darwin — l'engine naturale della piattaforma — e ci si affida al ritento di rete
 * gia' presente nel client.
 *
 * Da verificare su un iPhone vero, dove rete e stack di sistema non sono quelli del
 * simulatore. Se il rifiuto si ripetesse anche li', le strade sono due: un piccolo
 * proxy nostro davanti al servizio, oppure il canale open data del MIMIT descritto in
 * `docs/analisi_fonti_dati.md`, che sta su un altro host.
 */
actual fun engineHttpPredefinito(): HttpClientEngine = Darwin.create()
