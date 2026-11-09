# TODO — Motobit Ride (Android)

Idee e lavori **da fare**, in ordine di priorità. Nessuna di queste voci è implementata:
qui si annotano solo requisiti, note tecniche e decisioni ancora aperte.

Ultimo aggiornamento: 2026-11-06 · versione corrente: **1.2.8 (1208)**

---

## 1. ✅ Garage — restyling a card *(FATTO in 2.0.1 → 2.0.5)*

> Implementato: una sola card con nome (H1) + targa (H2), modifica in dialog con
> matita, pulsanti **Scadenze** e **Info tecniche** (stile Impostazioni),
> **Dimentica** in fondo con conferma che cancella anche i dati in memoria.
> MAC TFT reso persistente. Restano solo le voci sotto, non bloccanti.

Layout richiesto, dall'alto in basso:

1. **Nome moto** — casella di testo **compilabile**, scritta dall'utente.

   **Regola di priorità del nome** (oggi `"🏍️ Voge 800 Rally"` è **scritto nel codice**
   in `GarageScreen.kt`, non arriva dall'HUD):

   | Priorità | Fonte | Esempio |
   |---|---|---|
   | 1 | **Nome inserito dall'utente** (persistito) | `La mia Voge` |
   | 2 | **Nome dall'HUD** — `SavedHudData.displayName` (campo `name` del QR, altrimenti SSID) | `voge 033990` |
   | 3 | Nessun HUD memorizzato | `Nessun veicolo` |

   - [ ] Rimuovere la stringa hardcoded `"🏍️ Voge 800 Rally"`.
   - [ ] Se l'utente svuota il campo → si torna al nome dell'HUD (priorità 2).
   - [ ] Il nome utente va salvato **per veicolo** (vedi punto 3, veicoli multipli).
2. **Targa** — campo compilabile, con **a fianco** le tre scadenze, ognuna con
   **mese (1–12) + giorno (1–31, default 1)**:
   - **Revisione tecnica**
   - **Tassa di circolazione**
   - **Assicurazione**

   > Il giorno è lasciato all'utente: in alcuni paesi la scadenza è "entro il mese",
   > in altri "entro un giorno preciso". Default giorno = 1.
3. **Informazioni unificate** — un solo box che accorpa il contenuto dei tre box attuali
   (veicolo + informazioni veicolo + HUD memorizzato), con:
   - pulsante **Dimentica** se ci sono dati salvati
   - pulsante **QR** (scansione del QR del TFT) se non c'è nulla in memoria

Note:
- [ ] Dati salvati in locale (SharedPreferences/DataStore) e legati al veicolo
      (vedi punto 3, veicoli multipli).
- [ ] Validazione targa italiana (opzionale): `AA123BB` oppure formato vecchio `AA12345`.
- [ ] Le due tendine salvano **solo il mese**: l'anno si calcola di volta in volta
      (prossima occorrenza).

---

## 2. ✅ Scadenze veicolo *(DECISO: opzione B — solo notifiche; FATTO in 1.3.0)*

> Google Calendar **scartato**. Implementate 3 scadenze (revisione, tassa,
> assicurazione) con mese + giorno, notifiche WorkManager con Posponi/Elimina,
> preavviso configurabile. La sezione sotto resta come documentazione della scelta.

### Le strade possibili

| Soluzione | Permessi | Pro | Contro |
|---|---|---|---|
| **A. Notifica locale** (WorkManager + `POST_NOTIFICATIONS`) | già concessi | nessun account, funziona offline, messaggio e timing sotto controllo | vive solo sull'app: se la disinstalli le scadenze spariscono; non consultabile da fuori |
| **B. `CalendarContract`** (Google Calendar via provider) | `READ_CALENDAR` + `WRITE_CALENDAR` | evento **ricorrente annuale** nel calendario che l'utente guarda già; sync su tutti i dispositivi con lo stesso account | dialog permessi "accesso al calendario"; va scelto il calendario; rischio duplicati se non si salva l'ID evento |
| **C. API Google Calendar (REST + OAuth)** | OAuth + rete | controllo totale | richiede client ID, consent screen, refresh token — troppo per due date |
| **D. Intent al calendario** (`ACTION_INSERT`) | nessuno | semplicissimo | l'utente deve confermare a mano evento per evento, nessuna automazione |

### Valutazione

- Una **scadenza annuale** è un dato che si consulta **dal calendario** (B), non dall'app della
  moto: la trovi anche quando non hai MotoBit davanti.
- Però chiedere `WRITE_CALENDAR` per due date può sembrare invasivo, e va gestito il ciclo di
  vita dell'evento (creazione, aggiornamento, eliminazione) salvando l'**ID evento**.
- **A** è la base che funziona sempre e non chiede nulla; **B** è il valore aggiunto.

### ✅ DECISO: opzione B — solo notifica dell'app (niente Google Calendar)

Nessuna integrazione calendario: si evita `WRITE_CALENDAR` e tutta la gestione degli eventi.

### Le tre scadenze (DECISO: mese + giorno per tutte)

| Scadenza | Dato salvato |
|---|---|
| **Revisione tecnica** | mese (1–12) + giorno (1–31, default 1) |
| **Tassa di circolazione** | mese (1–12) + giorno (1–31, default 1) |
| **Assicurazione** | mese (1–12) + giorno (1–31, default 1) |

> Il giorno è lasciato all'utente: c'è chi ha la scadenza "entro il mese" e chi
> "entro un giorno preciso". Default = 1.

### Impostazioni globali

- **"Avvisami N giorni prima"** — campo numerico, **default 7**, intervallo 1–60.
  Vale per tutte e tre le scadenze (in futuro si può rendere per-scadenza).
- **Switch "Inviami notifiche"** — **default ON**. Se OFF: nessuna notifica di scadenza
  (il conto alla rovescia nel Garage resta visibile).

### Contenuto della notifica

**Titolo**: `Revisione tecnica — tra 5 giorni`
**Testo**: `Voge 033990 · scadenza marzo 2027`
**Azioni**: `Posponi` · `Elimina`

Varianti:
- il giorno stesso: `Revisione tecnica — scade oggi`
- il giorno prima: `Revisione tecnica — scade domani`

### Comportamento dei due tasti

| Tasto | Effetto | Quando torna |
|---|---|---|
| **Posponi** | snooze di **1 giorno** | domani, stessa ora |
| **Elimina** | sopprime l'avviso **per questa occorrenza** | alla **prossima scadenza** (anno successivo) |

> ✅ **DECISO (D1 = A)**: "Elimina" **non** cancella la scadenza dal Garage: sopprime solo
> la notifica fino all'anno successivo. La scadenza resta visibile in app con il conto
> alla rovescia, e l'avviso si riarma da solo alla prossima occorrenza.

### Note tecniche

- Canale notifiche dedicato **"Scadenze veicolo"** con importanza **DEFAULT** (suona/vibra),
  diverso dal canale del mirroring che è `LOW`.
- `WorkManager` `PeriodicWorkRequest` **giornaliero** (`ExistingPeriodicWorkPolicy.KEEP`):
  sopravvive al riavvio, non richiede `SCHEDULE_EXACT_ALARM`.
  - prima esecuzione con `initialDelay` fino alle ~09:00, per non svegliare di notte;
  - a ogni giro: per ogni scadenza calcola la prossima occorrenza, confronta con
    `giorniPrima`, salta se in snooze o già eliminata per quell'anno.
- Azioni della notifica tramite un `BroadcastReceiver` (come `NotificationActionReceiver`).
- Stato per scadenza da persistere: `snoozedUntil` (data) e `dismissedForYear` (anno).
- **Persistenza (D3 = A)**: JSON in `SharedPreferences` (stesso schema di `SavedHud`),
  niente Room per ora.
- **Nuova dipendenza**: `androidx.work:work-runtime-ktx` (~1 MB).
- Se l'app viene disinstallata le notifiche cessano (ovvio): nessun dato sopravvive.

---

## 3. Veicoli multipli (memorizzare e riusare più connessioni) — **PROSSIMO**

> ✅ Prerequisito pronto: il Garage è ora una card unica, quindi una lista di
> veicoli non richiede un ridisegno.

- [ ] Poter salvare **più veicoli**, ognuno con: nome, targa, mesi di scadenza,
      SSID/MAC/HU del QR associato.
- [ ] Selettore del veicolo attivo in Home/Garage.
- [ ] La riconnessione automatica usa il QR del **veicolo selezionato**.
- [ ] `SavedHud` oggi è un singolo oggetto: va evoluto a **lista + id selezionato**
      (migrando il dato esistente).

---

## 4. 🔴 PRIORITÀ TECNICA — Riconnessione lenta dopo la riaccensione del TFT (20–30 s)

> ⚠️ **In parte già risolto in 1.2.4** (`onLinkRestored` azzera il backoff al ritorno del link).
> Da verificare sul campo quanto resta; l'handshake Go (5–10 s) è ancora da ottimizzare.

- [ ] Capire dove finiscono i secondi residui e ridurli a 2–3 s.
- [ ] Misurare con i log già presenti (vedi "Come misurare" sotto).

### Da dove viene il ritardo (analisi sul codice)

Alla riaccensione del TFT il percorso è:

```
TFT si riaccende
  → gruppo P2P si riforma
  → WifiApController.markConnected() → onHudConnected(ip)
  → HudSession: setPreferredHost(ip) + onLinkRestored(autoResume)
  → beginDiscovery() → waitForHudPort() (fino a 8 s)
  → connectToHost() → startSession() (handshake 5–10 s)
```

**Il problema originale** — in `HudSession.onHudConnected` si chiamava `start()` solo con
stato `IDLE`/`ERROR`; durante un'assenza lo stato è **`WAITING_HUD`** → nessuna sollecitazione,
e si aspettava il timer di `scheduleReconnect` al **backoff massimo di 10 s**.
→ risolto in **1.2.4** con `HudStreamController.onLinkRestored()`.

### Il conto dei tempi (caso peggiore)

| Fase | Costo |
|---|---|
| Attesa del timer di backoff (prima: fino a 10 s) | **0 s** ✅ dopo 1.2.4 |
| `waitForHudPort()` — probe ogni 1 s | **fino a 8 s** |
| `startSession()` — handshake Go | **5–10 s** |
| **Totale atteso** | **~6–18 s** |

### Fix ancora da valutare

1. **Probe più corto quando il link è appena tornato**: 500 ms per i primi 3 s invece di 8 s
   ciechi (l'HU apre la porta in ~1 s quando è pronta).
2. **Handshake**: 5–10 s sono della libreria Go (`startSession`). Da valutare se il
   `waitForHudPort` può essere accorpato all'handshake invece di precederlo.
3. Verificare che non ci siano due loop di probe in parallelo (`probeInFlight` aggiunto in 1.2.4).

### Come misurare

```
adb logcat | grep -iE "HUD pronto su|SESSION_DIAG|Link tornato|LINK P2P ATTIVO"
```

| Riga | Cosa dice |
|---|---|
| `LINK P2P ATTIVO, ip=...` | quando il link è tornato (T0) |
| `Link tornato: riconnessione immediata` | il sollecito è partito subito |
| `HUD pronto su ... dopo Y ms` | quanto è durato il probe |
| `SESSION_DIAG ready ... handshakeMs=Z` | quanto è durato l'handshake |

---

## 5. Mantenere lo schermo acceso durante la sessione

- [ ] Schermo sempre acceso **finché la sessione è connessa** e **finché non scade** l'attesa
      attiva impostata (Impostazioni → Connessioni).
- [ ] Rilascio automatico in tutti questi casi:
  - [ ] utente preme **Stop** o **Chiudi**
  - [ ] link perso **oltre** l'attesa attiva impostata
  - [ ] `MediaProjection` revocata dal sistema
  - [ ] servizio di cattura distrutto

### Note tecniche

| Approccio | Funziona con MotoBit in background? | Note |
|---|---|---|
| `FLAG_KEEP_SCREEN_ON` sulla propria Activity | ❌ **No** | Vale solo per la finestra visibile: se l'utente è nel navigatore, la finestra di MotoBit non è visibile e il flag non ha effetto |
| `PowerManager.SCREEN_BRIGHT_WAKE_LOCK` / `SCREEN_DIM_WAKE_LOCK` dal foreground service | ✅ Sì | Deprecato da API 17 ma **ancora funzionante**; è l'unico modo per tenere acceso lo schermo mentre un'altra app è in foreground |
| `Activity.setTurnScreenOn(true)` | ❌ No | Serve solo a riaccendere lo schermo all'avvio dell'Activity |

**Decisione da prendere**: usare un `SCREEN_BRIGHT_WAKE_LOCK` (o `SCREEN_DIM_WAKE_LOCK`) acquisito
in `ScreenCaptureService` e rilasciato secondo le condizioni sopra. Va gestito con attenzione:
un wake lock schermo mai rilasciato = batteria prosciugata.

**Rischio noto**: tenere lo schermo acceso a luminosità piena per ore scalda il telefono e
consuma batteria → da qui nasce il punto 6 (oscuramento).

---

## 6. Oscuramento schermo con selettore di opacità

- [ ] Selettore di oscuramento: **0% (nero pieno)**, 10%, 20%, 30%, 40%, 50%.
- [ ] Attivabile/disattivabile **opzionalmente**.
- [ ] Attivazione rapida con:
  - [ ] **sensore di prossimità** (telefono in tasca / coperto → nero; scoperto → livello scelto)
  - [ ] **click sul tasto volume** (toggle on/off)

### Note tecniche

| Approccio | Copre altre app? | Permesso richiesto |
|---|---|---|
| Overlay `TYPE_APPLICATION_OVERLAY` (View nera con alpha) | ✅ Sì | `SYSTEM_ALERT_WINDOW` → **"Visualizza sopra altre app"** (concessione da schermata di sistema, non dialog) |
| `WindowManager.LayoutParams.screenBrightness` sulla propria Activity | ❌ No | nessuno (ma solo con MotoBit visibile) |
| `Settings.System.SCREEN_BRIGHTNESS` (globale) | ✅ Sì | `WRITE_SETTINGS` (sconsigliato: modifica un'impostazione di sistema) |

**Decisione da prendere**: overlay `TYPE_APPLICATION_OVERLAY` è l'unico che funziona davvero
mentre l'utente usa il navigatore. Richiede però un permesso in più da spiegare bene all'utente
(vedi punto 7: la sezione permessi deve includerlo).

**Sensore di prossimità**: `SensorManager` + `TYPE_PROXIMITY`, **nessun permesso**.
Attenzione: non tutti i telefoni hanno il sensore → serve fallback silenzioso (voce nascosta
o disabilitata se `getDefaultSensor(TYPE_PROXIMITY) == null`).

**Tasto volume**: intercettabile solo con la propria Activity visibile (`dispatchKeyEvent`).
Con MotoBit in background servirebbe una `MediaSession` con `VolumeProvider` oppure un
`AccessibilityService` (molto invasivo). **Da valutare**: probabilmente solo per il caso
"MotoBit visibile".

**Interazione con il punto 5**: se lo schermo è acceso ma coperto da un overlay nero al 100%,
il consumo è quasi solo quello del display acceso.

---

## 7. Sezione "Permessi" con switch e stato reale

- [ ] Trasformare la sezione attuale (righe con ✓ e pulsante) in **switch**:
  - **pallino a sinistra = rosso** → permesso mancante
  - **pallino a destra = verde** → permesso concesso
- [ ] Dove il permesso è **concedibile con dialog** → lo switch lo richiede direttamente.
- [ ] Dove **non è possibile** (interruttore di sistema o schermata dedicata) → il tocco sullo
      switch porta **direttamente alla pagina di concessione**.
- [ ] Se l'utente ha negato in modo permanente ("Non chiedere più") → aprire
      *Impostazioni app → Permessi* invece di un dialog che non comparirebbe più.

### Mappa dei permessi

| Permesso | Tipo | Come si concede | Switch? |
|---|---|---|---|
| Posizione precisa (`ACCESS_FINE_LOCATION`) | runtime | dialog di sistema | ✅ sì |
| Dispositivi nelle vicinanze (`NEARBY_WIFI_DEVICES`, API 33+) | runtime | dialog di sistema | ✅ sì |
| Notifiche (`POST_NOTIFICATIONS`, API 33+) | runtime | dialog di sistema | ✅ sì |
| Posizione di sistema (GPS) | **interruttore di sistema** | `ACTION_LOCATION_SOURCE_SETTINGS` | ⚠️ switch "di sola lettura" che al tocco apre la pagina |
| Esenzione batteria (Doze) | **dialog di sistema** | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | ✅ sì |
| Visualizza sopra altre app (per il punto 6) | **schermata di sistema** | `ACTION_MANAGE_OVERLAY_PERMISSION` | ⚠️ switch che al tocco apre la pagina |

### Note tecniche

- Lo stato va riletto al **ritorno in foreground** (`ON_RESUME` / `LifecycleEventObserver`),
  non solo con polling: oggi c'è un `while(true) { ... delay(1500) }` che funziona ma è
  uno spreco; meglio un refresh puntuale sugli eventi di lifecycle.
- `Switch` Material3 con `checked` derivato dallo stato reale e `onCheckedChange` che **non**
  cambia lo stato locale: si limita a lanciare la richiesta, poi lo stato si aggiorna da solo
  quando il sistema risponde. Altrimenti lo switch "mente" per un attimo.
- Aggiungere una riga di riepilogo in cima: *"X di Y permessi concessi"*.

---

## 8. Connessione — altre situazioni da considerare

### A. Feedback all'utente — ✅ **fatto in 1.2.1**

- [x] **Notifica con stato live** (Mirroring / In attesa / In pausa / Attesa passiva / Cattura revocata).
- [x] **Azioni nella notifica**: `Stop` e `Riconnetti` (`NotificationActionReceiver`).
- [x] **Vibrazione breve** su connessione/perdita (interruttore in Impostazioni, default OFF).
- [x] **Messaggi di causa comprensibili** in Home (Wi-Fi Direct spento, HUD occupato, TFT non trovato).

### B. Robustezza

- [x] **MAC salvato obsoleto → solo documentazione (DECISO).** Se il TFT viene sostituito o
      cambia MAC, il connect diretto fallisce per sempre (nessun fallback automatico).
      **Decisione: non si implementa il fallback**; si aggiunge una nota nella pagina **Garage**
      e nelle istruzioni: *"se hai cambiato TFT o non si connette più, riscansiona il QR"*.
      → da fare insieme alle istruzioni.
- [x] **Ruolo group owner — CHIUSO, non serve.** Ci colleghiamo sempre a un **MAC definito**
      (dal QR) e l'IP del GO in Wi-Fi Direct è **fisso** (`192.168.49.1`).
- [x] **Attesa attiva → ascolto passivo** (unica impostazione, default 5 min). Fatto in 1.2.3.
- [x] **MediaProjection revocata in background** — avviso esplicito (1.2.2).
- [ ] **Processo ucciso dal sistema.** `HudSession` è a vita di processo: se Android uccide
      il processo, la sessione muore e serve riaprire l'app. Da documentare nel CHANGELOG
      (non è aggirabile: la MediaProjection non sopravvive).
- [ ] **`MAX_MIRROR_ATTEMPTS = 12`** porta lo stato a `ERROR` dopo 12 s di mirroring non
      agganciato, richiedendo un "Avvia" manuale. Verificare che durante un'assenza lunga
      non si arrivi mai a quel contatore.

### C. Diagnostica utile (per validare i fix)

- [ ] **Cronologia delle disconnessioni**: timestamp, durata dell'assenza, motivo
      (`EOF`, `READ_TIMEOUT`, `link perso`), numero di tentativi. È il dato che manca
      per capire problemi tipo "si disconnette dopo 37 minuti".
- [ ] **Tempo di riconnessione misurato e mostrato**: da `LINK P2P ATTIVO` a `MIRRORING`.
- [ ] **Indicatore di qualità del link**: i channel diagnostics (`retrans`, `backoff`, coda)
      ci sono già nel log: esporre un semaforo semplice in Opzioni Sviluppo.

---

## 9. Altre idee annotate (non urgenti)

- [ ] Documentare nel CHANGELOG il comportamento atteso quando lo schermo del telefono è spento
      (nessun frame da trasmettere: il TFT mostra l'ultimo frame).
- [ ] Rivedere la pagina **Garage** anche graficamente (due card separate oggi, da ridisegnare).
- [ ] Revisione grafica della pagina **Risoluzione** quando avrà contenuti.
- [ ] Revisione della pagina **Opzioni Sviluppo** (contenuto inline, valutarne la divisione).

---

## 10. Consegna frame al TFT ("Fix 2") — ANNOTATO, NON NECESSARIO ORA

**Decisione (07/11/2026)**: non si implementa. La stabilità è validata e il difetto
è impercettibile con il contenuto d'uso reale (mappe). Resta annotato per il futuro.

### Il dato (misurato su due log)

| Log | Richieste TFT | Frame inviati | Idle | FPS reali |
|---|---|---|---|---|
| 1 h | 29,1 Hz | 60.840 | 43% | **16,6 fps** |
| 25 min | 25,7 Hz | 852 | 46% | **14,0 fps** |

Il gate `now - lastAdvance >= interval` (33 ms) rifiuta l'avanzamento sulle richieste
ravvicinate del TFT: quello slot è perso e si risponde `idle` (4 byte). Il TFT **tiene
l'ultimo frame**, quindi il difetto è un "fermo immagine" di 40-70 ms.

**Perché è accettabile**: con mappe (scorrimento lento) il fermo immagine è
impercettibile; con video è evidente. Il caso d'uso reale è la mappa.

### Due strade, se un giorno servisse

| Strada | Come | Costo | Rischio |
|---|---|---|---|
| **A. Kotlin, 1 riga** | passare `60` invece di `targetFps` al 2° parametro di `Api.newMobileConfig(...)`: rende il gate permissivo (16,7 ms) lasciando l'encoder a 30 fps. In Go `TargetFPS` serve **solo** a `interval` e alla diagnostica: **non viene inviato all'HU** | 1 riga, nessun rebuild | traffico 1,66 → ~2,6-3 Mbps |
| **B. Go** | in `LiveStreamSource.NextFrame` avanzare quando la **coda ha un frame**, non in base al tempo | richiede rebuild di `hudlib.aar` — **gomobile non è configurato** in questo ambiente | regressione possibile → video nero |

---

## 11. Scorciatoie app di navigazione + pulsante generale di mirroring

**Requisito utente (07/11/2026)**, da fare dopo la separazione connetti / mirroring.

- [ ] **Pulsante generale** nella Home del telefono per far partire il mirroring.
- [ ] **Collegamento a più app di navigazione**: una serie di icone (Maps, Waze, ...);
      al tocco si apre l'app toccata **e** parte il mirroring (con il consenso già gestito).
- [ ] Se il mirroring è **già attivo**, toccando un'altra icona si apre l'altra app
      **mantenendo** lo streaming (niente riavvio).
- [ ] Apertura app: `Intent` esplicito per package + `FLAG_ACTIVITY_NEW_TASK`
      (siamo in un foreground service); elenco app con `queryIntentActivities`.

**Prerequisito già fatto in 2.0.6**: la cattura è forzata a **schermo intero**
(`MediaProjectionConfig.createConfigForDefaultDisplay()`): il dialog di Android 14+
non offre più "app singola", che romperebbe il cambio app a mirroring attivo.

---

## 12. Revisione grafica della Home (pulsante di connessione)

**Feedback utente (07/11/2026)**: *il funzionamento è quello aspettato, la grafica e i
colori lasciano molto a desiderare* → da rivedere in un secondo momento.

Stato attuale (2.1.2): pulsante full width alto 140 dp, angoli 18 dp, ombra 6 dp,
tre colori pieni `#D32F2F` / `#F9A825` / `#2E7D32`, nome in `titleLarge` bold +
stato in `labelMedium`, riga "progressioni" in `labelSmall`.

- [ ] **Palette**: i tre colori sono "a semaforo". Valutare colori coerenti col tema
      Material 3 (`primary` / `tertiary` / `error`) o una scala derivata dall'icona dell'app.
- [ ] **Forma e ombra**: rivedere corner radius ed elevazione; valutare un gradiente
      verticale tenue invece del colore piatto.
- [ ] **Tipografia**: dimensioni e pesi del nome e dello stato.
- [ ] **Riga progressioni**: sostituire il testo con **micro-icone** (link, wifi,
      risoluzione) + valori, più leggibile a colpo d'occhio.
- [ ] **Animazione di lavoro**: pulsazione del bordo o shimmer sulla seconda riga,
      al posto del solo spinner.
- [ ] **Icona catena spezzata** (`ic_link_off`): verificare la leggibilità a 28 dp.
- [ ] Valutare se, a pulsante validato, **rimuovere la card informazioni** in cima
      (le progressioni sono già dentro il pulsante).

---

## 13. Splash di avvio personalizzata — **PRIORITÀ MOLTO BASSA**

**Decisione (07/11/2026)**: si lascia **solo lo schermo nero**. Togliere l'immagine
(due fasi sbagliate) e l'icona di sistema. Da non riprendere salvo richiesta.

### Stato attuale (2.1.6)
- `Theme.MotobitRide`: `windowBackground` = `@color/splash_background` (`#0B0B0D`)
- `values-v31`: anche `windowSplashScreenBackground` nero, **senza icona**
- Nessuna immagine, nessun overlay, nessun ritardo

### Cosa è stato provato e perché non andava
| Tentativo | Esito |
|---|---|
| `windowBackground` con l'immagine | Su Android 12+ la splash di sistema la copre: non si vede (nero) |
| PNG in `drawable/` | Scalato ~2,6× dalla densità (2237×4845 px): inutilizzabile |
| `SplashOverlay` in-app 750 ms | **Due fasi**: prima Android, poi l'immagine → sbagliato |
| `windowSplashScreenAnimatedIcon` = icona app | Funziona ma non piace / non convince |

### Se un giorno si volesse riprovare
Serve un **vector drawable quadrato** (es. 108×108) con il logo “MotoBit”
bianco/arancione da puntare in `windowSplashScreenAnimatedIcon`; la struttura
del tema è ancora pronta. L'immagine `ic_splash_strtup.png` (852×1846) **non è
adatta** alla splash di sistema, che mostra l'icona inun cerchio.

---

## 14. Connessione lenta con il TFT spento — **DA VERIFICARE**

**Rilevato dall'utente (09/11/2026)**: *"sembra funzionare un po' lento"*.

### I dati misurati (log 11:50-11:51, build 2.3.11)
```
11:50:32.403  Wi-Fi Direct verso DIRECT-VOGE-033990
11:50:33.240  connect richiesta accettata, attendo groupOwner
11:50:48.600  gruppo P2P non ancora formato
11:50:47.185  Retry P2P #1 tra 3000 ms          ← 2.3.9
11:50:48.580  retry passivo: connect diretto
11:51:06.110  lista peer aggiornata
11:51:07.275  gruppo formato                    ← link in 35 s
```
E il tentativo che riesce impiega **0,8 s**: la negoziazione non è lenta,
è l'**attesa tra un tentativo e l'altro**.

### Cosa è già stato corretto
| Versione | Fix |
|---|---|
| 2.3.7 | sblocco di `attemptInFlight` quando il gruppo non si forma (prima restava true per sempre e il `PEERS_CHANGED` veniva ignorato) |
| 2.3.9 | dopo lo sblocco si chiama `scheduleRetry()` → ritmo 3→10 s invece che solo su evento (erano 11-13 s) |
| 2.3.10 | `armActiveWait()` non prolunga più il timer a ogni `connect()` |

### Cosa resta da capire
- [ ] `WifiP2pManager.connect()` viene **accettato** anche con il peer assente e il
defunto si scopre solo dall'evento di sistema (~11-13 s dopo). Verificare se è
possibile un fallimento più rapido.
- [ ] Valutare un `requestPeers()` prima del `connect`: se il peer non c'è, non
sprecare il tentativo (e non toccare la macchina a stati).
- [ ] Misurare `connect` → `PEERS_CHANGED` con TFT acceso (caso buono): atteso 2-4 s.
- [ ] Ridurre `RETRY_MIN_DELAY_MS` (3 s) quando il peer **è già nella lista**: il
caso "TFT appena accesso e visibile" dovrebbe partire subito.
- [ ] Se il TFT impiega 20-30 s ad accendersi, la lentezza è **del TFT**: da
verificare con un cronometro, per non correggere l'app per un problema hardware.

---

## ✅ Chiusi / spiegati

### Notifica di mirroring persistente a app chiusa — **corretto in 2.0.3**
`updateStatus()` ricreava la notifica dopo `stopService` (via
`onTaskRemoved` → `closeAll` → `notify`). Aggiunto il flag `running` e
annullamento esplicito in `onDestroy`/`stopService`; in `HudScreen` prima
`stopService`, poi `closeAll`.

### MAC del TFT non persistente — **corretto in 2.0.4 / 2.0.5**
`SavedHud.save(qr)` riscriveva `mac = qr.mac`: se il QR non lo conteneva, il MAC
appreso da `updateMac` veniva azzerato a ogni ritorno in Home. Ora `save()`
conserva il MAC precedente a parità di SSID. In più "Dimentica" azzera anche il
`savedQr` in memoria (`HudSession.closeAll`) oltre ai dati su disco.

### Freeze del video sul telefono alla disconnessione — **non è un bug nostro**

Catena di esclusioni eseguite sul campo:

| Test | Esito | Conclusione |
|---|---|---|
| Uccidere l'app durante il freeze | il video resta bloccato | la nostra app non "tiene" la pipeline |
| Modalità aereo | nessun freeze | non è la rete |
| Spinner di buffering | assente (immagine ferma) | non è buffering |
| Sospensione encoder a 5 s (1.1.8) | freeze invariato | non è il carico dell'encoder |
| Rilascio immediato della cattura (1.1.9) | freeze invariato | non è la pipeline di cattura |
| **Fotocamera invece di YouTube** | **nessun freeze** | **è l'app sorgente che mal digerisce la disconnessione** |

**Conclusione**: il mirroring è fedele; YouTube entra in uno stato di errore del decoder quando
il gruppo Wi-Fi Direct viene smontato, e non si riprende finché non si fa pause/play. Non
dipende da MotoBit. Da qui in avanti: nessun intervento lato app.

### Martellamento Wi-Fi Direct durante l'assenza del TFT — **corretto in 1.2.0**

`removeGroup` + `initialize` + `connect` a ogni retry (ogni 3–10 s) → sostituito dal
**recupero passivo**: canale riusato, `connect` solo quando il peer riappare, `removeGroup`
solo su `disconnect()`.

### Rilascio della cattura alla disconnessione (1.1.9) — **revertato in 1.1.10**

Rompeva la riconnessione (l'app si connetteva senza trasmettere) e non risolveva il freeze.
La cattura resta viva durante l'assenza, così la ripresa è immediata e senza nuovo consenso.
