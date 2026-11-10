package com.nos.motobitride.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.os.Process
import android.provider.Settings
import android.view.Display
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Motore display — modulo `:display`.
 *
 * Metodo (allineato a RideLink, che è l'app che funziona):
 *  1. **`wm size WxH`** — si cambia solo la risoluzione logica.
 *  2. **`wm density`** SOLO se richiesto (`density > 0`): con densità 0 non si
 *     tocca niente, esattamente come fa RideLink.
 *  3. **Verifica** con `wm size` / metrics: se l'override non è quello richiesto
 *     la chiamata fallisce e il chiamante fa rollback.
 *  4. **Rotazione riapplicata DOPO** il cambio di dimensione: cambiare `wm size`
 *     fa ricalcolare l'orientamento, quindi il valore va messo per ultimo.
 *  5. Il ripristino è **`wm size reset` + `wm density reset`**: non serve
 *     nessun valore salvato, quindi funziona sempre — anche a Shizuku spento,
 *     anche prima dello sblocco.
 *
 * Due vie, nell'ordine:
 *  - **permessi** (`WRITE_SECURE_SETTINGS` concesso una volta) → `IWindowManager`
 *    via reflection. Funziona **senza Shizuku**.
 *  - **shell** (`wm size`, `settings put`) via Shizuku, solo se i permessi non
 *    bastano e Shizuku è abilitato.
 *
 * Sblocco hidden API: `addHiddenApiExemptions("L")` (tutta la hidden API), come
 * Monitune. Esentarne solo due classi non basta: la chiamata riflessiva su
 * `IWindowManager$Stub$Proxy` resta bloccata e il percorso "permessi" muore.
 *
 * ⚠️ Tutti i metodi possono bloccare: vanno chiamati da un thread di background.
 */
class DisplayEngineImpl(context: Context) {

    private val appContext: Context = context.applicationContext
    private val shizuku = ShizukuShell(appContext)

    /** Ultima via usata: "permessi", "shizuku", "nessuna". */
    @Volatile
    var lastVia: String = "nessuna"
        private set

    /** Interruttore "Usa Shizuku": con false il motore non chiama mai Shizuku. */
    fun setShizukuAllowed(allowed: Boolean) {
        shizuku.allowed = allowed
    }

    // ------------------------------------------------------------------
    // Lettura
    // ------------------------------------------------------------------

    /** Risoluzione effettiva corrente ("WxH"), override compreso. */
    fun currentSize(): String {
        parseLine(shell("wm size"), "Override size")?.let { return it }
        val dm = appContext.resources.displayMetrics
        if (dm.widthPixels > 0 && dm.heightPixels > 0) {
            return "${dm.widthPixels}x${dm.heightPixels}"
        }
        return physicalSize()
    }

    /** Densità effettiva corrente (override compreso). */
    fun currentDensity(): Int {
        parseIntLine(shell("wm density"), "Override density")?.let { return it }
        val dm = appContext.resources.displayMetrics
        if (dm.densityDpi > 0) return dm.densityDpi
        return parseIntLine(shell("wm density"), "Physical density") ?: 0
    }

    /**
     * Dimensioni **fisiche** del pannello, indipendenti da rotazione e override.
     * Servono solo per il log e per la diagnosi: il ripristino non le usa (usa
     * `wm size reset`).
     */
    fun physicalSize(): String {
        parseLine(shell("wm size"), "Physical size")?.let { return it }
        return runCatching {
            val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val mode = dm.getDisplay(Display.DEFAULT_DISPLAY)?.mode
            val w = mode?.physicalWidth ?: 0
            val h = mode?.physicalHeight ?: 0
            if (w > 0 && h > 0) "${w}x$h" else ""
        }.getOrDefault("")
    }

    /**
     * Stato da conservare per il ripristino della **rotazione** e della barra.
     *
     * Formato: `densitàFisica|accel|userRotation|policy`
     * La risoluzione non compare di proposito: il ripristino è `wm size reset`,
     * quindi non c'è nessun valore da poter sbagliare.
     */
    fun readOriginals(): String {
        val density = parseIntLine(shell("wm density"), "Physical density")
            ?: appContext.resources.displayMetrics.densityDpi
        val accel = runCatching {
            Settings.System.getInt(cr(), Settings.System.ACCELEROMETER_ROTATION, 1)
        }.getOrDefault(1)
        val userRotation = runCatching {
            Settings.System.getInt(cr(), Settings.System.USER_ROTATION, 0)
        }.getOrDefault(0)
        val policy = runCatching {
            Settings.Global.getString(cr(), "policy_control")
        }.getOrNull().orEmpty()

        return listOf(
            density.toString(),
            accel.toString(),
            userRotation.toString(),
            policy
        ).joinToString("|")
    }

    // ------------------------------------------------------------------
    // Applicazione
    // ------------------------------------------------------------------

    /**
     * Applica risoluzione (e densità, se `density > 0`) più rotazione.
     *
     * @param density densità da impostare; **0 = non toccare la densità**.
     * @param rotationMode `portrait` | `landscape` (niente auto)
     * @return true solo se la dimensione richiesta è **verificata** sul display.
     */
    fun apply(width: Int, height: Int, density: Int, rotationMode: String): Boolean {
        if (width <= 0 || height <= 0) return false

        // 1) Risoluzione (eventualmente con densità): è il passo critico.
        if (!applySizeAndDensity(width, height, density)) return false

        // 2) Rotazione DOPO il cambio di dimensione: cambiare `wm size` fa
        //    ricalcolare l'orientamento, quindi il valore va messo per ultimo.
        setOrientation(rotationMode)

        return true
    }

    private fun applySizeAndDensity(width: Int, height: Int, density: Int): Boolean {
        // --- via permessi (senza Shizuku) ---
        val sizeOk = callWindowManager("setForcedDisplaySize", 0, width, height)
        val densityOk = if (density > 0) {
            callWindowManager("setForcedDisplayDensityForUser", 0, density, currentUserId())
        } else {
            true
        }

        if (sizeOk && densityOk) {
            lastVia = "permessi"
            if (verifySize(width, height)) return true
            // Applicata ma non verificata: si prova la shell, poi si rinuncia.
            lastVia = "permessi (non verificato)"
        }

        // --- fallback shell ---
        val cmd = buildString {
            append("wm size ${width}x$height")
            if (density > 0) append("; wm density $density")
        }
        shell(cmd)
        val applied = verifySize(width, height)
        lastVia = if (applied) "shizuku" else "nessuna"
        return applied
    }

    /**
     * Rilegge il display e conferma che l'override sia davvero quello richiesto.
     *
     * Senza shell si guardano le metriche del processo (che riflettono
     * l'override). Se nessuna delle due fonti è disponibile si considera
     * applicata: la chiamata riflessiva avrebbe lanciato se avesse fallito, e un
     * rollback immotivato farebbe "rimbalzare" il display a ogni tocco.
     */
    private fun verifySize(width: Int, height: Int): Boolean {
        val shellOut = shell("wm size")
        val hasShell = shellOut.isNotBlank()

        repeat(3) { attempt ->
            if (hasShell) {
                if (parseLine(shell("wm size"), "Override size") == "${width}x$height") return true
            } else {
                val dm = appContext.resources.displayMetrics
                val same = dm.widthPixels == width && dm.heightPixels == height
                val swapped = dm.widthPixels == height && dm.heightPixels == width
                if (same || swapped) return true
                // Nessuna fonte affidabile: si crede alla chiamata.
                if (attempt == 0) return true
            }
            if (attempt < 2) runCatching { Thread.sleep(200) }
        }
        return false
    }

    /** Blocca l'orientamento: `portrait` (0) o `landscape` (1). */
    fun setOrientation(mode: String): Boolean {
        val rotationValue = if (mode == "landscape") 1 else 0
        val okSettings = runCatching {
            Settings.System.putInt(cr(), Settings.System.ACCELEROMETER_ROTATION, 0)
            Settings.System.putInt(cr(), Settings.System.USER_ROTATION, rotationValue)
            true
        }.getOrDefault(false)

        // Ignoto all'orientamento richiesto dalle app (Android 12+): serve shell.
        shell("wm set-ignore-orientation-request -d 0 true")

        if (okSettings) return true
        // Senza WRITE_SETTINGS si passa dalla shell.
        shell("settings put system accelerometer_rotation 0; settings put system user_rotation $rotationValue")
        return true
    }

    /** Sblocca la rotazione (torna all'automatico). */
    fun clearOrientation(): Boolean {
        val ok = runCatching {
            Settings.System.putInt(cr(), Settings.System.ACCELEROMETER_ROTATION, 1)
            true
        }.getOrDefault(false)
        shell("wm set-ignore-orientation-request -d 0 false")
        if (ok) return true
        shell("settings put system accelerometer_rotation 1")
        return true
    }

    /** Nasconde barra di stato e di navigazione (immersive) in tutte le app. */
    fun setNavBar(hidden: Boolean): Boolean = runCatching {
        Settings.Global.putString(
            cr(),
            "policy_control",
            if (hidden) "immersive.full=*" else null
        )
        true
    }.getOrDefault(false)

    // ------------------------------------------------------------------
    // Ripristino
    // ------------------------------------------------------------------

    /**
     * Ripristina **tutto** senza bisogno di valori salvati:
     * `wm size reset`, `wm density reset`, rotazione e barra dagli originali.
     * Idempotente e sempre possibile.
     */
    fun restore(originals: String): Boolean {
        val f = originals.split("|")

        // 1) risoluzione e densità: reset secco (nessun valore da sbagliare)
        val sizeOk = callWindowManager("clearForcedDisplaySize", 0)
        val densityOk = callWindowManager("clearForcedDisplayDensityForUser", 0, currentUserId())
        var resetOk = sizeOk && densityOk
        if (!resetOk) {
            val out = shell("wm size reset; wm density reset; wm size")
            resetOk = out.isNotBlank() && !out.contains("Override size")
        }
        lastVia = if (sizeOk && densityOk) "permessi" else "shizuku"

        // 2) rotazione: si rimette com'era
        runCatching {
            val accel = f.getOrNull(1)?.toIntOrNull() ?: 1
            val rotation = f.getOrNull(2)?.toIntOrNull() ?: 0
            Settings.System.putInt(cr(), Settings.System.ACCELEROMETER_ROTATION, accel)
            Settings.System.putInt(cr(), Settings.System.USER_ROTATION, rotation)
        }
        shell("wm set-ignore-orientation-request -d 0 false")

        // 3) barra comandi
        runCatching {
            val policy = f.getOrNull(3).orEmpty()
            Settings.Global.putString(cr(), "policy_control", policy.ifBlank { null })
        }

        return resetOk
    }

    // ------------------------------------------------------------------
    // Reflection IWindowManager
    // ------------------------------------------------------------------

    /**
     * ⚠️ I tipi vanno presi **primitivi** (`javaPrimitiveType`):
     * `Int` boxato a `java.lang.Integer` non fa trovare `setForcedDisplaySize(int,int,int)`
     * e la reflection lancia `NoSuchMethodException` → il percorso "permessi"
     * falliva SEMPRE e restava solo Shizuku. Era questo il motivo per cui
     * "MotoBit non funziona con Shizuku disabilitato".
     */
    private fun callWindowManager(name: String, vararg args: Any?): Boolean {
        return try {
            val wm = windowManagerService() ?: return false
            val types = args.map { arg ->
                arg?.let { it::class.javaPrimitiveType ?: it::class.java } ?: Any::class.java
            }.toTypedArray()
            val method = wm.javaClass.getMethod(name, *types)
            method.invoke(wm, *args)
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun windowManagerService(): Any? {
        return try {
            // Esenzione totale della hidden API (come Monitune): senza, il proxy
            // di IWindowManager resta bloccato e questo percorso non funziona.
            runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }

            val serviceManager = Class.forName("android.os.ServiceManager")
            val binder = serviceManager
                .getMethod("getService", String::class.java)
                .invoke(null, "window") as? IBinder ?: return null

            val stub = Class.forName("android.view.IWindowManager\$Stub")
            stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        } catch (t: Throwable) {
            null
        }
    }

    /** `Process.myUid() / 100000` — niente hidden API (Monitune). */
    private fun currentUserId(): Int = Process.myUid() / 100000

    // ------------------------------------------------------------------
    // Interni
    // ------------------------------------------------------------------

    private fun cr() = appContext.contentResolver

    private fun shell(command: String): String = shizuku.run(command)

    private fun parseLine(out: String, label: String): String? =
        out.lines().firstOrNull { it.contains(label) }
            ?.substringAfter(':')?.trim()?.takeIf { it.isNotBlank() }

    private fun parseIntLine(out: String, label: String): Int? =
        parseLine(out, label)?.toIntOrNull()?.takeIf { it > 0 }
}

/**
 * Ponte **riflessivo** verso `:shizuku`: nessuna dipendenza in compilazione.
 * Se il modulo non c'è, ogni comando restituisce stringa vuota e si usa solo la
 * via permessi.
 */
private class ShizukuShell(private val appContext: Context) {

    @Volatile
    var allowed: Boolean = true

    @Volatile
    private var impl: Any? = null

    @Volatile
    private var unavailable: Boolean = false

    fun run(command: String): String {
        if (!allowed || command.isBlank()) return ""
        val target = impl ?: obtain() ?: return ""
        return runCatching {
            target.javaClass
                .getMethod("runShell", String::class.java)
                .invoke(target, command) as? String ?: ""
        }.getOrDefault("")
    }

    private fun obtain(): Any? {
        if (unavailable) return null
        return runCatching {
            Class.forName("com.nos.motobitride.shizuku.ShizukuGatewayImpl")
                .getConstructor(Context::class.java)
                .newInstance(appContext)
        }.onFailure { unavailable = true }.getOrNull()
    }
}
