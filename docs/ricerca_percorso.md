# EcoRifornimenti - Ricerca per percorso (F9)

Piano della funzione: si indica una destinazione entro 50 km, l'app traccia il
percorso stradale e cerca i distributori piu' convenienti **lungo la strada che si
fara' davvero**, non attorno a un punto.

Le misure di questo documento sono state fatte il **2026-09-18** sul percorso
Firenze -> Siena, carburante `1-1` (Benzina self). Tutte le chiamate qui sotto sono
state eseguite realmente.

## 1. Perche' non basta quello che c'e' gia'

La ricerca attuale copre un **disco** attorno a un punto. Su un viaggio il disco e' la
forma sbagliata: a 25 km da Firenze ci sono centinaia di distributori che non si
incontreranno mai, e nessuno di quelli che stanno al chilometro 60. Serve un corridoio
attorno a una linea.

Tre cose che oggi non esistono: il **tracciato** (dove passa la strada), la
**geocodifica** (da "Siena" a una coordinata) e un **motore di ricerca sulla linea**.
Il resto — prezzi, fasce, targhette, scheda, cache, mappa — si riusa tale e quale.

## 2. Misure sul campo

### 2.1 Il tracciato: OSRM di FOSSGIS

```
GET https://routing.openstreetmap.de/routed-car/route/v1/driving/
    11.2558,43.7696;11.3308,43.3188?overview=full&geometries=geojson
```

| Voce | Misura |
|---|---|
| Esito | `200`, **151 ms**, 45 KB |
| Percorso | 74,9 km, 74 minuti |
| Punti della polyline | 2.066 |

Nessuna chiave, nessun account, nessuna quota dichiarata. **Non c'e' pero' nessuno
SLA**: sono i server della fondazione OSM, a uso equo. Vedi §6.

### 2.2 La geocodifica: Photon

```
GET https://photon.komoot.io/api/?q=siena&limit=3&lat=43.77&lon=11.25
```

`200` in **168 ms**, primi tre risultati tutti pertinenti, con `state` e `countrycode`
per disambiguare. Il parametro `lang=it` **non e' supportato** (solo `default`, `de`,
`en`, `fr`): si usa `default`, che rende comunque i nomi locali da OSM.
Il `lat`/`lon` della posizione corrente come bias e' quello che mette Siena-citta'
davanti alle frazioni omonime.

### 2.3 `/search/route` dell'Osservaprezzi

L'endpoint era gia' stato visto nell'analisi del 14/09 e **scartato per la ricerca ad
area**, perche' il corridoio stretto non permette di coprire un disco. Per la ricerca
lungo un percorso quel corridoio stretto non e' un limite: e' esattamente il requisito.

Corpo della richiesta: gli stessi campi di `/search/zone`, con `points` che porta la
polyline ricampionata.

```json
{"points":[{"lat":43.7706,"lng":11.2578}, ...],"fuelType":"1-1","priceOrder":"asc"}
```

| Passo di campionamento | Punti inviati | Impianti resi | Tempo |
|---|---|---|---|
| 0,8 km | 88 | 44 | 5,4 s |
| **2 km** | **38** | **41** | **1,8 s** |
| 5 km | 16 | 38 | 1,6 s |

**Una sola chiamata copre tutti i 75 km.** Il tetto di punti misurato il 14/09 (49) era
solo il campione provato: 88 punti sono stati accettati senza storie. Il tempo di
risposta cresce con i punti molto piu' di quanto cresca il risultato: **il passo di 2 km
rende il 93% degli impianti in un terzo del tempo**, ed e' il valore da adottare.

La misura e' su 75 km, ma il tetto adottato e' 50 (§7.5): a passo di 2 km sono **~26
punti**, un terzo di quelli gia' accettati senza storie. Il dimensionamento e' comodo.

Corridoio reale attorno alla polyline, misurato sui 44 impianti resi:

| Voce | Misura |
|---|---|
| Deviazione mediana | 0,25 km |
| Deviazione massima | **0,94 km** |
| Impianti oltre 0,5 km | 11 su 44 |

Il corridoio quindi e' di circa **1 km**, non di 0,5 km come stimato a settembre: la
misura di allora era stata fatta con due punti quasi coincidenti, che e' il caso
peggiore. Va corretto in `analisi_fonti_dati.md`.

`distance` resta **`null`** in risposta, come gia' noto: la distanza la ricalcoliamo
comunque da noi, quindi non cambia nulla.

### 2.4 Verita' a terra: quanto si perde?

