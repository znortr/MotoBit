# HANDOFF — Motobit_alfa_V1.0.4 (incolla come primo messaggio in una nuova chat)

> Documento di passaggio di consegne tra sessioni. Contiene regole, percorsi, stato reale verificato e prossimi passi.
> Creato il 2026-10-29 alla chiusura della sessione che ha chiuso V1.0.3 e migrato alla root piatta.

---

## 1. REGOLE DI INGAGGIO — VINCOLANTI

1. **MAI** chiedere di chiudere Android Studio, il progetto o l'IDE: chiudere = perdere la sessione. Ogni operazione deve essere eseguibile a IDE aperto.
2. Nessuna scrittura/modifica/cancellazione di file senza un **implementa / procedi / fai** esplicito dell'utente.
3. Ogni turno: dichiarare la modalità (**ANALISI** o **MODIFICA**), scrivere in 1 riga cosa si sta per fare e attendere conferma.
4. Non compilare l'APK se non richiesto. Quando si compila, avvisare prima.
5. Modifiche chirurgiche: mai riscrivere un file intero se basta una porzione.
6. Prima di modifiche rischiose: proporre uno snapshot (backup) e attendere.
7. Ripristino senza spostare cartelle: si annullano solo le modifiche, file per file. Rinominare, mai cancellare.
8. **Nelle scelte multiple: segnare l'opzione consigliata con ⭐ [CONSIGLIATA]**.

---

## 2. PERCORSI (mappa aggiornata — root PIATTA)

