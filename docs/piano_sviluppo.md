# EcoRifornimenti - Piano di sviluppo

App mobile che, a partire dalla posizione GPS corrente, mostra su mappa i distributori
piu' convenienti entro 10-25 km per il carburante preferito dall'utente.
Dati presi live dall'API dell'Osservaprezzi Carburanti MIMIT: **nessun backend proprio**.

Fonti e misure sul campo: `analisi_fonti_dati.md`.

## 1. Scelte fissate

| Voce | Scelta |
|---|---|
| Framework | Kotlin Multiplatform + Compose Multiplatform, stesso stack di DomusHub |
| Target | **Android** (principale) e **iOS** (dietro flag `enableIos`, come DomusHub) |
| Mappa | **MapLibre nativo** su tile OpenStreetMap: nessuna API key, nessun billing |
| Backend | nessuno: chiamate dirette a `https://carburanti.mise.gov.it/ospzApi` |
| HTTP | Ktor client (engine OkHttp su Android, Darwin su iOS) + kotlinx-serialization |
| Kotlin / Compose | allineati a DomusHub: Kotlin 2.3.21, Compose MP 1.11.1 |

## 2. Struttura moduli

```
EcoRifornimenti/
├── shared/
│   ├── core-model/     # Impianto, Carburante, Prezzo, Posizione, TipoCarburante
│   ├── core-api/       # OsservaprezziClient (Ktor), DTO, griglia di ricerca, cache
│   ├── core-geo/       # Haversine, griglia esagonale, expect/actual posizione GPS
│   └── ui/             # schermate Compose + ViewModel + expect/actual MapView
├── androidApp/
└── iosApp/             # compilato solo con -PenableIos=true
```

`core-geo` e `ui` hanno un `expect/actual` per le due cose intrinsecamente native:
il **provider di posizione** (FusedLocationProvider / CLLocationManager) e la **mappa**
(MapLibre Android SDK / MapLibre iOS SDK), incapsulati dietro un'unica interfaccia comune.

## 3. Flusso utente

1. **Avvio**: richiesta permesso posizione. Se negato -> ricerca manuale per citta'.
2. **Preferenze**: in alto solo il tipo carburante (Benzina / Gasolio / Metano / GPL);
   modalita' (Self / Servito / indifferente) e raggio (10 / 15 / 25 km) dietro il
   pulsante impostazioni, in un modale. Tutte salvate localmente e ritrovate al
   riavvio.
3. **Mappa popolata**: si arriva subito sulla mappa centrata sulla posizione, con i
   distributori gia' presenti (vedi strategia di caricamento sotto).
4. **Marker** con il prezzo scritto sopra, colorato per fascia rispetto alla mediana
   di zona: verde = conveniente, giallo = in media, rosso = caro.
5. **Lista** affiancata/estraibile, ordinata per prezzo crescente, con distanza in km.
6. **Scheda impianto** (tap su marker): indirizzo, gestore, bandiera, tutti i prezzi
   con self/servito, servizi, orari, freschezza del dato, pulsante **Naviga**
   (intent `geo:` su Android, Apple Maps su iOS).

## 4. Strategia di caricamento (il punto critico)

L'API taglia il raggio a **10 km** per chiamata, quindi:

- **Raggio 10 km -> 1 sola chiamata, ~1 s.** La mappa e' popolata praticamente subito.
- **Raggio 15 km -> 7 chiamate, 25 km -> 19** (numeri effettivi della griglia implementata): si parte dalla stessa chiamata centrale (la mappa si popola
  subito nello stesso tempo), poi gli anelli esterni della griglia esagonale vengono
  caricati in background e i marker compaiono progressivamente, con un indicatore
  discreto di avanzamento. Nessuna attesa a schermo vuoto.

Regole di rispetto del servizio pubblico, non negoziabili:

- massimo **3 richieste in volo**, mai piu';
- retry con backoff 2s / 4s / 6s sul **429**, poi resa con messaggio all'utente;
- **cache** dei risultati per cella di griglia, TTL 20 minuti (i prezzi cambiano al
  massimo una volta al giorno): tornare indietro e rifare la ricerca non ricontatta il server;