Per sapere se `/search/route` restituisce *tutto* quello che c'e' lungo la strada, lo
stesso percorso e' stato coperto con **9 chiamate `/search/zone`** (raggio 10 km, centri
ogni 10 km lungo la polyline), deduplicate e poi filtrate sulla distanza dalla linea.

| Voce | Misura |
|---|---|
| Chiamate | 9 |
| Tempo sequenziale | 61,1 s (a 3 in parallelo: ~20 s) |
| Impianti unici | 204 |
| Entro 1 km dal percorso | 50 |
| Entro 2 km | 76 |
| Entro 3 km | 110 |

**`/search/route` ne trova 44 dei 50 che stanno entro 1 km: l'88%, in una chiamata e
1,8 secondi, contro nove chiamate e venti secondi.** I sei mancanti sono ai bordi del
corridoio; nessuno di quelli entro mezzo chilometro sfugge.

> Nota collaterale: le chiamate `zone` di oggi hanno impiegato ~6,8 s l'una, contro
> l'~1 s misurato il 14/09. Una sola sessione non basta a dire se il servizio sia
> diventato piu' lento o se siano queste celle a essere pesanti, ma se il dato si
> ripetesse cambierebbe anche i tempi della ricerca attuale a 15 e 25 km.

## 3. Architettura scelta

Nessun modulo nuovo: la funzione si incastra dove sta gia' la roba dello stesso genere.

| Dove | Cosa si aggiunge |
|---|---|
| `core-geo` | `Polilinea.kt`: ricampionamento a passo costante, distanza punto-polilinea, **progressivo in km** lungo il percorso. Geometria pura, zero rete, tutta testabile |
| `core-api` | `ClientRotta.kt` (OSRM) e `ClientLuoghi.kt` (Photon), con i loro DTO tolleranti come gli altri |
| `core-api` | `RicercaPercorso.kt`: campiona la polyline a 2 km, chiama `/search/route`, ricalcola distanze e progressivi, ordina |
| `ui` | il campo destinazione, la polyline sulla mappa, la riga di lista con progressivo e deviazione |

`OsservaprezziClient` guadagna un solo metodo, `cercaPerPercorso(punti, fuelType)`,
gemello di `cercaPerZona`: stesso ritento, stesso rispetto del 429, stesso parsing.

### 3.1 Il modello di stato: uno solo, non due

`ModelloRicerca` gestisce gia' posizione, preferenze, annullamento della ricerca in
corso, scheda impianto e fasce. Duplicarlo per il percorso significherebbe duplicare
proprio le parti che funzionano.

La proposta e' un **ambito** dentro lo stato che c'e':

```kotlin
sealed interface Ambito {
    data class Intorno(val centro: Posizione) : Ambito
    data class LungoIlPercorso(val percorso: Percorso) : Ambito
}
```

`StatoRicerca` prende `ambito` e, quando e' un percorso, la lista `impianti` porta per
ciascuno il **progressivo** e la **deviazione**. Tutto il resto della schermata — mappa,
classifica, scheda, fasce, pull-to-refresh — non sa nemmeno di che ambito si tratti.

### 3.2 Cache

La cache attuale e' per cella di griglia e non serve qui. Si aggiunge una voce per
**tratta**: chiave = hash dei punti campionati + `fuelType`, stesso TTL di 20 minuti.
Cosi' tornare indietro dalla scheda, o cambiare modalita' self/servito, non ricontatta
ne' OSRM ne' l'Osservaprezzi. La rotta di OSRM si memorizza a parte, con chiave
partenza+destinazione: una rotta stradale non cambia in venti minuti.

## 4. La schermata

Non una schermata nuova: la stessa, con un ambito diverso.

- **Ingresso**: accanto ai chip del carburante, un campo "Dove stai andando?". Si scrive,
  Photon propone, si sceglie. Se il percorso supera i **50 km** si avvisa e non
  si procede: e' il tetto che tiene sotto controllo il carico sui servizi.
  Lo stesso campo, quando il GPS manca, fa da **ricerca manuale per citta'** (§5, F9.2).
- **Mappa**: la polyline del percorso disegnata sotto i marker, partenza e arrivo
  marcati. L'inquadratura si allarga a contenere tutto il tracciato invece del cerchio.
- **Lista**: ordinata **per prezzo**, come nell'altro ambito. Ogni riga porta
  **`+3 cent · km 42 · 400 m fuori strada`**. Nell'intestazione del pannello un
  interruttore passa all'ordine **per progressivo**: vedi §7.1 per il perche' siano due
  ordini espliciti e non un punteggio solo.
