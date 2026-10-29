# CHANGELOG — Motobit-Ride (Android)

Formato: voci dalla più recente alla più vecchia. Backup: non obbligatorio a ogni versione, solo quando serve (lo zip va in `motobitdev\dev_backup\`).

---

## 📋 INCOLLA NEL TERMINALE PER CREARE LO ZIP DI BACKUP

> PowerShell (Terminal di Android Studio va bene). **Cambia solo `$v`** — il resto resta identico per sempre.

```powershell
$v = 'Motobit_alfa_V1.0.3'   # <-- UNICA RIGA DA CAMBIARE a ogni versione ($v = 'Motobit_alfa_V1.0.4', ...)
tar -a -c -f "H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev\dev_backup\$v.zip" -C "H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev" --exclude=*/build --exclude=*/build/* --exclude=*/.gradle --exclude=*/.gradle/* --exclude=*/.kotlin --exclude=*/.idea --exclude=*/.idea/* "$v"
```

Verifica (stessa logica, un solo punto da cambiare):

```powershell
$v = 'Motobit_alfa_V1.0.3'
Get-Item "H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev\dev_backup\$v.zip" | Select-Object Name,@{n='MB';e={[math]::Round($_.Length/1MB,1)}}; tar -tf "H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev\dev_backup\$v.zip" | Select-String -Pattern "settings.gradle.kts|hudlib.aar|local.properties|git/HEAD"
```

Atteso: `0` voci `*/build/*` e `*/.gradle/*` dentro lo zip; `settings.gradle.kts`, `hudlib.aar`, `local.properties`, `.git/HEAD` tutti presenti.

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
- Aggiunto: `CHANGELOG.md`.
- Cambiato: `applicationId`/namespace `com.nos.ridedaemon` → `com.nos.motobitride`.
- Cambiato: `versionCode` 1 → 1003, `versionName` "1.0" → "1.0.3-alfa".
- Cambiato: baseline git creata (commit `0d4d523`, tag annotato `Motobit_alfa_V1.0.3`), `app/libs/hudlib.aar` tracciato con `git add -f`.
- Risolto: `core/hud/HudStreamController.kt.bak` spostato in `docs/legacy/` (git mv, storia preservata); `.gitignore` ora ignora `*.bak` e `*.orig`.
- Noto: remoto git "grafted" (storia shallow): non fare push senza valutazione del remoto.
- Noto: TFT adatta il video preservando il rapporto; per riempirlo serve rapporto 0,787 (target 1080x1372). Schermo virtuale scartato. Risoluzione via Shizuku/bolla: non presente in questa versione (esiste solo nella copia `test\`).

Backup: SI → `dev_backup\Motobit_alfa_V1.0.3.zip` (182,5 MB, creato 2026-10-29 con `tar`; esclusi `build`, `.gradle`, `.kotlin`, `.idea`; 278 file / 212,6 MB non compressi; include `motobit-ride-lib`, `hudlib.aar`, `.git` e `local.properties`)
APK: nessuno · non compilato in questa sessione · testato: no
Ripristino: `git checkout Motobit_alfa_V1.0.3 -- .` a IDE aperto (oppure `git reset --hard 0d4d523`)
