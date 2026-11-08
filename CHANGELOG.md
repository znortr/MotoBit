# CHANGELOG — Motobit-Ride (Android)

Formato: voci dalla più recente alla più vecchia. Backup: non obbligatorio a ogni versione, solo quando serve (lo zip va in `motobitdev\dev_backup\`).

---

## 🎨 2.1.4 — La splash ora si vede davvero (overlay in-app) e non deforma

**Data**: 2026-11-07 · **versionCode**: 2104

### Perché in 2.1.3 si vedeva solo nero
Due cause sovrapposte:
1. **Android 12+** mostra la **sua** splash sopra la finestra e l'app disegna il primo
   frame subito: il `windowBackground` con l'immagine **non viene mai mostrato**
   (restava solo il colore scuro = schermo nero).
2. Il PNG era in `drawable/` (trattato come mdpi) → **scalato di ~2,6×** a 2237×4845 px:
   bitmap enorme, inutilizzabile come sfondo di finestra.

### Soluzione
| Intervento | Dettaglio |
|---|---|
| Immagine spostata in **`drawable-nodpi/`** | nessuna scalatura automatica per densità |
| `splash_background.xml` | ora **solo colore** (l'immagine non serve più lì) |
| **`SplashOverlay()`** in `DaemonApp` | overlay Compose a schermo pieno: `Image` con **`ContentScale.Fit`** → **nessuna deformazione e nessun taglio**, sfondo `#0B0B0D` |
| Durata | 750 ms + dissolvenza in uscita di 250 ms |
| Tema `Theme.MotobitRide.Splash` | resta per evitare il lampo bianco all'avvio |

### Sequenza
splash di sistema (scura, brevissima) → **immagine di splash** → app

Funziona identico su Android 12+ e precedenti.

---

## 🎨 2.1.3 — Splash di avvio personalizzata (niente più schermata bianca)

**Data**: 2026-11-07 · **versionCode**: 2103

### Sintomo
All'avvio compariva il **bianco** del tema `Theme.Material.Light`.

### Soluzione
| File | Cosa fa |
|---|---|
| `drawable/splash_background.xml` **[nuovo]** | layer-list: colore `#0B0B0D` + `ic_splash_strtup.png` a tutto schermo |
| `values/colors.xml` | nuova `splash_background` |
| `values/themes.xml` | nuovo tema `Theme.MotobitRide.Splash` (windowBackground = splash) |
| `values-v31/themes.xml` **[nuovo]** | su Android 12+ imposta anche `windowSplashScreenBackground` scuro, così la splash **di sistema** non è più bianca |
| `AndroidManifest.xml` | l'Activity usa `Theme.MotobitRide.Splash` |
| `MainActivity` | `setTheme(R.style.Theme_MotobitRide)` **prima** di `super.onCreate()`: lo splash resta solo nella finestra di avvio, non come sfondo dell'Activity |

### Sequenza risultante
- **Android 12+**: splash di sistema (scura, con l'icona) → immagine di splash → app
- **Android < 12**: immagine di splash → app

Nessuna schermata bianca in nessun caso.

---

## 🐛 2.1.2 — Pulsante di connessione: forma, colori, nome e icone

**Data**: 2026-11-07 · **versionCode**: 2102

### 1. Lo switch non cambiava il pulsante
`autoWaiting` era usato nell'`onClick` ma **non** nel colore né nel testo: con
l'automatico attivo il pulsante restava verde "Tocca per connettere".
Ora `autoWaiting` è nel **colore** (giallo) e nel **testo** ("In attesa del TFT…
· tocca per annullare e passare a manuale").

### 2. Forma
Era una pillola (angoli completamente arrotondati). Ora:
- `shape = RoundedCornerShape(18.dp)` → **rettangolare con angoli morbidi**
- `elevation = 6.dp` (2 dp alla pressione) → **ombra**
- altezza **140 dp**, full width
- colori pieni: `#D32F2F` (nessuna moto) · `#F9A825` (sta lavorando / in attesa) · `#2E7D32` (connessa)

### 3. Nome su due righe
- Riga 1: **nome utente** (o nome HUD se vuoto) in `titleLarge` bold
- Riga 2: stato in `labelMedium`
Il campo è ora popolato con `vehicleDisplayName(profile, hudName)`, quindi usa il
nome scritto nel Garage e solo in mancanza quello dell'HUD.

### 4. Icone
| Stato | Icona a sinistra |
|---|---|
| Nessuna moto | QR |
| In connessione / disconnessa | **catena spezzata** (`ic_link_off`) |
| Connessa | ✓ |
E a destra, solo mentre lavora, uno **spinner**. Rimosso il vecchio ✓ attenuato.

---

## 🐛 2.1.1 — Pulsante grande: annullamento, reazione allo switch, dettagli

**Data**: 2026-11-07 · **versionCode**: 2101

### Fix dei 3 difetti segnalati

**1. Il tap durante la connessione non annullava.**
L'`onClick` chiamava sempre `HudSession.connect()` (no-op se già in corso).
Ora c'è una macchina a stati:

| Stato | Azione al tocco |
|---|---|
| Nessuna moto | apre la scansione QR |
| In connessione / in attesa | **annulla** (`HudSession.disconnect()`) + Toast *"Connessione annullata"* |
| Connessa | nessuna azione (D1) |
| Disconnessa | connette |

**2. Lo switch "Connessione automatica" non cambiava il pulsante.**
Il vecchio effetto di avvio scattava una volta sola (`startupApplied`). Ora un
`LaunchedEffect(autoConnect, savedHud)` reagisce **anche a runtime**: accendendo
lo switch l'app si connette subito, spegnendolo l'annullamento locale si azzera.
Nuovo stato **`autoWaiting`**: con l'automatico attivo e nessun tentativo in corso
il pulsante è giallo "In attesa del TFT…".

**3. Pulsante troppo piccolo e senza feedback.**
- **Full width**, altezza **124 dp**
- icona a sinistra (QR rossa / spinner giallo / ✓ verde / ✓ attenuata)
- **nome della moto nel testo**: «Voge 033990 · connessa»
- sotto il titolo, quando lavora: *"tocca per annullare"*
- divisore e **riga piccola in fondo con le progressioni** (le stesse della vecchia card):
  `Link ON · 192.168.13.1 · MIRRORING · 592x752 · MIRROR`

---

## 🚀 2.1.0 — Connessione separata dal mirroring + connessione automatica

**Data**: 2026-11-07 · **versionCode**: 2100

### Cosa cambia per l'utente
Ora **connettersi** e **trasmettere** sono due azioni distinte:

| Pulsante | Azione |
|---|---|
| **Pulsante grande di connessione** | connette il TFT (link + sessione), **senza** avviare il video |
| **Avvia / Ferma mirroring** | avvia o ferma solo il video; la sessione resta viva |
| **Chiudi** | chiude tutto (invariato) |

### Pulsante grande — macchina a stati
| Stato | Colore | Testo |
|---|---|---|
| Nessuna moto | 🔴 rosso + icona QR | "Registra la moto" → apre la scansione |
| Connessione in corso | 🟡 giallo + spinner | "si sta connettendo…" + "tocca per annullare" |
| Connessa | 🟢 verde + ✓ | "«nome» connessa" (tocco: nessuna azione) |
| Disconnessa (manuale) | 🟢 verde | "Tocca per connettere" |

### Impostazioni → Connessioni
Il selettore a 3 voci ("Manuale" / "Solo credenziali" / "Collega e trasmetti") è
sostituito dallo **switch "Connessione automatica"** (default **OFF**):
- **ON** → all'avvio l'app resta in attesa e si connette da sola
- **OFF** → la connessione parte dal pulsante in Home
- **Migrazione**: chi aveva uno dei due `AUTO_*` ritrova lo switch **ON**

### Interno — separazione degli stati
- Nuovo stato **`CONNECTED`**: sessione HUD pronta, video non avviato.
- `HudStreamController.connectOnly()`: stabilisce la sessione senza creare encoder/VirtualDisplay.
- `stop()` (Ferma mirroring) ora **non** spegne più la riconnessione: la sessione e il link
  restano vivi e si autoripristinano se cadono. Prima `Stop` disattivava il retry.
- `HudSession` espone `connect()`, `startStream()`, `stopStream()`, `disconnect()`;
  nuovo flag `wantsConnection` distinto da `wantsStream`.

### ⚠️ Da verificare (rischio noto)
L'HU potrebbe chiudere una sessione su cui non arrivano frame (`MediaCtrlInit` +
`VideoStart` senza video). Test: **connetti senza avviare il mirroring** e osserva
2-3 minuti. Se l'HU chiude, si aggiungerà un frame di cortesia o l'avvio automatico
del video dopo N secondi.

---

## 🎨 2.0.6 — Cattura sempre a schermo intero

**Data**: 2026-11-07 · **versionCode**: 2006

### Sintomo
All'avvio della cattura Android 14+ mostrava il dialog con la scelta **"un'app"**
vs **"schermo intero"**. Scegliendo "un'app" il TFT mostrerebbe solo quella, e
sarebbe impossibile cambiare app con il mirroring attivo.

### Fix
Su Android 14+ (`API 34`) si crea l'intent con
`MediaProjectionConfig.createConfigForDefaultDisplay()`:
il dialog di sistema **non offre più l'opzione app singola**, la cattura è sempre
sullo schermo intero. Su versioni precedenti resta l'intent classico.

```kotlin
val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
} else {
    manager.createScreenCaptureIntent()
}
```

### Invariato
Tasto "Avvia"/"Stop"/"Chiudi" **non toccati** in questa versione: la separazione
connetti/mirroring e il pulsante grande di connessione arriveranno in commit dedicati.

---

## 🐛 2.0.5 — "Dimentica" cancella anche il MAC in memoria

**Data**: 2026-11-07 · **versionCode**: 2005

### Cosa era già corretto
Il **MAC vive dentro `SavedHudData`**, lo stesso JSON di SSID/nome/modello/S-N.
`SavedHud.clear()` rimuove l'intera chiave `profile`/`hud`: quindi il MAC viene
**già cancellato** da `SharedPreferences` e non sopravvive al riavvio.
Il MAC è quindi **legato alla moto salvata** (l'SSID ne è la chiave: due moto
diverse non se lo scambiano, e il merge del 2.0.4 si applica solo a parità di SSID).

### Il pezzo mancante (corretto ora)
`WifiApController.savedQr` è **in memoria** dentro `HudSession` (vita di processo)
e contiene anche il MAC. Dopo "Dimentica", il link poteva **ancora riconnettersi**
alla moto dimenticata usando quel dato, che invece non esisteva più su disco.

**Fix** — `onConfirm` di "Dimentica" ora fa anche:
```kotlin
HudSession.closeAll()                 // stream chiuso + savedQr in memoria azzerato
ScreenCaptureService.stopService(ctx) // se stava trasmettendo, chiude il servizio
```

### Risultato
Dopo "Dimentica": nessun dato su disco, nessun dato in memoria, nessun tentativo
di riconnessione alla moto dimenticata, notifiche di scadenza annullate.

---

## 🐛 2.0.4 — Il MAC del TFT ora è davvero persistente

**Data**: 2026-11-07 · **versionCode**: 2004

### Sintomo
In **Garage → Info tecniche** il campo **MAC** mostrava `—` (o spariva dopo
qualche passaggio). Il MAC appreso dalla discovery Wi-Fi Direct va poi perso,
costringendo a rifare la discovery (2-3 s) a ogni connessione.

### Causa
`SavedHud.save(qr)` fa `data = SavedHudData.fromQr(qr)`, cioè `mac = qr.mac`. Se il
QR **non contiene** il MAC (succede), la scrittura azzerava il valore appreso in
precedenza da `updateMac()`.

E `save()` viene richiamata **molto spesso** con lo stesso QR:
- `LaunchedEffect(hudQrValue)` in `HudScreen` — a ogni ritorno in Home;
- nel ramo "Avvia" di `HudScreen`;
- `LaunchedEffect(hudQrValue)` in `GarageScreen`.

Quindi: connetti → `updateMac()` salva il MAC → torni in Home → `save()` lo
riscrive `null`. Il MAC non sopravviveva mai.

### Fix
In `save()`: se il QR in arrivo **non** ha il MAC e l'SSID è lo stesso, si
**conserva** quello già memorizzato:

```kotlin
if (data.mac.isNullOrBlank() && previous != null && previous.ssid == data.ssid) {
    data = data.copy(mac = previous.mac)
    AppLog.i(TAG, "MAC preservato dal salvataggio precedente: ${data.mac}")
}
```

La prima volta il MAC viene imparato dalla discovery (`updateMac`), poi resta
stabile in `SharedPreferences` e visibile in **Info tecniche**.

### Invariato
Connessione, trasmissione, stabilità, encoder, libreria Go.

---

## 🐛 2.0.3 — Notifica persistente su "Chiudi" e "Dimentica" corretto

**Data**: 2026-11-07 · **versionCode**: 2003

Solo UI/servizio. Connessione/trasmissione/stabilità invariate (2.0.0).

### Bug 1 — la notifica restava dopo "Chiudi"
**Sequenza che la ricreava**:
```
Chiudi → HudSession.closeAll() → updateStatus("Fermo") → notify()
       → ScreenCaptureService.stopService() → notifica rimossa
       → finishAndRemoveTask() → onTaskRemoved()
       → HudSession.closeAll() → updateStatus("Fermo") → notify()  ← RICREATA
```
`updateStatus()` non sapeva che il servizio era già morto e ripostava la
notifica, che restava per sempre.

**Fix**:
- flag `running` in `ScreenCaptureService`: `updateStatus()` esce subito se il
  servizio non è in primo piano;
- `onDestroy()` e `stopService()` annullano esplicitamente la notifica;
- in `HudScreen` l'ordine è cambiato: **prima** `stopService`, **poi** `closeAll()`.

### Bug 2 — "Dimentica" nel Garage
| | Prima | Ora |
|---|---|---|
| Conferma | ❌ cancellava al primo tocco | ✅ **dialog "Dimenticare il veicolo?"** con Annulla/Dimentica |
| Cosa cancella | solo i dati di connessione (`SavedHud`) | ✅ **tutto**: connessione + nome + targa + scadenze (`VehicleStore.clear()`) + notifiche di scadenza annullate |

---

## 🎨 2.0.2 — Scansione QR: intestazione come le altre pagine

**Data**: 2026-11-07 · **versionCode**: 2002

Solo UI. Connessione/trasmissione/stabilità invariate (congelate in 2.0.0).

- L'intestazione della pagina QR è ora una `TopAppBar` **identica alle altre pagine**:
  logo **MotoBit a sinistra** con la versione, su **sfondo pieno** (non più
  trasparente sopra la fotocamera).
- Al posto delle tre icone di navigazione: pulsante **X + "Chiudi"**.
- Il tocco su **Chiudi riporta alla Home** (`popBackStack` fino a `hud`, non più
  un semplice "indietro" che tornava al Garage).
- Rimossa la sfumatura in alto, non più necessaria con la barra opaca.
- Invariati: fotocamera a schermo pieno, cornice di puntamento, riquadro
  semitrasparente con le istruzioni tradotte.

---

## 🎨 2.0.1 — Garage e scansione QR ridisegnati

**Data**: 2026-11-07 · **versionCode**: 2001

> Solo UI: **nessuna modifica** a connessione, trasmissione, stabilità, encoder,
> libreria Go (restano quelle congelate nella 2.0.0).

### Garage — una sola card, pulita
Prima c'erano tre card con dati ripetuti e il nome scritto nel codice. Ora:

| Elemento | Comportamento |
|---|---|
| **Nome moto** (H1, bold) + **Targa** (H2) | in evidenza in cima |
| **Icona matita** | apre il dialog **"Nome e targa"** (due campi + Salva/Annulla) — il nome non si modifica più digitando in pagina |
| **Pulsante "Scadenze"** | stile card delle Impostazioni · sottotitolo = scadenza più vicina · apre un dialog con revisione/tassa/assicurazione + switch **"Avvisi di scadenza"** + giorni di preavviso. **Una sola icona ⓘ in alto a destra** che mostra la spiegazione inline |
| **Pulsante "Info tecniche"** | apre un dialog con HUD, SSID, MAC, modello, S/N, risoluzione, protocollo |
| **In fondo alla card** | **Dimentica** se c'è un HUD memorizzato, altrimenti **Scansiona QR** |

I tre box precedenti sono stati accorpati: niente più dati duplicati.

### Scansione QR — schermo pieno
- **Barra in alto**: solo il logo **MotoBit centrato** e la **X** per chiudere (niente icone di navigazione, niente titolo inglese)
- **Cornice bianca** al centro che indica dove inquadrare il QR
- **Riquadro semitrasparente subito sotto la cornice** con il titolo *"Scansiona QR"* e l'istruzione tradotta:
  *"Inquadra il QR sul TFT della moto che vuoi collegare."*
- Sfumatura in alto per la leggibilità del logo sulla fotocamera
- Estratto `MotobitLogo()` come composable riutilizzabile (usato anche dalla barra principale)

### Nuove stringhe IT/EN
`garage_edit_title`, `garage_deadlines_button`, `garage_deadlines_none`,
`garage_info_button`, `garage_info_sub`, `garage_info_hud`, `garage_info_model`,
`garage_info_resolution`, `garage_info_protocol`, `common_save`, `common_cancel`,
`qr_scan_permission`; aggiornate `qr_scan_title` e `qr_scan_instruction`.

---

## 🏁 2.0.0 — VERSIONE CANDIDATA DEFINITIVA (connessione / trasmissione / stabilità)

**Data**: 2026-11-07 · **versionCode**: 2000 · **Backup**: `dev_backup\Motobit_alfa_V2.0.0.zip`

> ### 📌 Da questa versione il lato **connessione, trasmissione e stabilità è CONGELATO**
>
> Test di stabilità **superato**: 1 ora di streaming continuo, zero riconnessioni,
> zero errori, `retrans=0` su tutte le socket. Le versioni successive riguarderanno
> **solo prodotto/UI** (Garage, scadenze, schermo acceso, oscuramento, permessi),
> salvo richiesta esplicita dell'utente di toccare la connessione.

### Cosa contiene rispetto alla 1.1.5 (base di partenza)

| Area | Intervento |
|---|---|
| **Riconnessione in background** | `ScreenCaptureService` con `foregroundServiceType="mediaProjection\|location"`: senza il tipo `location` Android 10+ rifiuta `discoverPeers`/`connect` in background |
| **Sessione a vita di processo** | `HudSession` possiede link + streaming: la UI non li distrugge più in `onDispose` |
| **Recupero P2P passivo** | niente più `removeGroup`/`initialize` a ogni retry: canale riusato, `connect` solo al ritorno del peer |
| **Riconnessione immediata** | `onLinkRestored()`: azzera il backoff al ritorno del link (era fino a 10 s di attesa) |
| **Guardia anti-doppio-handshake** | flag `connecting` auto-riparante (90 s): elimina le due sessioni concorrenti che rendevano l'immagine intermittente |
| **Cadenza encoder** | `paceLoop` senza recupero: eliminati i picchi 43-51 fps |
| **Notifica viva** | stato reale + azioni `Stop` / `Riconnetti`; si chiude con l'app (`onTaskRemoved`) |
| **Attesa attiva → passiva** | dopo N minuti senza TFT si smette di tentare ma il receiver resta in ascolto |
| **Revoca MediaProjection** | messaggio esplicito in Home e notifica |

### Test superati
- 1 ora di streaming continuo: **zero** riconnessioni, **zero** errori, `retrans=0`
- Spegnimento/riaccensione TFT: riconnessione automatica anche con app in background
- Swipe dal registro app: sessione chiusa e notifica rimossa
- `Stop` / `Riconnetti` dalla notifica
- Doppio tocco su "Avvia" dopo un "Chiudi": risolto (era un flag di processo)

### Cosa è stato verificato **non** essere una regressione
`HudEncoder.kt` e la pipeline di cattura/encoding sono **identici alla 1.1.5**
(confronto diretto con il backup `Motobit_alfa_V1.1.5.zip`).

### Limite noto e accettato
Consegna frame ~**14-16 fps** su ~26-29 richieste del TFT (46% di risposte `idle`).
Il TFT tiene l'ultimo frame → fermo immagine di 40-70 ms: **impercettibile con le
mappe** (uso reale), visibile solo con video. Due strade annotate nel `TODO.md` §10.

### Funzioni di prodotto incluse
Garage a card (nome editabile, targa, revisione/tassa/assicurazione con mese+giorno,
conto alla rovescia), promemoria con notifiche `WorkManager` (Posponi/Elimina),
Impostazioni in 5 categorie con sottopagine, icona adattiva unica.

---

## 🚀 1.3.6 — La notifica si chiude quando si chiude l'app

**Data**: 2026-11-07 · **versionCode**: 1306

### Sintomo
Chiudendo l'app (swipe dal registro app) la notifica di mirroring restava
**per sempre**, con i pulsanti `Stop` / `Riconnetti`, mostrando uno stato che non
corrispondeva più a nulla: **falsa informazione**.

### Causa
`ScreenCaptureService` è un foreground service con `START_NOT_STICKY`: senza
`onTaskRemoved()` continuava a vivere dopo la rimozione del task, e la sua
notifica con lui. Il pulsante "Chiudi" già fermava tutto (`stopService`), ma lo
swipe no.

### Fix
Override di `onTaskRemoved()` in `ScreenCaptureService`: alla rimozione del task
si chiude la sessione (`HudSession.closeAll()`), si fa `stopSelf()` e quindi
`onDestroy()` ferma la `MediaProjection`. La notifica sparisce.

### Cosa resta volutamente invariato
- **Home / passaggio in background**: `onTaskRemoved` **non** viene chiamato,
  quindi la sessione resta viva — è la funzione che permette di usare il
  navigatore con il mirroring attivo.
- **Stop**: resta una pausa (notifica "In pausa", link vivo).
- **Chiudi**: chiude tutto (già funzionante).

---

## 🚀 1.3.5 — Fix del doppio tocco su "Avvia" dopo un "Chiudi"

**Data**: 2026-11-07 · **versionCode**: 1305

### Verifica del Fix 1 (1.3.4)
Dal log di 25 minuti: `flusso encoder` non presenta più picchi 43-51 fps.
Massimo osservato **30 fps**, serie piatta. ✅

### Bug trovato nel log (doppio tocco)
```
16:16:44.412  Chiudi premuto            → close(): stopRequested = true
16:17:26.291  Avvia premuto             → link P2P parte
16:17:29.808  LINK P2P ATTIVO           → setPreferredHost
              ...niente streaming per 16 secondi...
16:17:45.437  Avvia premuto (2° tocco)  → parte subito
```

**Causa**: `close()` imposta `stopRequested = true`. Quel flag vive in
`HudSession`, cioè a **livello di processo**: sopravvive alla chiusura e riapertura
dell'Activity. `onLinkRestored()` usciva subito con `if (stopRequested) return`,
quindi la prima pressione di "Avvia" ricostruiva **solo il link**, senza avviare
lo streaming. Il secondo tocco cadeva nel ramo "link già attivo" → `start()` →
che azzera il flag.

**Fix**: `onLinkRestored(autoResume = true)` azzera `stopRequested`/
`streamPaused` prima del controllo: la richiesta esplicita dell'utente vale come
un nuovo "Avvia".

### Analisi consegna frame (per il futuro Fix 2)
Dal log: la TFT richiede frame a **25,7 Hz** (`polls=1566` in 61 s) ma ne riceve
**14,0 fps** (`frames=852`): il **46%** delle richieste riceve un `idle` di 4 byte.
Il gate `now - lastAdvance >= interval` (33 ms) rifiuta l'avanzamento sulle
richieste ravvicinate, e quello slot è perso per sempre.
→ Fix 2 proposto: avanzare quando la **coda ha un frame** (non in base al tempo),
lasciando che sia il polling della TFT a dettare il ritmo.

### Invariato
Link, riconnessione, sessione, risoluzione, bitrate, cattura: non toccati.

---

## 🚀 1.3.4 — Fix 1: cadenza encoder senza "recupero"

**Data**: 2026-11-07 · **versionCode**: 1304

### Sintomo (dal log di 1h)
`flusso encoder` oscillava tra **12 e 51 fps** (target 30). Il TFT alternava
raffiche e stalli → video non fluido. Rete esclusa: `retrans=0` su tutte le socket.

### Causa
In `HudEncoder.paceLoop`, `nextNs += intervalNs` non gestiva il ritardo: se il
thread perdeva un giro (GC, contesa CPU) `nextNs` restava nel passato e le
iterazioni successive accodavano **più frame di fila** per recuperare → raffica
da 43-51 fps, seguita dallo stallo successivo.

### Fix
```kotlin
val lateNs = now - nextNs
nextNs = if (lateNs > intervalNs) now + intervalNs else nextNs + intervalNs
```
Se in ritardo di più di un intervallo si **riallinea** e salta il frame perso,
invece di recuperarlo a raffica. Il contenuto non cambia: il pace loop rimanda
comunque l'ultimo `currentFrame`, quindi saltare un duplicato è invisibile.

### Invariato
- `HudEncoder` era **identico alla 1.1.5** prima di questa modifica: nessuna
  regressione pregressa, solo un miglioramento del pacing.
- Non toccati: link, riconnessione, sessione Go, risoluzione, bitrate, keyint,
  cattura schermo, libreria Go.

---

## 🚀 1.3.3 — Revert delle due regressioni introdotte da me

**Data**: 2026-11-06 · **versionCode**: 1303

Due regressioni introdotte nelle versioni precedenti, entrambe **non presenti** nel
codice originale. Riportate al comportamento di partenza.

### Regressione 1 — "Avvia" non rispondeva per 2-3 secondi
**Causa**: in 1.1.6 avevo reso **asincrono** `teardownP2p()`: prima di iniziare la
connessione aspettava il callback di `requestGroupInfo`/`removeGroup`, con un
timeout di sicurezza di **2 secondi**. Il `connect` partiva solo dopo quell'attesa,
percepita come "il tasto non fa nulla".

**Fix**: `teardownP2p()` è tornato **sincrono** (fire-and-forget): `removeGroup`
non viene atteso, il `connect` parte immediatamente. Il rischio di `BUSY` è
gestito dal retry, non dall'attesa.

### Regressione 2 — Video a scatti
**Causa**: in 1.1.7 avevo introdotto `FrameSender`, una coda di **10 frame** che
**scarta i frame quando è piena**. Prima la spinta era sincrona: `PushFrame`
lato Go scrive in un ring buffer **in memoria** (non sulla socket), quindi non
blocca. La coda introduceva frame mancanti → microscatti.

**Fix**: rimosso `FrameSender`. La spinta è di nuovo **sincrona** dal thread di
drain, identica all'originale.

### Miglioramento (non regressione)
- `WifiApController.lastEvent` è ora `mutableStateOf`: la UI si aggiorna in tempo
  reale sullo stato del link (prima non si ricomponeva e sembrava "fermo").
- Se un tentativo resta appeso oltre **5 s**, il successivo tocco su "Avvia" ne
  avvia uno nuovo invece di essere ignorato.

### Invariato
Recupero P2P passivo, `retryPassive`, `passiveWait`, `onLinkRestored`,
`probeInFlight`, guardia `connecting`, FGS `location`, notifiche: **non toccati**.

---

## 🚀 1.3.2 — Guardia `connecting` auto-riparante

**Data**: 2026-11-06 · **versionCode**: 1302

La guardia anti-doppio-handshake introdotta in 1.3.1 poteva, in teoria, bloccare
per sempre le riconnessioni se un handshake si fosse appeso. Ora si auto-ripara:

- `connectingSince` registra l'inizio dell'handshake;
- `isConnecting()` restituisce `false` se sono passati più di **90 s**
  (l'handshake Go ha `startupTimeout = 10 s`, quindi 90 s è un margine ampio);
- `beginDiscovery()` e `onLinkRestored()` usano `isConnecting()`, così dopo la
  soglia riprendono a funzionare da sole;
- log esplicito: *"Handshake precedente bloccato da oltre 90s: lo considero caduto"*.

Nessun'altra modifica al percorso di streaming.

---

## 🚀 1.3.1 — Fix trasmissione intermittente (doppia sessione)

**Data**: 2026-11-06 · **versionCode**: 1301

### Sintomo
Sul TFT l'immagine alternava tra due contenuti (es. YouTube ↔ home), in loop.

### Causa
Dalla 1.2.4 `HudSession.onHudConnected` chiama `onLinkRestored()`, che avvia
`beginDiscovery()`. Se la sollecitazione arrivava **mentre un handshake era già
in corso** (`connectToHost` è bloccante e `isRunning` è ancora `false`), partiva un
**secondo `connectToHost`**: due `MobileSession` concorrenti sullo stesso HU, di
cui una orfana. Il TFT riceveva i frame di entrambe → immagine intermittente.

### Fix
- Nuovo flag `connecting`: `connectToHost` è protetto, e `beginDiscovery()` /
  `onLinkRestored()` non avviano nulla se un handshake è già in corso.
  `connectToHost` è stato diviso in wrapper + `connectToHostInternal` con
  `try/finally` per garantire il reset del flag.
- Prima di assegnare la nuova sessione, se ne esiste ancora una precedente viene
  chiusa esplicitamente (niente sessioni orfane).
- `forceFreshFrame()` non fa più `resize` del VirtualDisplay a ogni riconnessione:
  il `resize` (invasivo) avviene **solo** uscendo da una pausa vera dell'encoder;
  sulle riconnessioni normali si chiede solo un IDR al codec.

---

## 🚀 1.3.0 — Garage a card + scadenze con notifica

**Data**: 2026-11-06 · **versionCode**: 1300

### Garage ridisegnato (card)
- **Nome moto** editabile dall'utente. Se vuoto → nome dell'HUD (`voge 033990`).
  Rimosso il nome hardcoded `🏍️ Voge 800 Rally`.
- **Targa** editabile (maiuscole automatiche).
- **Tre scadenze** con mese (1–12) + giorno (0–31) e conto alla rovescia:
  revisione tecnica, tassa di circolazione, **assicurazione** (nuova).
  - `giorno = 0` → *entro il mese* (vale l'ultimo giorno del mese)
  - `giorno = 1–31` → data precisa
  - icona **ⓘ** accanto a ogni scadenza → dialog con la spiegazione
- I tre box sono unificati in una card **Informazioni** con pulsante
  **Dimentica** (se c'è un HUD) oppure **Scansiona QR** (se non c'è nulla).
- Card **Avvisi di scadenza**: switch **Inviami notifiche** (default ON) e
  slider **Avvisami N giorni prima** (default 7, 1–60).

### Notifiche di scadenza (opzione B: nessun Google Calendar)
- Controllo **giornaliero** con `WorkManager` (sopravvive al riavvio, nessun
  permesso `SCHEDULE_EXACT_ALARM`), primo giro alle ~09:00 locali.
- Notifica per singola scadenza:
  - titolo `Revisione tecnica — tra 5 giorni` (varianti: *scade oggi* / *scade domani*)
  - testo `Voge 033990 · scadenza 15 marzo 2027`
  - azioni **Posponi** (snooze 1 giorno) e **Elimina** (sopprime per questa
    occorrenza, si riarma l'anno successivo)
- Canale dedicato **"Scadenze veicolo"** con importanza DEFAULT (suona/vibra),
  separato dal canale silenzioso del mirroring.
- Nel Garage, se un avviso è stato eliminato compare **Riattiva avviso**.

### Nuovi file
- `core/garage/VehicleProfile.kt` — dati veicolo + store JSON in SharedPreferences
- `core/garage/DeadlineReminder.kt` — prossima occorrenza e giorni mancanti
- `core/garage/DeadlineNotifications.kt` — canale e builder notifica
- `core/garage/DeadlineWorker.kt` — controllo giornaliero + pianificazione
- `core/garage/DeadlineActionReceiver.kt` — azioni Posponi/Elimina

### Dipendenze
- Aggiunta `androidx.work:work-runtime-ktx:2.9.1` (~1 MB).

### Invariato
Streaming, link P2P, sessione Go, permessi, impostazioni: nessuna modifica.

---

## 🚀 1.2.8 — Sottotitoli spostati sotto la barra

**Data**: 2026-11-06 · **versionCode**: 1208

- Nella barra sticky delle sottopagine resta solo il **titolo** (a destra).
- Il **sottotitolo** è ora la **prima riga del contenuto**, subito sotto la barra,
  a larghezza piena: non viene più troncato.

---

## 🚀 1.2.7 — Barra sticky delle sottopagine come la barra MotoBit

**Data**: 2026-11-06 · **versionCode**: 1207

Le sottopagine delle Impostazioni ora usano una `TopAppBar` **sticky** con lo stesso
stile della barra MotoBit:
- **sinistra**: `< Indietro` tappabile (al posto del logo);
- **destra**: titolo della pagina + sottotitolo (al posto delle 3 icone).

Il contenuto scorre sotto la barra. Rimossa la precedente intestazione interna
(barra Indietro + titolo grande nel corpo della pagina).

---

## 🚀 1.2.6 — Impostazioni riorganizzate in 5 categorie

**Data**: 2026-11-06 · **versionCode**: 1206

### Struttura
Il menu Impostazioni ora è un elenco di **5 card** (icona colorata, titolo,
 sottotitolo e chevron `>`). Ogni card apre una **sottopagina con slide-in da destra**,
con barra `< Indietro` che **sostituisce** la barra MotoBit (più spazio e più pulito).

| Categoria | Contenuto |
|---|---|
| **Generale** | Lingua e Tema |
| **Connessioni** | 1) comportamento all'avvio 2) vibrazione su connessione 3) attesa attiva della riconnessione |
| **Risoluzione** | Solo titolo e sottotitolo (pronta per le impostazioni future) |
| **Permessi** | Permessi e affidabilità (invariato) |
| **Opzioni Sviluppo** | 1) sincronizzazione orologio TFT 2) log e diagnostica (inline) |

### Dettagli
- **Generale**: tolti i titoli di sezione; il nome sta nella label del select box e la
  descrizione è **sotto** il box. Rimossi "(Italiano/English)" e "(Default: Scuro)".
- **Opzioni Sviluppo** sostituisce la vecchia pagina **Diagnostica** (`DiagnosticsScreen`
  eliminata insieme alla sua rotta).
- **HUD Memorizzato** spostato dalla pagina Impostazioni alla pagina **Garage**
  (due card separate: Veicolo e HUD — ridisegno previsto in seguito).
- Nuova icona vettoriale `ic_cat_resolution.xml` per la categoria Risoluzione.

### Invariato
Nessuna funzionalità toccata: switch, slider, permessi, log e sincronizzazione orologio
funzionano come prima.

---

## 🚀 1.2.5 — Icone: una sola immagine da sostituire

**Data**: 2026-11-06 · **versionCode**: 1205

### Pulizia risorse
- Rimossi i **10 PNG legacy** delle densità (`mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi`,
  `ic_launcher.png` + `ic_launcher_round.png`). Servivano solo ad Android 7 e precedenti:
  con `minSdk = 29` viene sempre usata l'**icona adattiva** (`mipmap-anydpi`).
- L'icona dell'app ora dipende da **un solo file**: `res/drawable/ic_launcher_foreground.png`
  (432×432). Sostituirlo mantenendo il nome aggiorna l'icona ovunque, senza toccare il codice.
- Aggiunta la cartella **`design/`** (fuori dall'APK) con l'archivio delle immagini,
  le dimensioni nel nome e un `README.md` con la mappa file → cartella `res/`.

### Invariato
Nessuna modifica funzionale: solo risorse e documentazione.

---

## 🚀 1.2.4 — Riconnessione immediata al ritorno del link

**Data**: 2026-11-06 · **versionCode**: 1204

### Causa dei 20–30 s
Alla riaccensione del TFT il link tornava, ma `HudSession` chiamava `start()` solo se lo
stato era `IDLE`/`ERROR`. Durante un'assenza lo stato è **`WAITING_HUD`** → nessuna
sollecitazione: si aspettava il timer di `scheduleReconnect`, ormai al **backoff massimo
di 10 s**, più il probe (fino a 8 s) più l'handshake Go (5–10 s).

### Fix
- Nuovo `HudStreamController.onLinkRestored(autoResume)`: azzera il backoff, annulla il
  timer pendente e fa partire **subito** `beginDiscovery()`.
- `HudSession.onHudConnected` lo chiama sempre (non più solo con stato `IDLE`/`ERROR`).
- Guardia `probeInFlight`: evita due thread di probe in parallelo quando il link torna
  mentre un ciclo è già in corso.

---

## 🚀 1.2.3 — Attesa attiva → ascolto passivo (impostazione unica)

**Data**: 2026-11-06 · **versionCode**: 1203

- Sostituito lo slider *"Sospendi encoder dopo: N s"* con **"Attesa attiva: N min"**
  (default **5**, intervallo 1–30).
- Per i primi N minuti: encoder in pausa dopo 5 s (interno, non configurabile) e retry P2P
  attivo. Oltre la soglia: **nessun tentativo**, solo il receiver `PEERS_CHANGED` in ascolto
  → la riconnessione resta automatica alla riaccensione del TFT.
- `HudStreamController.setPassiveWait()` e `WifiApController.setPassiveWait()`: fermano solo
  il timer, non il receiver.
- Notifica: *"In attesa passiva del TFT"*.

---

## 🚀 1.2.2 — Cattura schermo revocata: avviso esplicito

**Data**: 2026-11-06 · **versionCode**: 1202

- Se il sistema revoca la `MediaProjection` (tipico: revoca mentre l'app è in background),
  lo stato diventa `AWAITING_PROJECTION` con messaggio **"Cattura schermo revocata: riapri
  MotoBit per riprendere"**, visibile in Home **e nella notifica**.
- Su Android 14+ il consenso può essere richiesto solo con l'app visibile: senza questo
  avviso il TFT restava nero senza spiegazione.

---

## 🚀 1.2.1 — Feedback utente sulla connessione

**Data**: 2026-11-06 · **versionCode**: 1201

- **Notifica con stato live**: non più il testo fisso *"Invio dello schermo all'HUD..."*,
  ma lo stato reale (`Mirroring 592x752`, `In attesa dell'HUD (tentativo N)`, `In pausa`,
  `In attesa passiva del TFT`, `Cattura schermo revocata...`).
- **Azioni nella notifica**: `Stop` e `Riconnetti`, utilizzabili senza aprire l'app
  (`NotificationActionReceiver`, receiver dichiarato nel manifest).
- **Vibrazione** breve (60 ms) su connessione/perdita del link: nuovo interruttore in
  Impostazioni, default **OFF**. Permesso `VIBRATE`.
- **Messaggi di causa comprensibili** in Home al posto dell'evento tecnico:
  *Wi-Fi Direct disattivato*, *L'HUD potrebbe essere connesso a un altro telefono* (BUSY),
  *TFT non trovato*.

---

## 🚀 1.2.0 — Recupero P2P passivo (fine del martellamento su Wi-Fi Direct)

**Data**: 2026-11-06 · **versionCode**: 1200

### Bug corretto
A ogni tentativo di riconnessione (ogni 3–10 s, per tutta la durata dell'assenza
del TFT) il codice eseguiva:

```
teardownP2p()  →  requestGroupInfo + removeGroup
               →  manager.initialize()   (canale nuovo)
               →  manager.connect()
```

Con il peer assente il gruppo **non esiste più**: `removeGroup()` restituiva `BUSY`
lasciando la macchina a stati del servizio Wi-Fi Direct occupata, e il `connect()`
seguente partiva su uno stato sporco e falliva. In pratica: smontaggio e rimontaggio
dell'interfaccia Wi-Fi Direct ogni pochi secondi, per minuti.

### Fix
- Nuovo `retryPassive()`: riusa il canale esistente e tenta **solo** la connessione.
  Nessun `removeGroup`, nessun `initialize` durante l'assenza del peer.
- `initialize()` avviene **una sola volta** per sessione; viene rifatta solo se il
  canale è davvero morto (`onChannelDisconnected` → azzera canale e manager).
- **Fast path**: su `WIFI_P2P_PEERS_CHANGED_ACTION`, appena il peer riappare si
  tenta subito un `connect` senza aspettare il timer → riconnessione più rapida.
- `attemptInFlight` viene azzerato sui fallimenti: prima restava `true` e la
  guardia di `retryPassive` avrebbe bloccato ogni tentativo successivo.
- `removeGroup()` resta solo dove serve davvero: `disconnect()` (Chiudi) e primo
  tentativo con credenziali nuove.

### Invariato
Sessione Go, cattura, encoder, sospensione encoder, UI: non toccati.

---

## 🚀 1.1.10 — Revert del rilascio cattura (1.1.9) + cattura esclusa come causa

**Data**: 2026-11-06 · **versionCode**: 1110

### Cosa è stato revertato
Il rilascio immediato della cattura introdotto in 1.1.9 è stato **rimosso**:
- alla disconnessione l'app ricreava encoder e VirtualDisplay, ma alla riconnessione
  **si connetteva senza trasmettere** (regressione);
- il freeze del video sul telefono **non è migliorato**.

Ripristinato il comportamento di 1.1.8: la cattura resta viva durante l'assenza
dell'HU, così la ripresa è immediata e senza nuovo consenso.

### Cosa abbiamo imparato (importante)
Rilasciare la cattura **nell'istante esatto** della caduta **non** evita il freeze.
Quindi la pipeline di cattura (`VirtualDisplay` + `ImageReader` + encoder) è
**esclusa** come causa del video congelato sul telefono.

### Sospetti rimasti
Resta un solo comportamento che correla al 100% con il freeze: il **loop di retry
P2P** che a ogni tentativo (ogni 3–10 s) esegue `removeGroup` + `initialize` +
`connect`, martellando lo stack Wi-Fi Direct mentre il peer è assente.
→ prossimo intervento: **recupero P2P passivo** (nessun teardown, `connect` solo
quando il peer riappare).

---

## 🚀 1.1.9 — Rilascio immediato della cattura alla caduta della connessione

**Data**: 2026-11-06 · **versionCode**: 1109

### Ipotesi in esame
Il video delle altre app (YouTube/navigatore) si congela **sul telefono** quando il
TFT si spegne, e non si riprende (serve pause/play). Esclusi con i test: rete
(modalità aereo ok), buffering (nessuno spinner), app che "tiene" la pipeline
(uccidere l'app non sblocca). Resta la combinazione **cattura attiva + teardown
del gruppo Wi-Fi Direct**.

### Modifica
- Nuovo `releaseCaptureOnLoss()`: alla **prima** caduta della connessione rilascia
  subito `VirtualDisplay` + `ImageReader` + thread di cattura + `MediaCodec`.
- La `MediaProjection` **non** viene rilasciata: resta valida, quindi alla
  riconnessione `attachMirroring()` ricrea tutto **senza nuovo consenso** alla
  cattura schermo.
- Doppio innesco, conta il primo dei due: `scheduleReconnect()` (sessione Go persa)
  e `onLinkLost()` (gruppo P2P perso, chiamato da `HudSession`).
- Guardia `captureReleased`: il rilascio avviene **una sola volta per caduta**, non
  a ogni tentativo di riconnessione. Si riarma quando la cattura viene ricreata.
- `attachMirroring()` e il rilascio sono serializzati da `captureLock` (arrivano da
  thread diversi: callback Go, main thread, watchdog).

### Invariato
Il meccanismo di riconnessione (link P2P + sessione Go) **non è stato toccato**:
questo è un livello aggiuntivo, non un sostituto.

---

## 🚀 1.1.8 — Fix del video congelato sul TELEFONO durante l'attesa

**Data**: 2026-11-06 · **versionCode**: 1108

### Sintomo
Con il TFT spento il video **sul telefono** (YouTube/navigatore) si congelava e
ripartiva solo con pause/play. Il TFT era innocente: mostrava fedelmente ciò che il
telefono disegnava.

### Causa (misurata con test A/B/C/D/E)
Il freeze si presentava **solo con "Sospendi encoder dopo: 0 (mai)"**. Con un
timeout attivo non si presentava. Quindi il colpevole era l'encoder che continuava
a girare durante l'assenza del TFT:
- `KEY_I_FRAME_INTERVAL = 0` → **ogni frame è un IDR (all-I)**, la configurazione
  più costosa possibile, 30 volte al secondo;
- con il TFT assente quei frame non venivano consumati da nessuno, ma il costo CPU
  restava identico;
- il budget CPU/GPU residuo per il decoder video delle altre app crollava → layer
  video congelato, mentre la UI (composita dalla GPU) restava reattiva.

### Fix
- La grazia prima della pausa dell'encoder passa da **minuti a secondi**:
  default **5 s** (era 5 min), intervallo 0-60 s.
- **`0` non significa più "mai" ma "pausa immediata"**: l'opzione "mai" è stata
  rimossa perché era esattamente la configurazione che riproduceva il freeze.
- Nuova chiave di preferenza `encoder_idle_timeout_sec`: il vecchio valore in
  minuti viene ignorato di proposito (letto come secondi sarebbe 60× sbagliato).
- Testo della sezione Impostazioni aggiornato con la spiegazione del perché
  tenere l'encoder attivo a vuoto è dannoso.
- La ripresa resta istantanea: `pause()` non ferma il codec, e alla riconnessione
  `resumeEncoderIfPaused()` + `forceFreshFrame()` riattivano e forzano un frame.

---

## 🚀 1.1.7 — Fix definitivo del video "frizzato" dopo la riconnessione

**Data**: 2026-11-06 · **versionCode**: 1107

### Causa
`MobileSession.pushFrame()` è una chiamata **bloccante** e veniva invocata
**sincronamente dal thread di drain dell'encoder**. Quando il TFT sparisce (socket
ancora aperta ma nessuno che legge) la write TCP resta appesa per minuti: il drain
non rilascia più i buffer di output del codec, l'encoder si ferma e la pipeline
**non riparte nemmeno dopo la riconnessione** (il thread resta appeso sulla sessione
morta). Da qui il video congelato fino al riavvio dell'app.

### Fix
- Nuova `FrameSender`: thread dedicato + coda limitata (10 frame ≈ 0,3 s).
  Il drain dell'encoder non si blocca **mai**: se il peer non legge, i frame
  vengono scartati (con log ogni 300) e il codec continua a girare.
- A ogni nuova sessione si crea un `FrameSender` nuovo: un thread rimasto appeso
  su una sessione morta non blocca quella nuova.
- `stopSession()` (bloccante) spostato su un thread dedicato in `closeSessionOnly()`
  e `stopInternal()`: non blocca più il thread dei callback Go né la UI ("Chiudi").

---

## 🚀 1.1.6 — Riconnessione in background + Garage + icone header

**Data**: 2026-11-06 · **versionCode**: 1106

### Fix principale: riconnessione automatica con app in background
- **Causa**: `WifiP2pManager` è "location-gated" da Android 10. Con l'app in background
  (nessuna Activity visibile) `discoverPeers()`/`connect()` fallivano con `ERROR`, quindi
  il gruppo Wi-Fi Direct non si ricreava e il TFT non tornava raggiungibile.
- **Fix**: `ScreenCaptureService` dichiara `foregroundServiceType="mediaProjection|location"`
  (+ permesso `FOREGROUND_SERVICE_LOCATION`), con fallback automatico a solo
  `mediaProjection` se il permesso di posizione manca.
- **Fix**: nuova `HudSession` (vita di processo) che possiede `WifiApController` e
  `HudStreamController`. Prima `onDispose` della UI faceva `stop()` + `dispose()`,
  uccidendo il loop di retry P2P se l'Activity veniva distrutta in background.
- **Fix**: teardown P2P asincrono (`requestGroupInfo` → `removeGroup` solo se il gruppo
  esiste → attesa esito), `HandlerThread` dedicato, `BUSY` → retry 1,5 s,
  `ERROR` → log dello stato permessi/GPS.
- **Fix**: probe TCP di riconnessione da 60 s a 8 s; dopo 3 cicli falliti il livello
  streaming sollecita il link (`nudge()`) a ricreare il gruppo.

### Fix: video "frizzato" dopo la riconnessione
- Alla riconnessione `attachMirroring()` usciva subito (encoder e VirtualDisplay già
  vivi) senza riattivare l'encoder né forzare un frame: il TFT restava sull'ultima
  immagine finché lo schermo del telefono non cambiava (sembrava servire un tocco).
- Ora si chiama `resumeEncoderIfPaused()` + `forceFreshFrame()` (resize del
  VirtualDisplay + `requestSyncFrame`): il TFT si aggiorna subito.

### UI
- Header sticky unico con logo MotoBit e 3 icone: Home, Moto (Garage), Ingranaggio (Impostazioni).
- Nuova pagina **Garage** con scheda veicolo e dati del link.
- Transizioni slide da destra verso sinistra tra le schermate.
- "Scansiona QR" spostato in basso, fuori dall'header.
- Nuova sezione **Permessi e affidabilità** in Impostazioni (posizione, GPS, notifiche,
  batteria) con stato live e pulsante che apre il dialog o la schermata giusta.
- Nuova icona di lancio (adaptive + tutte le densità).
- Icona moto come vettoriale locale: rimosso `material-icons-extended` (−50 MB di dex).

---

## 📋 INCOLLA NEL TERMINALE PER CREARE LO ZIP DI BACKUP

> PowerShell (Terminal di Android Studio va bene). **Cambia solo `$v`** (nome dello zip = versione).
>
> ⚠️ **`$dir` (cartella da archiviare) NON cambia**: il root del progetto è ancora `Motobit_alfa_V1.0.4`, mentre lo zip prende il nome della versione. Sono due cose distinte.
>
> ⚠️ **Esclusi di proposito** (dal 1.1.5): `_versions` (25 APK = 2,5 GB), `.git` (570 MB), i `*_sorgenti.zip` nel root (backup dentro il backup) e i residui `aar-check` / `hudlib-check.zip` / `diagnostica`. Senza questi esclusi lo zip passa da **479 MB a 2,2 GB**.

```powershell
$v   = 'Motobit_alfa_V1.1.5'   # <-- UNICA RIGA DA CAMBIARE a ogni versione (nome dello zip)
$dir = 'Motobit_alfa_V1.0.4'   # <-- cartella del progetto (root piatta: NON cambia)
$dev = 'H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev'
$zip = Join-Path $dev "dev_backup\$v.zip"
$ex  = '*/build','*/build/*','*/.gradle','*/.gradle/*','*/.kotlin','*/.idea','*/.idea/*','*/.git','*/.git/*','*/_versions','*/_versions/*','*.zip','*/aar-check','*/aar-check/*','*/diagnostica','*/diagnostica/*'
if (Test-Path $zip) { Remove-Item $zip }
tar -a -c -f $zip -C $dev @($ex | ForEach-Object { "--exclude=$_" }) $dir
```

> Se dopo il paste il prompt scende su `>>`, la shell sta aspettando una quote chiusa: `Ctrl+C` (una o due volte), poi rilanciare. Incollare riga per riga, un solo Invio.

Verifica (stessa logica, un solo punto da cambiare):

```powershell
$v   = 'Motobit_alfa_V1.1.5'
$zip = Join-Path 'H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev\dev_backup' "$v.zip"
Get-Item $zip | Select-Object Name,@{n='MB';e={[math]::Round($_.Length/1MB,1)}}
$l = tar -tf $zip
"VOCI=$($l.Count) BUILD=$(($l|Select-String '/build/').Count) GIT=$(($l|Select-String '/\.git/').Count) VERSIONS=$(($l|Select-String '_versions').Count) APK=$(($l|Select-String '\.apk$').Count)"
$l | Select-String -Pattern "settings.gradle.kts$|app/libs/hudlib.aar$|local.properties$|gradle.properties$|CHANGELOG.md$"
```

Atteso: **~479 MB** e `241` voci circa; `BUILD=0 GIT=0 VERSIONS=0 APK=0`; presenti `settings.gradle.kts`, `app/libs/hudlib.aar`, `local.properties`, `gradle.properties`, `CHANGELOG.md`.

> Nota: lo zip **non** contiene la storia git né gli APK archiviati. Restano comunque su disco in `motobit-ride-android/.git`, `motobit-ride-lib/.git` e `_versions/`.

---

## 🔢 CAMBIARE VERSIONE (1 riga sola)

Sorgente unica di verità: **`MOTO_VERSION` in `gradle.properties`**. Non toccare più `versionCode` / `versionName` in `app/build.gradle.kts` (sono calcolati).

```properties
# gradle.properties
MOTO_VERSION=1.0.7-alfa
```

`versionCode` si calcola da sé: `X.Y.Z` → `X*1000 + Y*100 + Z`

| `MOTO_VERSION` | `versionCode` |
|---|---|
| 1.0.3-alfa | 1003 |
| 1.0.4-alfa | 1004 |
| 1.0.5-alfa | 1005 |
| 1.0.6-alfa | 1006 |
| 1.0.7-alfa | 1007 |
| 1.1.0-alfa | 1100 |
| 2.0.0-alfa | 2000 |

Regola: `versionCode` deve sempre crescere; se il valore in `MOTO_VERSION` non è nel formato `X.Y.Z`, Gradle si ferma con errore (voluto).

---

## Motobit_alfa_V1.1.5 — 2026-11-05

- **Risolto (disconnessioni a ~37 min) — causa MISURATA, non ipotizzata**: il push dell'ora NON richiesto (`PxcTimeSyncAck`, `0x10451`) inviato ogni 5 minuti sulla socket di controllo PXC (`:54612`) **non viene letto dal TFT**: resta in coda TCP non confermata con RTO in backoff esponenziale (448 ms → 25 s → 50 s → **101 s**, `backoff=8`). Poiché TCP consegna in ordine, quel segmento **blocca gli ACK `0x10631`** dovuti alle richieste periodiche del TFT (`0x10630`, ogni ~60 s). Il TFT resta senza risposta e **chiude la socket ~48 s dopo** (`lastClose="REMOTE_CLOSE afterLastRX=48.378s afterLastTX=48.378s"`), poi **abbatte MediaStream ~73 s dopo** (`error reading header: EOF`) → `StopStream` → riconnessione.
  - Evidenza nei `CHANNEL_DIAG`: `retrans=1` con RTO crescente su `:54612` **esattamente** dopo ogni `0x10451` (08:42:00 → backoff 8; 08:47:00 → backoff 7), mentre durante tutta la jam `MediaStream` continuava a ricevere poll (`lastRX=22ms`, `polls=67179`): il video era vivo, il canale di controllo no.
- **Corretto**: `startKeepaliveClockPush()` in `hud/net/clocksync.go` ora parte **DISATTIVATO** (campo `periodicKeepalive`, default `false`). Il TFT **non** ha bisogno di traffico non richiesto: la sua richiesta `0x10630` ogni ~60 s è già la keepalive, e gli rispondiamo come sempre (la risposta vive in `handleEvent`, non nel keepalive).
- **Dimostrato che la raffica iniziale non è la causa**: `:54612` senza `retrans` per i primi ~30 minuti (08:39:30 → `retrans=0 timer=off`); la jam compare solo con i push periodici. Quindi la raffica all'handshake (3..60) resta invariata.
- **Rimosso (codice morto)**: tutto il percorso "1 pacchetto al primo frame" — `PXCControl.SendSingleClockPush`, `CfmotoHUD.SendSingleClockPush`, `MobileSession.SendSingleClockPush` e il ramo Kotlin `if (clockPushCount.value == 1)`. L'opzione era già stata eliminata dallo slider e il codice non era più raggiungibile. Guardia della raffica corretta da `count <= 1` a `count <= 0`: il valore **0** resta valido per TFT che non richiedono la raffica; **minimo e default restano 3** (range 3..60).
- **Aggiunto (solo per bisazione)**: interruttore in **Diagnostica** → *"Keepalive orologio periodico PXC (5 min)"*, **default OFF**, così in strada è possibile riaccenderlo **senza ricompilare l'AAR** (`MobileSession.SetPeriodicClockKeepalive`).
- **Invariato**: raffica iniziale dell'ora (3..60, default 3), risposta a `0x10450` (richiesta ora del TFT), risposta a `0x10630`, socket heartbeat `:54613`, MediaControl, MediaStream, encoder, Wi-Fi Direct.
- **Noto**: a **freddo** (primo avvio) la raffica da **3** pacchetti può non bastare a impostare l'ora sul TFT; basta salire a 5 una volta, poi l'ora rimane anche tornando a 3. Default 3 confermato per scelta.
- **Verificato sul campo**: **1 ora e 34 minuti** di streaming continuo **senza alcuna disconnessione**, senza riconnessioni e senza QR. Criterio di verifica: `retrans=0 timer=off` stabile su `192.168.13.1:54612` nei `CHANNEL_DIAG` ogni 30 s.
- **Toolchain**: `gomobile` riallineato (`v0.0.0-20251126181937-5c265dc024c4`) e `-androidapi 21` obbligatorio (con API 16 il bind fallisce: `unsupported API version 16 (not in 21..37)`).

Backup: SI → `dev_backup\Motobit_alfa_V1.1.5.zip`
APK: `_versions\Motobit_1.1.5.apk` · versionCode **1105** · `1.1.5` · SHA256 `EA423BEB197CF421D3A1001A13E6FE366E794BBD64E92A529DEE15A81CE1D896` · testato: 100% sul campo (1h34m continui)

---

## Motobit_alfa_V1.1.4 — 2026-11-04

- **Risolto (disconnessioni a 14 min)**: In `hud/net/pxc.go`, la socket 1 di controllo non viene più chiusa in caso di `READ_TIMEOUT` (`if closeReason == "READ_TIMEOUT" { continue }`), rispecchiando il comportamento dell'app Carbit/EasyConn ufficiale (`PXCSocketManager`). La socket 1 resta aperta in stato TCP `ESTABLISHED`, impedendo al TFT di inviare il pacchetto `EOF` dopo 14 minuti.
- **Corretto (bug `SetClockPushCount`)**: In `hud/core/cfmoto.go`, la funzione `SetClockPushCount` ora salva `hud.clockPushCount = count` ed imposta correttamente il valore scelto nelle Impostazioni dell'app al momento della creazione del server PXC.
- **Aggiunto (opzione 1 pacchetto orologio al primo frame)**: Aggiunta nelle Impostazioni l'opzione `1 (sincronizzato al primo frame)`. Quando selezionata, la raffica all'handshake viene saltata e viene inviato 1 singolo pacchetto dell'ora in concomitanza con l'evento `MediaCtrl/VideoStart` (primo frame video).
- **Corretto (tempi morti riconnessione)**: In `HudStreamController.kt`, `RECONNECT_MIN_DELAY_MS` è stato ridotto da `1000ms` a `100ms` per ripristinare la sessione istantaneamente in caso di caduta.
- **Corretto (socket reuse)**: In Go (`hud/net/listener_posix.go` / `listener_windows.go`), abilitati `SO_REUSEADDR` e `SO_REUSEPORT` sui server TCP per eliminare l'errore `bind: address already in use` e velocizzare la connessione iniziale (handshake in ~700ms).
- **Verificato sul campo**: 43 minuti consecutivi di streaming video ininterrotto senza disconnessioni, senza riconnessioni e senza mai mostrare il QR code.

Backup: SI → `dev_backup\Motobit_alfa_V1.1.4.zip`
APK: `_versions\Motobit_alfa_V1.1.4.apk` · versionCode **1104** · `1.1.4` · testato: 100% sul campo (43 min continui)

---

## Motobit_alfa_V1.0.7 — 2026-10-31

Base: sorgenti dell'app **1.0.5** ripristinati dallo zip `dev_backup\Motobit_alfa_V1.0.5.zip`
(`git diff` vuoto rispetto al commit `a61e2f4`), più una correzione nella libreria Go.
La serie 1.0.6 è stata sperimentata e **non rilasciata**: non esiste come versione.

- Corretto (libreria `hud/net/pxc.go`): **scrittura atomica dei pacchetti PXC**. `writeResponse`
  inviava header e corpo con **5 `Write` separate**; sulla stessa connessione scrivono due
  goroutine (servizio dei comandi + clock push), quindi i pezzi potevano intercalarsi e il TFT
  riceveva un pacchetto corrotto. Ora il pacchetto è costruito in memoria e inviato con **una
  sola `Write`** (atomica sul socket TCP).
- Corretto (libreria `hud/net/stream_ctrl.go`): stessa correzione sul canale MediaControl
  (3 `Write` + payload → una sola).
- Aggiunto (libreria `hud/net/pxc.go`): classificazione degli errori per capire **chi** chiude
  la sessione: `REMOTE_CLOSE`, `REMOTE_CLOSE_PARTIAL`, `READ_TIMEOUT`, `LOCAL_STOP`,
  `PROTOCOL_ERROR`, `WRITE_ERROR`, `READ_ERROR`. Il `magic check` fallito ora produce un
  `PROTOCOL_ERROR` con i valori (prima usciva in silenzio).
- Aggiunto (libreria `hud/net/pxc_test.go`): `TestPXCConcurrentWritesDoNotInterleave`.
  Con il codice precedente **fallisce 3/3** (`body corrotto`, `size=65537`), con la correzione passa.
- Invariato: logica dell'orologio (formato, ordine, timing, 60 invii in 15 s), encoder H.264,
  negoziazione risoluzione, Wi-Fi Direct, e **tutto il codice Kotlin dell'app** (è la 1.0.5).
- Noto (da affrontare dopo): il **retry P2P** di `WifiApController` non si riprende se
  `discoverPeers` risponde `ERROR` — crea un canale nuovo a ogni tentativo, non chiama
  `stopPeerDiscovery` e non attende `removeGroup`. Documentato nel log del 31/10 22:16-22:23
  (34 tentativi, 6 minuti, `0 trovati`).
- Noto: differenza residua di alcuni secondi sull'orologio del TFT (problema separato).
- Verificato: 455 s di streaming continuo senza errori (21:59:11 → 22:06:46), contro una
  mediana precedente di ~130 s. Test lungo su strada ancora da fare.

Backup: SI → `dev_backup\Motobit_alfa_V1.0.7.zip`
APK: `_versions\Motobit_alfa_V1.0.7.apk` · versionCode **1007** · `1.0.7-alfa` · testato: parzialmente (7,5 min)

---

## Motobit_alfa_V1.0.5 — 2026-10-30
- Cambiato: `MOTO_VERSION` → `1.0.5-alfa` (versionCode calcolato **1005**, verificato con `aapt2` sull'APK).
- Corretto: il retry P2P armato durante la negoziazione **smontava il gruppo appena formato** (~6 s dopo `LINK P2P ATTIVO`). Ora `markConnected()` annulla ogni retry pendente.
- Corretto: `groupFormed=false` senza link attivo è solo un transiente: non arma più un retry (prima generava `Retry P2P #n`).
- Corretto: un secondo Avvia (o `connect`) con le stesse credenziali non rifà più il teardown del tentativo in corso: log `connect(...) ignorato: tentativo in corso`.
- Corretto: lookup del peer guidato dagli eventi `PEERS_CHANGED` + polling 1 s (prima attesa fissa di 6 s) e runnable annullato al teardown (nessun doppio connect).
- Corretto: MAC o config P2P non validi non azzerano più il retry automatico (prima `autoRetryEnabled = false` lasciava il link morto per sempre).
- Corretto: anche la sessione Go **in fase di `startSession()`** viene chiusa con le altre (fine di `listen tcp :10922: bind: address already in use`).
- Corretto: **Stop = pausa** — sessione e cattura restano attive, l'encoder va in pausa; Avvia riprende senza handshake e senza nuovo consenso (prima l'HU rifiutava la nuova sessione per ~45 s). La chiusura completa resta sul pulsante **Chiudi** (nuovo metodo `close()`).
- Aggiunto: probe TCP su `192.168.13.1:10930` prima di ogni handshake (log `HUD pronto su … dopo X ms`): evita tentativi da 10 s a vuoto.
- Aggiunto: pulsante **Chiudi** — chiude sessione, rilascia il link, ferma il servizio e termina l'app.
- Noto: dal `LINK P2P ATTIVO` alla prima sessione pronta servono **~17 s costanti** (10:18:36→10:18:53, 10:20:19→10:20:36): è un tempo dell'HU, ora misurato dal probe.
- Noto: Android revoca la `MediaProjection` quando l'HU chiude la sessione (`MediaProjection revocata dal sistema/utente`) → serve un nuovo consenso. Da ridurre nella 1.0.6.
- Verificato con i log 10:18-10:25: Stop→Avvia ripreso in **7 ms** (`Sessione ancora viva: ripresa immediata senza handshake`); link P2P agganciato in 1,5-2,5 s e stabile (nessun `Link P2P perso` dopo 6 s).

Backup: SI → `dev_backup\Motobit_alfa_V1.0.5.zip` (299,7 MB, creato 2026-10-30 10:58 con `tar`; esclusi `build`, `.gradle`, `.kotlin`, `.idea`; 561 voci; include `motobit-ride-lib`, `hudlib.aar`, `.git`, `local.properties` e tutti gli APK in `_versions`)
APK: `_versions\Motobit_alfa_V1.0.5.apk` · 101,9 MB · build `assembleDebug` 2026-10-30 10:55 (BUILD SUCCESSFUL, 0 warning) · `aapt2` conferma versionCode 1005 / versionName 1.0.5-alfa · testato: SI

---

## Motobit_alfa_V1.0.4 — 2026-10-29
- Cambiato: **root del progetto piatta**. Da `Motobit_alfa_V1.0.3\Motobit-Ride\Motobit-Ride\Motobit-Ride\motobit-ride-android` a `Motobit_alfa_V1.0.4\motobit-ride\motobit-ride-android` (eliminati i 3 livelli ridondanti; `motobit-ride-lib` resta sibling).
- Cambiato: `MOTO_VERSION` → `1.0.4-alfa` (versionCode calcolato **1004**, verificato con `aapt2` sull'APK).
- Cambiato: risoluzione mDNS senza API deprecate — `registerServiceInfoCallback` + `hostAddresses` su API 34+, `resolveService` legacy su API 29..33 (commit `5200246` sulla storia V1.0.3).
- Noto: l'APK archiviato come `_versions\Motobit_alfa_V1.0.3.apk` **non** contiene il fix mDNS (build precedente al fix): il fix debutta in 1.0.4.
- Noto: le firme reali nell'SDK 36 sono `onServiceUpdated(NsdServiceInfo)` + `onServiceLost()` e `hostAddresses` è una `List<InetAddress>` (non `onServiceInfoUpdated`, non array) — verificate con `javap` su `android-34/35/36`.

Backup: SI → `dev_backup\Motobit_alfa_V1.0.4.zip` (221,6 MB, creato 2026-10-29 15:07 con `tar`; esclusi `build`, `.gradle`, `.kotlin`, `.idea`; 522 file; include `motobit-ride-lib`, `hudlib.aar`, `.git`, `local.properties` e `_versions\Motobit_alfa_V1.0.3.apk`)
APK: `_versions\Motobit_alfa_V1.0.4.apk` · 101,9 MB · build `assembleDebug` 2026-10-29 14:47 (BUILD SUCCESSFUL, 0 warning) · `aapt2` conferma versionCode 1004 / versionName 1.0.4-alfa · testato: no

---

## Motobit_alfa_V1.0.3 — 2026-10-29
- Aggiunto: link Wi-Fi Direct (P2P) verso l'HU — MAC dal QR come `deviceAddress`, IP da `groupOwnerAddress`, mDNS saltato se l'IP è noto, retry illimitato con backoff 3→10 s (TAG log `WifiP2pLink`).
- Aggiunto: mirroring reale MediaProjection → VirtualDisplay → ImageReader → YUV420 → MediaCodec ByteBuffer con pace thread 30 fps (input Surface limitato a ~1 fps a schermo fermo). Baseline, level 3.1, `keyint=1`, CBR 3 Mbps, SPS/PPS sui keyframe.
- Aggiunto: risoluzione negoziata applicata solo su `source==3 && cmd==16`, valori binari allineati a 16 (l'HU negozia 592x752).
- Aggiunto: riconnessione trasparente — chiude solo la sessione Go, projection/encoder/display restano vivi.
- Aggiunto: sospensione encoder dopo N minuti di attesa (default 5, range 0..30, 0 = mai) con `virtualDisplay.resize()` al risveglio.
- Aggiunto: HUD memorizzato (`SavedHud`) — QR una volta sola, credenziali persistite, nome mostrato.
- Aggiunto: impostazioni — comportamento avvio (3 opzioni, default "solo credenziali"), sospensione encoder, esclusione Doze, HUD salvato.
- Aggiunto: pannello log in-app (Copia / Salva .txt / Pulisci).
- Aggiunto: `CHANGELOG.md` con comando `tar` per lo zip di backup (variabile `$v`) e sezione "cambiare versione".
- Aggiunto: cartella `_versions\` (gitignored) con l'APK archiviato per versione.
- Cambiato: `applicationId`/namespace `com.nos.ridedaemon` → `com.nos.motobitride`.
- Cambiato: versione da sorgente unica `MOTO_VERSION` in `gradle.properties`; `versionName` = `MOTO_VERSION`, `versionCode` calcolato (`X*1000+Y*100+Z`). Valori attuali: `1.0.3-alfa` / `1003`.
- Cambiato: baseline git creata (commit `0d4d523`, tag annotato `Motobit_alfa_V1.0.3`), `app/libs/hudlib.aar` tracciato con `git add -f`.
- Risolto: `core/hud/HudStreamController.kt.bak` spostato in `docs/legacy/` (git mv, storia preservata); `.gitignore` ora ignora `*.bak` e `*.orig`.
- Noto: remoto git "grafted" (storia shallow): non fare push senza valutazione del remoto.
- Noto: TFT adatta il video preservando il rapporto; per riempirlo serve rapporto 0,787 (target 1080x1372). Schermo virtuale scartato. Risoluzione via Shizuku/bolla: non presente in questa versione (esiste solo nella copia `test\`).

Backup: SI → `dev_backup\Motobit_alfa_V1.0.3.zip` (182,5 MB, creato 2026-10-29 con `tar`; esclusi `build`, `.gradle`, `.kotlin`, `.idea`; 278 file / 212,6 MB non compressi; include `motobit-ride-lib`, `hudlib.aar`, `.git` e `local.properties`)
APK: `_versions\Motobit_alfa_V1.0.3.apk` · 101,9 MB · build `assembleDebug` 2026-10-29 (BUILD SUCCESSFUL 28s, 34 task) · `aapt2` conferma versionCode 1003 / versionName 1.0.3-alfa · testato: no
Ripristino: `git checkout Motobit_alfa_V1.0.3 -- .` a IDE aperto (oppure `git reset --hard 0d4d523`)