- **Uscita**: si torna all'ambito "intorno a me" chiudendo la destinazione.

Il chip del raggio (5/10/15/25 km) non ha senso in questo ambito: al suo posto va il
**corridoio**, cioe' quanto lontano dalla strada si e' disposti a deviare. Con
`/search/route` il corridoio e' ~1 km e non e' negoziabile; per andare oltre serve la
griglia di chiamate `zone`, che parte solo su richiesta esplicita (§5, F9.5).

**Niente destinazioni recenti** (§7.3): il campo si riscrive ogni volta.

## 5. Fasi

| Fase | Contenuto | Rete |
|---|---|---|
| **F9.1** | `Polilinea.kt`: ricampionamento, distanza punto-linea, progressivo. Test con percorsi costruiti a mano e con la polyline vera registrata | no |
| **F9.2** | `ClientRotta` (OSRM) e `ClientLuoghi` (Photon), DTO, errori parlanti. Test su risposte registrate, piu' un test di integrazione spento di default come quello che c'e' gia'. **Con il geocoder in casa si chiude anche il vicolo cieco del permesso negato** (§7.4) | si |
| **F9.3** | `cercaPerPercorso` sul client Osservaprezzi + `RicercaPercorso` con campionamento a 2 km, cache di tratta, ordinamento per prezzo e per progressivo | si |
| **F9.4** | UI: campo destinazione, polyline sulla mappa, riga di lista con progressivo e deviazione, interruttore dell'ordinamento, uscita dall'ambito | — |
| **F9.5** | Corridoio largo (2-3 km) con la griglia `zone` lungo il percorso, progressiva come quella attuale, dietro il gesto "cerca anche piu' lontano dalla strada" (§7.2) | si |

F9.1 e' interamente verificabile senza rete: e' li' che sta la logica che puo'
sbagliare in silenzio (un progressivo storto ordina male tutta la lista).

### 5.1 Il vicolo cieco del permesso negato, dentro F9.2

Oggi, se l'utente nega la posizione, la schermata mostra un messaggio con "Riprova" che
richiama `avvia()`, ritrova il permesso negato e rimostra lo stesso messaggio: il
dialogo di sistema parte solo in `onCreate`, e la "ricerca manuale per citta'" promessa
da `piano_sviluppo.md` §3.1 non e' mai stata scritta. L'app, senza GPS, non ha alcuna
via d'ingresso.

`ClientLuoghi` e' esattamente il pezzo che mancava: `ModelloRicerca.cercaIn(posizione)`
esiste gia' e funziona. Serve solo mostrare il campo di ricerca anche nello stato di
errore e passargli la coordinata scelta. Si aggiunge, gia' che ci si e', il rimando alle
impostazioni di sistema per chi vuole concedere il permesso dopo averlo negato.

## 6. Rispetto dei servizi

Le regole gia' adottate per l'Osservaprezzi valgono uguali per i due servizi nuovi, con
un'aggravante: **OSRM e Photon sono infrastrutture donate alla comunita' OSM, senza SLA
e con un uso equo atteso**. Per un'app distribuita sugli store la strada corretta,
prima o poi, e' un'istanza propria o un fornitore con chiave e contratto. Nel frattempo:

- **una sola chiamata di rotta per ricerca**, mai un ricalcolo automatico;
- **nessuna navigazione**: F9 e' "pianifica prima di partire", non turn-by-turn. Niente
  aggiornamento mentre ci si muove, che moltiplicherebbe le chiamate per tutto il viaggio;
- percorso **entro 50 km**, con il tetto verificato sulla distanza stradale resa da OSRM
  prima di chiamare l'Osservaprezzi;
- autocompletamento della destinazione **a mano ferma** (300 ms di quiete), mai a ogni
  tasto;
- cache di rotta e di tratta, 20 minuti;
- `User-Agent` che identifica l'app, come gia' si fa con l'Osservaprezzi.

## 7. Decisioni prese (21/09/2026)

### 7.1 Ordinamento: prezzo, con il progressivo come seconda chiave esplicita

Niente punteggio composito che pesi prezzo e deviazione insieme. Il motivo e'
aritmetico: **dentro un corridoio di 1 km la deviazione e' economicamente
irrilevante**. Un pieno da 50 litri con uno scarto di 10 cent/L vale 5 euro; deviare di
900 metri, andata e ritorno, costa circa 15 centesimi. Il prezzo domina di trenta
volte, quindi un punteggio composito produrrebbe quasi lo stesso ordine del prezzo
nudo, in cambio dell'impossibilita' di spiegare perche' un impianto stia sopra un altro.

