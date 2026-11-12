# CONNESSIONE E AVVIO DELLO STREAMING — specifica di riferimento

> Documento di **comportamento atteso**, non di implementazione.
> Se il codice fa qualcosa di diverso da questo, **il codice sbaglia**.
> Ultimo aggiornamento: 2026-11-11 · versione 2.6.0

---

## 1. Il metodo di connessione (le fasi, in ordine)

| Fase | Cosa avviene | Dove vive |
|---|---|---|
| **1. Scoperta** | ricerca **mDNS** del TFT sulla rete | `HudStreamController` (sezione mDNS) |
| **2. Link** | associazione **Wi-Fi Direct (P2P)** con il TFT | `HudSession` (`link`, `teardownP2p`, `onLinkRestored`) |
| **3. Sessione** | socket verso il TFT (PXC) + protocollo/handshake (`MediaCtrl/Init`) | `HudSession` (`stream`) |
| **4. Negoziazione** | il TFT dichiara la sua risoluzione (`resolutionNegotiated`) | `HudStreamController` |
| **5. Streaming** | consenso MediaProjection → `VirtualDisplay` → encoder → frame al TFT | `StreamLaunchActivity` → `ScreenCaptureService` → `HudEncoder` |

**Fasi 1-3 = connessione. Fasi 4-5 = trasmissione.**
La bolla **AVVIA** interviene solo al confine tra 3 e 5: serve a far partire la trasmissione.

---

## 2. I tre casi

### Caso 1 — Avvio MANUALE (TFT acceso, app non collegata)

```
Tocco "Connetti"
    -> fasi 1-2-3 (mDNS -> P2P -> sessione)              NESSUNA BOLLA
TFT connesso
    -> tocco sull'app che voglio aprire
         -> consenso proiezione + apertura app + streaming (fasi 4-5)
```

### Caso 2 — Avvio AUTOMATICO della connessione

```
Apertura app (connessione automatica attiva)
    -> fasi 1-2-3 da sole                                 NESSUNA BOLLA
TFT connesso
    -> tocco sull'app che voglio aprire
         -> consenso proiezione + apertura app + streaming (fasi 4-5)
```

### Caso 3 — TFT DISCONNESSO, si parte dall'app

```
Tocco sull'app
    -> si apre l'app + compare la bolla AVVIA             NESSUNA CONNESSIONE ANCORA

Tocco sulla bolla AVVIA
    -> RICERCA ATTIVA: fasi 1-2-3 ripetute
       per tutta la durata di "attesa attiva della riconnessione" (default 5 min)
       FUNZIONA ANCHE A TFT SPENTO: continua a cercare
    -> notifica "ci stiamo connettendo" (corretta, deve restare)

Connessione stabilita
    -> consenso proiezione + streaming (automatici, senza altri tocchi)

Streaming attivo
    -> la bolla AVVIA SPARISCE (non serve più)
```

---

## 3. Regole trasversali (valgono sempre)

| # | Regola | Stato al 2026-11-11 |
|---|---|---|
| R1 | Streaming attivo -> **la bolla AVVIA sparisce** | ✅ implementato (`StreamState.MIRRORING -> stopSelf()`) |
| R2 | Connessione in corso (casi 1 e 2) -> **nessuna bolla** | ✅ implementato (bolla solo nel ramo "non connesso") |
| R3 | Tocco sulla bolla -> **ricerca attiva garantita**, TFT acceso o spento | ❌ da implementare |
| R4 | Il tocco sulla bolla **non deve azzerare** una ricerca già in corso | ⚠️ vincolo: `HudSession.connect()` esegue `teardownP2p()` e riparte da zero → usare la via **idempotente** (`requestAutoConnect()`) |
| R5 | Durata delle ricerche = valore di **"attesa attiva della riconnessione"** | ⚠️ da verificare che non ci sia un timeout fisso |
| R6 | Nulla in corso all'apertura dell'app -> **nessuna bolla** (niente residui) | ❌ da implementare |

---

## 4. Punti di codice (dove si decide il comportamento)

| Comportamento | File · funzione |
|---|---|
| Tocco su un'app in Home | `feature/hud/HudScreen.kt` · `onAppTap()` |
| Consenso proiezione + avvio app + streaming | `feature/stream/StreamLaunchActivity.kt` |
| Bolla AVVIA (mostra/spinner/sparisce) | `feature/stream/StartBubbleService.kt` · `watchStreamState()` |
| Connessione automatica di sessione | `core/hud/HudSession.kt` · `requestAutoConnect()` |
| Scoperta, link, sessione, riavvii con backoff | `core/hud/HudSession.kt`, `core/hud/HudStreamController.kt` |
| Cattura + encoder | `core/hud/ScreenCaptureService.kt`, `core/hud/HudEncoder.kt` |

---

## 5. Come si verifica (log)

```
adb logcat -s HudSession:* HudStreamController:* StartBubbleService:* TAG_UI:* ScreenCaptureService:*
```

Righe chiave da cercare:
- `Auto di sessione ATTIVA` — è partita la ricerca attiva
- `Bolla AVVIA premuta` — il tocco ha innescato il flusso
- `Mirroring attivo: rimuovo la bolla` — R1 rispettata
- `createVirtualDisplay fallita` / `SecurityException` — proiezione revocata (vedi TODO §10 «Fix 2»)

---

## 6. Vincoli che NON si toccano

- connessione (mDNS, P2P, socket, protocollo)
- trasmissione (VirtualDisplay, encoder, media projection)
- `app/libs/hudlib.aar`

Le modifiche ammesse riguardano **chi comanda** la connessione e **la visibilità della bolla**, non il funzionamento del collegamento.
