# CHANGELOG — Motobit-Ride (Android)

Formato: voci dalla più recente alla più vecchia. Backup: non obbligatorio a ogni versione, solo quando serve (lo zip va in `motobitdev\dev_backup\`).

---

## 📋 INCOLLA NEL TERMINALE PER CREARE LO ZIP DI BACKUP

> PowerShell (Terminal di Android Studio va bene). **Cambia solo `$v`** — il resto resta identico per sempre.

```powershell
$v   = 'Motobit_alfa_V1.0.7'   # <-- UNICA RIGA DA CAMBIARE a ogni versione ($v = 'Motobit_alfa_V1.0.7', ...)
$dev = 'H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev'
$zip = Join-Path $dev "dev_backup\$v.zip"
tar -a -c -f $zip -C $dev --exclude=*/build --exclude=*/build/* --exclude=*/.gradle --exclude=*/.gradle/* --exclude=*/.kotlin --exclude=*/.idea --exclude=*/.idea/* $v
```

> Se dopo il paste il prompt scende su `>>`, la shell sta aspettando una quote chiusa: `Ctrl+C` (una o due volte), poi rilanciare. Incollare riga per riga, un solo Invio.

Verifica (stessa logica, un solo punto da cambiare):

```powershell
$v   = 'Motobit_alfa_V1.0.7'
$zip = Join-Path 'H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev\dev_backup' "$v.zip"
Get-Item $zip | Select-Object Name,@{n='MB';e={[math]::Round($_.Length/1MB,1)}}
tar -tf $zip | Select-String -Pattern "settings.gradle.kts|hudlib.aar|local.properties|git/HEAD"
```

Atteso: `0` voci `*/build/*` e `*/.gradle/*` dentro lo zip; `settings.gradle.kts`, `hudlib.aar`, `local.properties`, `.git/HEAD` tutti presenti.

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
