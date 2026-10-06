# EcoRifornimenti — monetizzazione e mercato

Valutazione al 6 ottobre 2026. In breve: **come app consumer da vendere o da far
vivere di pubblicita' non sta in piedi**; ha senso come componente B2B o come
dimostratore tecnologico. Sotto i numeri su cui si fonda questa conclusione.

## 1. Il mercato e' presidiato, e da luglio anche dallo Stato

| Concorrente | Dati | Peso |
|---|---|---|
| **Osservaprezzi Carburanti (MIMIT)** | gli stessi nostri | app **ufficiale**, gratuita, dal 20/07/2026 su iOS e Android |
| **Prezzi Benzina** (Milano, 2012) | Osservaprezzi **+ segnalazioni degli utenti** | 4,6 milioni di download, ~28.000/mese, 6 dipendenti |
| **Fuelio** | prezzi + registro consumi | 5 milioni di download, 4,2 stelle su 136.000 recensioni |
| Waze, Google Maps | prezzi integrati nella navigazione | installati di default su mezzo mondo |

Il punto dolente e' il primo. Dal 20 luglio 2026 il Ministero distribuisce **la sua
app**, gratuita, senza pubblicita', con gli stessi identici dati che usiamo noi —
anzi con in piu' il confronto col prezzo medio regionale. Qualunque nostra proposta
a pagamento parte da li': "perche' dovrei pagare per quello che il ministero mi da'
gratis?".

Il secondo punto dolente e' Prezzi Benzina: quattordici anni di vantaggio e, cosa
che non si recupera con il codice, una **community che segnala i prezzi**. Il loro
database vede anche le pompe bianche che all'Osservaprezzi arrivano tardi o male.

**Non abbiamo alcun vantaggio sui dati**: sono pubblici, gratuiti e identici per
tutti. L'unico terreno di gioco e' l'esperienza d'uso, che e' il terreno dove si
vince solo spendendo in marketing.

## 2. L'economia della pubblicita' non torna

eCPM italiani 2026: **banner ~1 €**, interstitial 5-8 €, rewarded video 10-20 €.

Uno scenario ottimistico per un'app senza marketing:

| | |
|---|---|
| Utenti attivi al mese | 10.000 |
| Sessioni per utente | 6 |
| Impression per sessione | 1,5 |
| Impression al mese | 90.000 |
| **Ricavo lordo mensile** (eCPM 1 €) | **~90 €** |

Novanta euro al mese, da cui togliere i 99 $/anno di Apple. Per arrivare a 20.000 €
l'anno — nemmeno uno stipendio — servirebbero **circa 200.000 utenti attivi**.
Comprarli costerebbe, a 0,50-1,50 € per installazione, **100.000-300.000 €**.

Lo stesso vale per il freemium: con l'1-2% di conversione su 10.000 utenti e 2,99 €
una tantum, si parla di qualche centinaio di euro **in totale**, non all'anno.

Il lato buono: l'app **non costa nulla da esercire** (nessun backend, mappe
OpenFreeMap senza quota, dati pubblici). Tenerla viva e' gratis. Quindi la domanda
non e' "come ripaga i costi" ma "a cosa serve".

## 3. Le strade che reggono davvero

### A. Componente dentro prodotti che vendiamo gia' — la piu' sensata

Non vendere l'app, **regalarla come servizio dentro qualcosa di gia' pagato**.
Nelle app per i lavoratori (Socialws) un "dove conviene fare il pieno" e' un
servizio di welfare a costo marginale zero: il codice c'e', i dati sono gratis,
l'esercizio non costa. Non produce ricavo diretto, ma e' valore aggiunto in
trattative dove il prezzo e' gia' fissato.

### B. Flotte piccole: artigiani, PMI, cooperative

Chi ha 5-30 veicoli oggi non ha nulla: le fuel card (Enilive Multicard, DKV, IP
Card) danno la carta e la fattura, non lo strumento per capire *dove* conviene
fermarsi e *quanto* si e' speso per mezzo. Il confronto di prezzo e' la meta' del
problema; l'altra meta' — registro rifornimenti, consumi per veicolo, rimborsi
chilometrici, export per il commercialista — **oggi l'app non la fa**.

Ordine di grandezza, guardando cosa costa il software di nota spese (ZTravel
Zucchetti 6,50 €/utente/mese, Emburse 6-7 €):

| | |
|---|---|
| Prezzo | 4 €/veicolo/mese |
| 50 clienti da 15 veicoli | 36.000 €/anno |
| 10 clienti da 15 veicoli | 7.200 €/anno |

Numeri raggiungibili **solo vendendo alla base clienti che gia' abbiamo**: con
acquisizione da zero non tornano. Richiede 2-3 mesi di sviluppo in piu' per la
parte gestionale, che e' il vero prodotto — il confronto prezzi e' il richiamo.

### C. Affiliazione con le carte carburante

Portare contratti a DKV, UTA, Enilive e simili, a provvigione. Si innesta bene su
(B): chi sta guardando dove costa meno e' il pubblico giusto. Ma senza volume sono
spiccioli, e si introduce un conflitto d'interessi con la neutralita' del confronto.

### D. Dimostratore tecnologico

Un'app Kotlin Multiplatform che gira su Android e iOS dalla stessa base, con mappe,
GPS e un servizio pubblico non documentato, e' un pezzo di portfolio concreto e
riutilizzabile: la struttura (modulo dati, modulo mappa, schermata comune) si
ricicla su qualunque altra app mobile a venire. Questo valore c'e' gia' ed e'
incassato.

## 3-bis. La ricerca sul percorso: non basta da sola, ma indica dove scavare