La deviazione si scrive sulla riga come **comodita'** ("ci arrivo facilmente?"), non
come fattore di valore.

Quello che il prezzo davvero non dice e' l'**autonomia**: il piu' conveniente al
chilometro 40 non serve a chi ha la spia accesa. Quindi la seconda chiave non e' un
prezzo pesato, e' "cosa incontro prima" — un interruttore nell'intestazione del
pannello, presente solo in questo ambito.

### 7.2 Corridoio: 1 km di default, piu' largo solo a richiesta

Un chilometro prende 44 impianti dei 50 che esistono entro quel chilometro, in una
chiamata e 1,8 secondi. Portarlo a 2 km ne aggiunge 26, ma costa nove chiamate e venti
secondi. E' la stessa filosofia gia' adottata per gli anelli esterni della griglia: il
caso istantaneo resta istantaneo, il resto si chiede. Non diventa un'impostazione
permanente, perche' chi la lasciasse a 3 km si ritroverebbe venti secondi di attesa a
ogni ricerca senza capire da dove arrivino.

### 7.3 Niente destinazioni recenti

Photon risponde in 168 ms e il campo si riscrive in tre lettere. Salvarle vorrebbe dire
affiancare un secondo archivio ad `ArchivioPreferenze`, che oggi sa leggere e scrivere
solo una `PreferenzaRicerca`: lavoro su entrambe le piattaforme per un risparmio di
pochi secondi. Se dall'uso sul campo dovesse risultare fastidioso, si riapre.

### 7.4 La ricerca manuale per citta' entra in F9.2

Vedi §5.1: e' il rilievo piu' grave emerso dall'analisi del codice, e il geocoder che lo
risolve lo stiamo scrivendo comunque.

### 7.5 Tetto del percorso: 50 km

Oltre non ha senso — chi fa piu' strada non sceglie il distributore in partenza. Il
tetto e' anche un regalo al dimensionamento: a passo di 2 km, 50 km di strada fanno
**~26 punti per chiamata**, contro gli 88 gia' accettati senza storie dal servizio.
Una sola chiamata bastera' sempre, con ampio margine. Il controllo si fa sulla
**distanza stradale** resa da OSRM, non sulla distanza in linea d'aria fra i due punti:
sono due numeri diversi, e quello che conta e' il primo.

## 7-bis. Stato dell'implementazione (06/10/2026)

Fatte **F9.1-F9.4** come da piano: geometria (ricampionamento a 2 km, proiezione sui
segmenti per progressivo e deviazione), `PercorsoOsrm` su FOSSGIS, `LuoghiPhoton` con
bias geografico, `cercaLungoPercorso` sul client, tetto di 50 km sulla distanza
stradale, cache di tratta, e in UI il campo destinazione, la polilinea sulla mappa e la
riga con `km 42 · 400 m fuori strada`.

Verificato contro i servizi veri (Poggibonsi-Siena): 28 km, 32 minuti, 18 distributori
in una chiamata sola, il piu' conveniente a 1,989 dopo 24 km e 638 metri fuori strada.

**Restano da fare** le parti che il piano prevedeva e che non sono state scritte:

- **F9.5**: corridoio largo 2-3 km con la griglia `zone`, dietro gesto esplicito.
- **Ricerca manuale per citta' senza GPS** (§5.1): il geocoder adesso c'e', ma la
  schermata di errore non lo usa ancora — il vicolo cieco del permesso negato e'
  ancora li'.
- **Interruttore d'ordinamento prezzo / progressivo** (§7.1): oggi l'ordine e' solo per
  prezzo, con il progressivo come seconda chiave.

Una nota sulla geometria: la deviazione **va calcolata sui segmenti**, non sui vertici.
A passo di due chilometri il vertice piu' vicino puo' stare a un chilometro di
distanza, e un distributore a 400 metri dalla strada risulterebbe a 1,1 km — tre volte
tanto, proprio sul numero che si mostra all'utente.

## 8. Correzioni da riportare altrove

- ~~`analisi_fonti_dati.md`: il corridoio di `/search/route` e' ~1 km, non ~0,5 km, e il
  tetto di punti e' almeno 88, non 49.~~ fatto il 06/10.
- ~~`README.md`: raggi selezionabili e conteggio dei test.~~ fatto il 06/10 (134 test).