| Cosa | Percorso |
|---|---|
| **Progetto di lavoro (V1.0.4, root piatta)** | `H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev\Motobit_alfa_V1.0.4\motobit-ride\motobit-ride-android` |
| Libreria Go di riferimento (NON modificare) | `...\Motobit_alfa_V1.0.4\motobit-ride\motobit-ride-lib` |
| Archivio storico V1.0.3 (congelato, non toccare) | `...\motobitdev\Motobit_alfa_V1.0.3\` (contiene ancora il vecchio nesting a 3 livelli) |
| Backup manuali | `...\motobitdev\dev_backup\Motobit_alfa_Vx.y.z.zip` |
| APK archiviati (gitignored) | `<progetto>\_versions\Motobit_alfa_Vx.y.z.apk` |
| App di riferimento (risoluzione schermo) | `H:\userData\Desktop\APP-CARBIT-VOGE\app definitive\RideLink-beta-1.7` |

Regola: il progetto Gradle è la cartella con `settings.gradle.kts` + `gradlew.bat`. Aprire QUELLA, non le superiori.
⚠️ La copia vecchia `test\Motobit-Ride\...` contiene Shizuku + bolla flottante: **non** è la base di lavoro.

---

## 3. BUILD

```powershell
Remove-Item env:\ANDROID_PREFS_ROOT -ErrorAction SilentlyContinue
$env:ANDROID_HOME="C:\Users\Sandro\AppData\Local\Android\Sdk"
.\gradlew.bat assembleDebug
```
Output: `app\build\outputs\apk\debug\app-debug.apk` (~102 MB).
Stack: Kotlin 2.0.21 · minSdk 29 · compileSdk/targetSdk 36 · Compose Material3 · Navigation 2.9.6.
AAR protocollo: `app/libs/hudlib.aar` (19 MB, gitignored → `git add -f` se serve).

---

## 4. VERSIONING — SORGENTE UNICA

- **`MOTO_VERSION` in `gradle.properties`** è l'unica riga da cambiare (attualmente `1.0.4-alfa`).
- In `app/build.gradle.kts`:
  ```kotlin
  val motoVer = providers.gradleProperty("MOTO_VERSION").get()
  val (vMaj, vMin, vPat) = Regex("""(\d+)\.(\d+)\.(\d+)""").find(motoVer)!!.destructured
  versionName = motoVer
  versionCode = vMaj.toInt() * 1000 + vMin.toInt() * 100 + vPat.toInt()
  ```
- Mappatura: 1.0.3→1003 · 1.0.4→**1004** · 1.1.0→1100 · 2.0.0→2000 (verificato con `aapt2` sull'APK).
- Backup **non** obbligatorio a ogni versione: si fa quando l'utente lo ritiene necessario; il changelog scrive `Backup: SI/NO`.

Comando zip di backup (in testa a `CHANGELOG.md`, cambia solo `$v`):
```powershell
$v = 'Motobit_alfa_V1.0.4'
tar -a -c -f "H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev\dev_backup\$v.zip" -C "H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev" --exclude=*/build --exclude=*/build/* --exclude=*/.gradle --exclude=*/.gradle/* --exclude=*/.kotlin --exclude=*/.idea --exclude=*/.idea/* "$v"
```

---

## 5. STATO REALE AL PASSAGGIO DI CONSEGNE

**V1.0.3 — CHIUSA**
- Tag git `Motobit_alfa_V1.0.3` sul commit `0d4d523` (baseline) nel repo dell'albero annidato.
- Backup: `dev_backup\Motobit_alfa_V1.0.3.zip` (182,5 MB, verificato: 278 file, esclusi build/.gradle).
- APK: `_versions\Motobit_alfa_V1.0.3.apk` (101,9 MB, versionCode 1003).
- ⚠️ Quell'APK **non** contiene il fix mDNS (build precedente al fix): il fix debutta in 1.0.4.

**V1.0.4 — IN CORSO** (questo albero)
- Git: branch `master`, HEAD `713ca59` (root piatta + MOTO_VERSION 1.0.4-alfa + changelog), ahead 9 su `origin/master` (nessun push).
- Build: `assembleDebug` **SUCCESSFUL**, 0 warning, APK 101,9 MB, `aapt2` → versionCode **1004** / versionName **1.0.4-alfa**.
- API archiviata V1.0.3 presente anche qui in `_versions\` (ereditata dalla copia).

---

## 6. FATTO NEL PASSAGGIO — fix mDNS (commit `5200246`)

`core/hud/HudStreamController.kt`: eliminati i due warning di deprecazione
(`NsdManager.resolveService`, `NsdServiceInfo.host`).
- API 34+: `registerServiceInfoCallback` + `hostAddresses` (IPv4 preferito, come il vecchio `host`).
- API 29..33: `resolveService` legacy, isolato in `@Suppress("DEPRECATION")`.
- **Firme reali nell'SDK 36** (verificate con `javap` su android-34/35/36):
  `onServiceUpdated(NsdServiceInfo)`, `onServiceLost()` (abstract), `onServiceInfoCallbackUnregistered()`,
  `onServiceInfoCallbackRegistrationFailed(int)`; `hostAddresses` è `List<InetAddress>` (**non** array).
  `onServiceInfoUpdated` NON esiste.
- Comportamento invariato: priorità `attrs["ip"]` → `hostAddress`, stessi log, stesse guardie.

---

## 7. PROTOCOLLO HUD — FATTI VERIFICATI (non riaprire)

- `MuxSource{NoSignal, Live}`; `StaticSignal` vuoto ⇒ nessun video di test.
- MediaCtrl: Init=16, ScreenConf=96, Chk/VideoStart=112, Ping=64 (ack 17/97/113/65).
- `MediaCtrlInit` = risoluzione binaria: width u16 LE [0:2], height u16 LE [2:4], allineati a 16. L'HU negozia **592x752**.
- `PushFrame` scarta frame finché non arriva un IDR ⇒ `KEY_I_FRAME_INTERVAL = 0`.
- Polling media stream ~30 Hz su :10920 ⇒ serve flusso continuo.
- Porte: 10920 stream · 10921 media control · 10922 PXC. mDNS `_EasyConn._tcp`, pkg `com.cfmoto.cfmotointernational`.
- L'handshake non comunica la risoluzione del telefono: la geometria la decide l'HU.
- Eventi HU→telefono via `OnEvent(source, cmd, payload)`: PXC=2, MediaControl=3.
- La libreria espone `startSessionWithSocketFd(fd)` / `InitStreamCmdWithConn(conn)`: il link è responsabilità dell'app.

---

## 8. DECISIONI CHIUSE (non riaprire)

1. Link: **Wi-Fi Direct** (WifiNetworkSuggestion/Specifier scartate: bloccavano i dati mobili).
2. TFT: adatta il video preservando il rapporto; per riempirlo serve rapporto **0,787** (=592/752) ⇒ target 1080x1372.
3. Schermo virtuale: **scartato** (TFT non touch, telefono in tasca).
4. Risoluzione: replicare RideLink (wm size via Shizuku + bolla flottante) — **non ancora** in questo albero.
5. Allineamento 16: ogni dimensione che finisce nell'encoder deve essere multipla di 16.
6. **Root piatta** da V1.0.4: `Motobit_alfa_Vx\motobit-ride\motobit-ride-android`.
7. Git: remoto **grafted/shallow** → **non fare push** senza valutare il remoto.

---

## 9. PROSSIMI PASSI PROPOSTI

- ⭐ [CONSIGLIATA] Aggiornare i percorsi in `motobitdev\prompt.txt` (sez. 2) alla root piatta, così ogni nuova sessione parte corretta.
- Archiviare `_versions\Motobit_alfa_V1.0.4.apk` (regolamento sez. 4).
- Funzionale V1.0.4: link P2P (robustezza/retry) · encoder/mirroring (qualità, sospensione/risveglio) · HUD/QR · impostazioni · log in-app · `restore.cmd`.

---

## 10. COMANDI DI SICUREZZA

```powershell
git status --short --branch        # stato
git log --oneline -5               # storia
git checkout Motobit_alfa_V1.0.3 -- .   # ripristino V1.0.3 (a IDE aperto)
git reset --hard 713ca59           # torna a questo handoff
```
