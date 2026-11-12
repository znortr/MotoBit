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

## Lingue: dove sono i testi e come aggiungerne una

Tutti i testi dell'interfaccia stanno in **`assets/texts/`**, una cartella per
lingua. **Nessun testo è nel codice.**

### Percorsi

```
app\src\main\assets\texts\
    languages.xml                  elenco delle lingue (una riga per lingua)
    it-IT\ui.xml                   comuni, Home, Garage, QR
    it-IT\settings.xml             Impostazioni (tutte le pagine)
    it-IT\hud.xml                  connessione e streaming
    en-GB\ui.xml
    en-GB\settings.xml
    en-GB\hud.xml
```

Percorso completo della cartella:

```
H:\userData\Desktop\APP-CARBIT-VOGE\motobitdev\Motobit_alfa_V1.0.4\motobit-ride\motobit-ride-android\app\src\main\assets\texts\
```

Codice che li legge (da non toccare per modificare i testi):

| File | Ruolo |
|---|---|
| `app\src\main\java\com\nos\motobitride\ui\i18n\AppTexts.kt` | ponte: `AppTexts.get(chiave, lingua)` |
| `app\src\main\java\com\nos\motobitride\ui\i18n\LanguageTexts.kt` | legge gli XML, cache, fallback |

---

### Cambiare un testo

1. apri il file della categoria giusta, per esempio `assets\texts\it-IT\settings.xml`
2. trova la riga con la chiave:

```xml
<text id="settings_cat_development_title">Opzioni Sviluppo</text>
```

3. cambia **solo il testo tra i tag**, mai `id="..."`
4. ricompila (vedi sotto)

> Se il testo contiene `&` va scritto `&amp;`; per `<` e `>` usa `&lt;` e `&gt;`.

---

### Aggiungere una lingua nuova (esempio: tedesco)

1. **Crea la cartella**:

```
app\src\main\assets\texts\de-DE\
```

2. **Copia i tre file** da una lingua esistente e traducili:

```
de-DE\ui.xml
de-DE\settings.xml
de-DE\hud.xml
```

3. **Aggiungi una riga** in `assets\texts\languages.xml`:

```xml
<language id="de-DE" name="Deutsch" flag="🇩🇪" file="de-DE" />
```

4. **Ricompila.** La lingua compare da sola nel selettore (Impostazioni → Generale → Lingua App).

Non serve toccare il codice Kotlin.

> Le chiavi (`id="..."`) non vanno tradotte né rinominate: devono restare
> identiche a quelle delle altre lingue. Se una chiave manca, l'app usa
> l'inglese e, se manca anche lì, mostra `[chiave]`.

---

### Controllo rapido (chiavi allineate)

Da PowerShell, nella cartella del progetto, questo conta le chiavi per lingua:

```powershell
(Select-String -Path app\src\main\assets\texts\it-IT\*.xml -Pattern 'id="([^"]+)"' -AllMatches).Matches |
    ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique | Measure-Object
```

I numeri devono essere **uguali** per tutte le lingue.

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
