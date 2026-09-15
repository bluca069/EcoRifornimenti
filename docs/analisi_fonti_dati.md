# EcoRifornimenti - Analisi fonti dati prezzi carburanti

Verifica effettuata il 2026-09-14 su https://carburanti.mise.gov.it/ospzSearch/zona
(Osservaprezzi Carburanti, MIMIT). Tutte le chiamate qui sotto sono state provate
realmente e hanno risposto 200.

## 1. API interna Osservaprezzi (non documentata)

Il sito e' una SPA Angular; il backend REST e' su `https://carburanti.mise.gov.it/ospzApi`.
Nessuna autenticazione, nessun token, nessuna API key. Nessun rate limit rilevato
(12 richieste consecutive, tutte 200, ~0.9s l'una, anche senza User-Agent).

### Ricerca per posizione GPS

```
POST https://carburanti.mise.gov.it/ospzApi/search/zone
Content-Type: application/json

{"points":[{"lat":43.7696,"lng":11.2558}],"radius":5}
```

Risposta:

```json
{"success":true,"center":{"lat":43.773,"lng":11.255},"results":[
  {"id":15532,"name":"Fibbi&Valacchi snc","brand":"Q8",
   "location":{"lat":43.78,"lng":11.29},
   "distance":"3.378",
   "insertDate":"2026-09-14T18:15:43+02:00",
   "address":null,
   "fuels":[{"id":122663391,"fuelId":1,"name":"Benzina","price":2.029,"isSelf":true}]}]}
```

- `distance` e' in km, gia' calcolata dal server.
- `address` e' SEMPRE null nella ricerca per zona: va preso dal dettaglio o dal CSV anagrafica.
- **Il raggio e' tagliato server-side a 10 km.** Misurato: radius 1 -> 4 impianti (max 1.0 km);
  5 -> 127 (max 5.0); 10 -> 332 (max 10.0); 20/50/100/200 -> sempre 332 con max 10.0 km.
  Per coprire aree piu' ampie servono piu' chiamate su una griglia di punti.

### Altri endpoint (GET, tutti aperti)

| Endpoint | Contenuto |
|---|---|
| `/ospzApi/registry/servicearea/{id}` | Dettaglio impianto: `address`, `company`, `phoneNumber`, `email`, `website`, `services`, `orariapertura`, prezzi con `insertDate` e `validityDate` |
| `/ospzApi/registry/fuels` | Tipi carburante |
| `/ospzApi/registry/brands`, `/region`, `/province`, `/town`, `/services`, `/stationtype`, `/flags`, `/highway` | Tabelle di lookup |
| `/ospzApi/search/area` | Ricerca per comune/provincia/regione |
| `/ospzApi/search/route` | Ricerca lungo un percorso |
| `/ospzApi/search/highway`, `/search/servicearea` | Autostrade e aree di servizio |

### Codici carburante (`/registry/fuels`)

Formato `<fuelId>-<self>`: `x` = indifferente, `1` = self, `0` = servito.

- `1-x` / `1-1` / `1-0` Benzina
- `2-*` Gasolio
- `3-*` Metano
- `4-*` GPL
- `323-*` L-GNC

### Limiti

1. **CORS bloccato**: `OPTIONS` con Origin esterna -> `403 Invalid CORS request`.
   Irrilevante per app mobile nativa; una web-app/PWA richiede un proxy server-side.
2. **Nessun contratto d'uso**: API non documentate, nessuno SLA, possono cambiare
   senza preavviso (sono l'API interna del frontend). `robots.txt` -> 404.

## 1-bis. Misure aggiuntive (2026-09-14, seconda sessione)

Rilevanti per un'app che chiama l'API direttamente, senza backend proprio.

### Rate limit: esiste

- 19 richieste in burst con **8 thread paralleli** -> `HTTP 429 Too Many Requests`.
- 10 richieste **sequenziali** a 500 ms -> tutte 200.
- Concorrenza **2, 3 e 4** -> tutte 200.
- Regola operativa adottata: **max 3 richieste in volo**, retry con backoff (2s, 4s, 6s) sul 429.

### `points` multipli su `/search/zone` NON funziona

Con 2 o 3 punti la risposta e' `results: []` e `center` = default Roma (41.890546, 12.49425).
Il multi-punto e' solo per `/search/route`, dove i punti sono la polyline del percorso.

### `/search/route` non e' utilizzabile come copertura ad area

Accetta molti punti in **una sola chiamata** (49 punti -> 457 impianti, copertura fino a 36 km),
ma il corridoio attorno alla polyline e' di appena **~0.5 km** (misurato con due punti quasi
coincidenti: solo 2 impianti entro 0.47 km). Per coprire un disco servirebbero centinaia di punti.
Inoltre nella risposta `distance` e' `null`.
Richiede almeno 2 punti: con 1 punto restituisce 0 risultati.

### Copertura di raggi > 10 km: griglia di chiamate `zone`

Unica strada praticabile: piu' chiamate `zone` con `radius: 10` su centri a griglia esagonale,
in parallelo (max 3), deduplicate per `id`, poi filtro client-side sulla distanza reale.

Misure a Milano, carburante `2-1` (Gasolio self), concorrenza 3:

| Raggio | Chiamate | Tempo | Impianti unici | Entro raggio |
|---|---|---|---|---|
| 10 km | **1** | ~1 s | 332 | 332 |
| 25 km | 28 (griglia ridondante) | 10.1 s | 1245 | 823 |

Con passo esagonale ottimale (centri a `r*sqrt(3)` ~ 17.3 km) le chiamate per 25 km
scendono a ~15. **Per 10 km basta una sola chiamata**: e' il caso da rendere istantaneo.

### Filtro carburante lato server: parziale

`fuelType` (es. `2-1`) riduce gli **impianti** restituiti (306 su 332) ma ogni impianto
riporta comunque **tutti** i suoi carburanti. La selezione del prezzo pertinente e
l'ordinamento vanno fatti client-side; `priceOrder` non e' affidabile come unico ordinamento.

Campi accettati da `/search/zone`: `points`, `radius`, `fuelType`, `priceOrder`, `service`.

## 2. Open data MIMIT (canale ufficiale, licenza IODL 2.0)

Aggiornamento quotidiano, prezzi "in vigore alle ore 8 del giorno precedente".
Separatore **pipe `|`** (cambiato dalla virgola il 10/02/2026).
Prima riga del file e' l'intestazione "Estrazione del YYYY-MM-DD", l'header vero e' la seconda.

- `https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv` (~3.5 MB)
  `idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine`
- `https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv`
  `idImpianto|descCarburante|prezzo|isSelf|dtComu`

Non e' real-time, ma e' completo, con indirizzo, e legalmente riutilizzabile.

## 3. Architettura scelta (2026-09-14): client diretto, nessun backend

Decisione: **nessun servizio ad hoc**. L'app chiama direttamente l'API Osservaprezzi.
Essendo un'app nativa il blocco CORS non si applica.

Conseguenze accettate:
- Dipendenza da un'API non documentata che puo' cambiare senza preavviso -> il parsing
  deve essere tollerante (campi sconosciuti ignorati) e l'errore va mostrato con garbo.
- Rate limit da rispettare: max 3 richieste in volo, backoff sul 429, cache locale.
- `address` assente nella ricerca per zona: recuperato on-demand da
  `/registry/servicearea/{id}` all'apertura della scheda impianto.

Gli open data MIMIT (sezione 2) restano il **piano B** documentato se l'API venisse
chiusa o cambiata: stessa `idImpianto`, dato completo di indirizzo, licenza IODL 2.0.

Dettaglio implementativo in `piano_sviluppo.md`.
