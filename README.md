# EcoRifornimenti

App mobile che mostra su mappa i distributori di carburante piu' convenienti attorno
alla posizione GPS corrente, entro 10, 15 o 25 km, per il carburante scelto.

Dati live dall'**Osservaprezzi Carburanti** del MIMIT. Nessun backend proprio.

## Stato

| Fase | Contenuto | Stato |
|---|---|---|
| F1 | Scaffolding KMP, `core-model`, `core-api` (client Ktor, ricerca progressiva, cache) | fatta |
| F2 | `core-geo`: distanze, griglia di ricerca, provider GPS | fatta |
| F3 | Mappa MapLibre, GPS, marker prezzo | fatta |
| F4 | Filtri e preferenze persistite | fatta |
| F5 | Caricamento progressivo, cache, gestione rete | fatta |
| F6 | Scheda impianto con indirizzo, orari, servizi, navigazione | fatta |
| F7 | Lista ordinata, fasce di prezzo, declutter targhette | fatta |
| F8 | Porting iOS | fatta, con un limite noto (vedi sotto) |

Piano completo in `docs/piano_sviluppo.md`, analisi delle fonti dati in
`docs/analisi_fonti_dati.md`.

## Moduli

```
shared/core-model   modelli e preferenze di ricerca, senza dipendenze di piattaforma
shared/core-geo     distanze, griglia delle celle, provider GPS per piattaforma
shared/core-api     client dell'Osservaprezzi, ricerca progressiva, cache
shared/ui           stato della schermata, schermata Compose, mappa (expect/actual)
androidApp          app Android
iosApp              app iOS (Xcode + CocoaPods)
```

La stessa schermata Compose gira su entrambi i sistemi. Per piattaforma cambiano solo
tre cose, dietro `expect/actual`: la **mappa** (MapLibre Android / MapLibre iOS), la
**posizione** (fused provider / CoreLocation) e il posto dove si salvano le
**preferenze** (SharedPreferences / NSUserDefaults).

La mappa e' **MapLibre** con tile **OpenFreeMap** (dati OpenStreetMap): nessuna chiave,
nessuna quota, nessun account di fatturazione.

## Build e test

Serve il JDK 21 (il percorso e' in `gradle.properties`, come in DomusHub).

```bash
./gradlew :shared:core-model:jvmTest :shared:core-geo:jvmTest \
          :shared:core-api:jvmTest :shared:ui:testDebugUnitTest
```

74 test. Per l'app Android: `./gradlew :androidApp:assembleDebug`.

I test usano risposte reali del servizio, registrate in `RispostaEsempio`.
C'e' anche un test che chiama davvero l'Osservaprezzi, spento di default perche'
dipende dalla rete e fa carico su un servizio pubblico:

```bash
./gradlew :shared:core-api:jvmTest -Dintegrazione=true --rerun-tasks
```

### iOS

`enableIos=true` e' attivo in `gradle.properties` (serve al podspec del framework).

```bash
cd iosApp && pod install            # la prima volta
xcodebuild -workspace iosApp.xcworkspace -scheme iosApp \
           -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' build
```

**Limite noto (15/09/2026).** Dal simulatore iOS il servizio dell'Osservaprezzi chiude
l'handshake TLS (`NSURLErrorDomain -1200`): l'app mostra "Connessione non disponibile".
Nello stesso momento, dalla stessa macchina, `curl` risponde 10 volte su 10 e l'app
Android funziona senza intoppi; anche `openssl s_client` viene resettato, quindi il
filtro davanti al servizio discrimina i client dall'handshake. Provati senza esito TLS
1.2 forzato e il motore CIO. Da verificare su un iPhone vero; se il rifiuto si
ripetesse, restano un proxy nostro o il canale open data del MIMIT (altro host),
descritto in `docs/analisi_fonti_dati.md`.

## Come funziona la ricerca

L'API copre al massimo **10 km per chiamata**, quindi:

| Raggio | Chiamate |
|---|---|
| 10 km | 1 |
| 15 km | 7 |
| 25 km | 19 |

La prima cella interrogata e' sempre quella dell'utente: la mappa si popola in circa
un secondo a qualunque raggio, e gli anelli esterni la riempiono man mano.
Le chiamate viaggiano a **3 alla volta** (a 8 in parallelo il servizio risponde 429),
con backoff sul 429 e cache di 20 minuti per cella.

## Sulla mappa

Con qualche centinaio di distributori nel raggio piu' stretto, disegnarli tutti con il
prezzo produce un tappeto illeggibile. Quindi: **targhetta con il prezzo ai dodici piu'
convenienti**, pallino colorato per fascia a tutti gli altri, e collisione attiva con
priorita' al prezzo piu' basso — chi conviene resta visibile, gli altri riemergono
ingrandendo.

## Le scelte dell'utente

In alto resta solo il **carburante**, che e' quello che si cambia davvero: quattro chip
su una riga sola, e accanto il pulsante che apre le altre impostazioni. **Rifornimento**
(self / servito / indifferente) e **raggio** (10, 15, 25 km) stanno in un modale, perche'
si toccano di rado e in cima rubavano spazio alla mappa a ogni apertura.

Tutte e tre le scelte si ricordano fra un avvio e l'altro, e alla prima apertura si
parte da **Benzina · self · 10 km**.

## Verticale e orizzontale

In verticale la mappa prende tutto lo schermo e i risultati stanno in fondo. In
orizzontale la mappa resta a sinistra e i risultati passano in una **colonna stretta a
destra** (260 dp), dove non le rubano meta' schermo: la decisione dipende solo da quale
lato sia piu' lungo, non dal tipo di dispositivo. La scheda di un distributore prende il
posto della classifica nello stesso pannello, in entrambe le disposizioni.
