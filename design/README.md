# design/

---

## Come cambiare l'icona dell'app

1. Prepara un PNG **quadrato 432×432** con il logo centrato.
   Il logo deve stare entro **264×264 px** dal centro, altrimenti Android lo taglia sui bordi.
2. Salvalo come **`ic_launcher_foreground.png`**.
3. Copialo in:

```
H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev\Motobit_alfa_V1.0.4\motobit-ride\motobit-ride-android\app\src\main\res\drawable\
```

sovrascrivendo il file esistente (stesso nome = stesso riferimento nel codice).

In pratica, per cambiare l'icona
1.
Prepara un PNG 432×432 con il logo centrato (entro 264×264 px dal centro)
2.
Salvalo come  ic_launcher_foreground.png
3.
Copialo in app/src/main/res/drawable/ (sovrascrivi)

4. Ricompila (vedi sotto).

---

## Come ricompilare

### 1) Cambia la versione

Apri:

```
H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev\Motobit_alfa_V1.0.4\motobit-ride\motobit-ride-android\gradle.properties
```

e modifica l'ultima riga:

```properties
MOTO_VERSION=1.2.6
```

> Il `versionCode` si calcola da solo: `X.Y.Z` → `X*1000 + Y*100 + Z` (1.2.6 → 1206).

### 2) Compila

PowerShell:

```powershell
cd H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev\Motobit_alfa_V1.0.4\motobit-ride\motobit-ride-android
.\gradlew.bat assembleDebug
```

### 3) APK generato

```
H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev\Motobit_alfa_V1.0.4\motobit-ride\motobit-ride-android\app\build\outputs\apk\debug\app-debug.apk
```

### 4) Installa sul telefono (opzionale)

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

> In alternativa, da Android Studio: **Run** (`Shift + F10`) ricompila e installa in un colpo solo.

---

## Contenuto di questa cartella

| File | Cosa è |
|---|---|
| `README.md` | questo file |

L'unica immagine dell'icona è quella in
`app\src\main\res\drawable\ic_launcher_foreground.png`.