Domanda posta: l'app del Ministero e' lenta e non fa confronti immediati — la
ricerca lungo il percorso puo' essere il nostro terreno?

**Come funzione in se', no: ce l'hanno gia' tutti.**

| Chi | Ricerca lungo il percorso |
|---|---|
| App MIMIT | **si'**, "ricerca in zona (5 km) o lungo un percorso" |
| Fuelio, iFuel | si', con pianificazione del viaggio |
| Waze | si', **durante la navigazione** — cioe' nel momento in cui si guarda |

Waze e' il punto: la ricerca sul percorso serve mentre si guida, e li' c'e' gia'
un'app aperta sul cruscotto che lo fa.

**Ma la critica all'app ufficiale e' fondata, e documentata.** Codacons parla di
"procedura tortuosa che impedisce di ottenere un confronto immediato fra i prezzi
della zona"; l'Unione Nazionale Consumatori la definisce "inutile cosi'", e chiede
due cose precise: che **all'apertura mostri subito i 3-5 prezzi piu' convenienti**
e che il raggio massimo passi da 10 a 30 km.

La prima e' esattamente quello che EcoRifornimenti fa gia': si apre e si vede la
classifica ordinata per prezzo, con lo scarto in centesimi dal piu' basso. Siamo
oggettivamente migliori dell'app di Stato sull'immediatezza — il che **non cambia
l'economia**: restiamo senza un canale per farci trovare.

### Quello che nessuno fa davvero: dire se conviene deviare

"Mostrami i distributori sul tragitto" e' una commodity. "**Mi conviene uscire?**"
no — e e' la domanda vera, perche' la risposta non e' ovvia:

- in autostrada il self costa **9-10 cent/litro in piu'** della rete ordinaria, e
  il servito arriva a **+24 cent**;
- su un pieno da 50 litri sono **4,5-12 €**; su un furgone da 100 litri il doppio;
- dall'altra parte ci sono i km di deviazione, il carburante speso per farli e il
  tempo perso, che su un'ora di lavoro valgono piu' del pieno.

Nessuna delle app citate mette insieme i due lati e dice "esci allo svincolo X:
risparmi 8,40 € spendendone 1,10 di deviazione". E' un conto che serve a chi guida
per mestiere — agente di commercio, artigiano, autotrasportatore — cioe' **lo stesso
pubblico del modulo flotte** del punto (B).

### Cosa costerebbe

| Pezzo | Stato |
|---|---|
| Distributori lungo una polyline | **gia' pronto**: `/ospzApi/search/route` li da' in **una sola chiamata** (misurato: 49 punti → 457 impianti, corridoio ~0,5 km) |
| Calcolo del percorso | da aggiungere: OpenRouteService, piano gratuito 2.500 richieste/giorno uso commerciale incluso, 20 €/mese per 20.000/giorno |
| Conto costi/benefici della deviazione | da scrivere: e' il pezzo che vale |

Il routing non e' un ostacolo economico: a 20 € al mese regge decine di migliaia di
ricerche. Il lavoro vero e' il terzo punto, ed e' poco codice ma va tarato bene
(consumo del mezzo, valore del tempo, capienza del serbatoio).

### Giudizio

Come **funzione gratuita dell'app consumer**: utile, ci distingue dall'app di Stato,
non porta un euro. Come **cuore del pacchetto per chi guida per lavoro**: e' il
pezzo che giustifica un abbonamento, perche' su un furgone che fa due pieni a
settimana i 9 cent al litro valgono **circa 900 € l'anno** — e un'app che ne
recupera anche solo meta' si paga da sola a 4-8 € al mese per veicolo.

## 4. Un nodo da sciogliere prima di qualunque uso commerciale

Oggi l'app chiama **l'API interna** del sito Osservaprezzi: non documentata, senza
contratto d'uso, senza SLA (vedi `analisi_fonti_dati.md`). Finche' si tratta di uso
personale e' un rischio accettabile; **per un prodotto venduto a terzi non lo e'**.

La strada pulita esiste ed e' gia' studiata: gli **open data MIMIT in licenza IODL
2.0**, che consente esplicitamente il riuso anche commerciale con attribuzione.
Costo: un backend che ingerisce i due CSV ogni notte — la cosa che oggi l'app evita
apposta. Da mettere in conto in (A) e (B).

## 5. Conclusione

| Strada | Ricavo realistico | Investimento | Giudizio |
|---|---|---|---|
| Pubblicita' consumer | ~1.000 €/anno | marketing a sei cifre per cambiare scala | **no** |
| Freemium consumer | poche centinaia € totali | — | **no** |
| Vendita dell'app | — | — | **no**: il concorrente e' lo Stato, gratis |
| Componente in prodotti esistenti | indiretto | poco: c'e' gia' tutto | **si'** |
| Modulo flotte per PMI | 7.000-36.000 €/anno | 2-3 mesi + backend open data | **forse**, solo su base clienti nostra |
| Ricerca percorso da sola | nessuno | 1-2 settimane | **no**: ce l'hanno MIMIT, Fuelio, Waze |
| "Conviene deviare?" dentro il modulo flotte | vedi sopra | +2 settimane su (B) | **si'**: e' il pezzo che nessuno fa |
| Affiliazione fuel card | marginale | poco | solo in appoggio al modulo flotte |

Detto senza giri: **l'app come la vendo a un consumatore non vale niente, perche'
lo stesso servizio glielo da' gratis il ministero**. Vale qualcosa come pezzo di un
prodotto che risolve un problema che il ministero non risolve — tenere i conti del
carburante di un parco mezzi — e vale gia' adesso come base di codice riutilizzabile.