- nessun polling in background, nessun prefetch speculativo;
- `User-Agent` che identifica l'app.

Dopo il merge dei risultati: dedup per `id`, filtro sulla distanza reale (Haversine)
rispetto al raggio scelto, selezione del prezzo del carburante preferito, ordinamento.

## 5. Dettagli API da gestire

- `fuelType` (es. `2-1` = Gasolio self) filtra gli impianti ma ogni impianto torna
  comunque con **tutti** i suoi carburanti: la scelta del prezzo giusto e l'ordinamento
  si fanno client-side. `priceOrder` non e' affidabile da solo.
- `address` e' sempre `null` nella ricerca: si carica on-demand da
  `/registry/servicearea/{id}` all'apertura della scheda.
- `insertDate` alimenta il badge di freschezza ("aggiornato 3 ore fa"); un prezzo
  troppo vecchio va segnalato invece che nascosto.
- Parsing tollerante: campi sconosciuti ignorati, campi mancanti non fatali.
  L'API non e' documentata e puo' cambiare.

## 6. Roadmap

| Fase | Contenuto |
|---|---|
| **F1** | **FATTA** — scaffolding KMP, `core-model`, `core-api` con client Ktor, ricerca progressiva, cache e 34 test |
| **F2** | **FATTA** per la parte comune (Haversine, griglia esagonale); permessi e provider GPS Android restano in F3 |
| **F3** | **FATTA** — mappa MapLibre + OpenFreeMap, GPS via fused provider, marker prezzo/pallino, provata sull'emulatore |
| **F4** | **FATTA** — filtri carburante/modalita'/raggio, flusso di avvio, preferenze ricordate fra i riavvii |
| **F5** | **FATTA** — progressione in UI, cache 20 min, ritento su 429 e su connessione caduta |
| **F6** | **FATTA** — scheda con indirizzo, tutti i prezzi, servizi, orari e pulsante Naviga |
| **F7** | **FATTA** — lista ordinata, fasce di prezzo, declutter delle targhette |
| **F8** | **FATTA** — MapLibre iOS, CoreLocation, NSUserDefaults, app Xcode; resta il limite TLS descritto nel README |

## 7. Convenzioni

- Versioning come DomusHub: build su **emulatore -> bump PATCH**, build installata su
  **telefono -> bump MINOR**; `versionCode` incrementato a ogni build.
- Commit lasciati sempre all'utente: le modifiche restano nel working tree.

## Cosa ha insegnato la prova sul campo

Quattro difetti che nessun test unitario avrebbe trovato, tutti emersi mettendo l'app
su un telefono (emulatore e simulatore) con dati veri:

1. **Le targhette si coprivano a vicenda.** Con 320 distributori entro 10 km la mappa
   era un tappeto illeggibile. La cura e' in due tempi: targhetta con il prezzo solo ai
   piu' convenienti, pallino colorato agli altri, e scarto delle targhette troppo
   vicine fra loro (`Targhette.kt`). Serviva anche su iOS, dove le annotazioni classiche
   di MapLibre non gestiscono affatto le collisioni.
2. **Mappa e classifica si contraddicevano.** `LazyColumn` con `key` resta ancorato
   all'elemento visibile: i risultati piu' convenienti che arrivavano dagli anelli
   esterni finivano sopra il bordo, invisibili, mentre la mappa li mostrava.
3. **Un annullamento veniva scambiato per rete caduta.** Cambiare carburante annulla la
   ricerca in corso: la `CancellationException` finiva nel `catch` generico e diventava
   "Connessione non disponibile".
4. **Gli orari non erano stringhe.** `orariapertura` e' una lista di oggetti con una
   decina di flag; il campione registrato li aveva vuoti, quindi il caso e' saltato
   fuori solo dal test che chiama il servizio vero — ed era abbastanza grave da far
   fallire il parsing dell'intera scheda.
