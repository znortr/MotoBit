package com.nos.motobitride.core.display

import android.content.Context
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import java.lang.reflect.Method

private const val TAG = "DisplayEngine"

/**
 * Motore display visto dall'app.
 *
 * L'implementazione vive nel modulo `:display`, raggiunta per **riflessione**
 * (nome + numero argomenti): togliendo il modulo, [NoOpDisplayEngine] tiene
 * l'app compilabile e la pagina Risoluzione si dichiara non disponibile.
 *
 * Contratto volutamente minimo, uno strumento per parametro: risoluzione (+
 * densità **opzionale**), orientamento, barra comandi, ripristino. L'oscuramento
 * non è qui: è un overlay dell'app (non tocca le impostazioni di sistema e non
 * viene trasmesso al TFT).
 *
 * ⚠️ Tutti i metodi possono bloccare: chiamarli da un thread di background.
 */
interface DisplayEngine {

    /** "permessi", "shizuku", "nessuna". */
    val lastVia: String

    /** Interruttore "Usa Shizuku": con false il motore non lo chiama mai. */
    fun setShizukuAllowed(allowed: Boolean)

    /** Risoluzione effettiva corrente ("WxH"), override compreso. */
    fun currentSize(): String

    /** Densità effettiva corrente (dpi), override compreso. */
    fun currentDensity(): Int

    /** Dimensioni fisiche del pannello, indipendenti da rotazione e override. */
    fun physicalSize(): String

    /** Stato per il ripristino: `densitàFisica|accel|userRotation|policy`. */
    fun readOriginals(): String

    /**
     * Applica risoluzione (+ densità se `density > 0`) e orientamento.
     * @param rotationMode `portrait` | `landscape`
     * @return true solo se la dimensione è **verificata** sul display.
     */
    fun apply(width: Int, height: Int, density: Int, rotationMode: String): Boolean

    fun setOrientation(mode: String): Boolean
    fun setNavBar(hidden: Boolean): Boolean

    /** `wm size reset` + `wm density reset` + ripristino rotazione/barra. */
    fun restore(originals: String): Boolean
}

/** Usato quando il modulo `:display` non è nella build. */
object NoOpDisplayEngine : DisplayEngine {
    override val lastVia: String get() = "nessuna"
    override fun setShizukuAllowed(allowed: Boolean) = Unit
    override fun currentSize(): String = ""
    override fun currentDensity(): Int = 0
    override fun physicalSize(): String = ""
    override fun readOriginals(): String = ""
    override fun apply(width: Int, height: Int, density: Int, rotationMode: String): Boolean = false
    override fun setOrientation(mode: String): Boolean = false
    override fun setNavBar(hidden: Boolean): Boolean = false
    override fun restore(originals: String): Boolean = false
}

/** Adattatore riflessivo: cerca i metodi per nome + numero di argomenti. */
private class ReflectiveDisplayEngine(private val impl: Any) : DisplayEngine {

    private fun call(name: String, vararg args: Any?): Any? = try {
        impl.javaClass.methods
            .firstOrNull { it.name == name && it.parameterTypes.size == args.size }
            ?.invoke(impl, *args)
    } catch (t: Throwable) {
        AppLog.w(TAG, "$name fallita: ${t.message}")
        null
    }

    private fun bool(name: String, vararg args: Any?): Boolean =
        call(name, *args) as? Boolean ?: false

    private fun str(name: String, vararg args: Any?): String =
        call(name, *args) as? String ?: ""

    private fun int(name: String, vararg args: Any?): Int =
        call(name, *args) as? Int ?: 0

    override val lastVia: String get() = str("getLastVia")

    override fun setShizukuAllowed(allowed: Boolean) {
        call("setShizukuAllowed", allowed)
    }

    override fun currentSize(): String = str("currentSize")
    override fun currentDensity(): Int = int("currentDensity")
    override fun physicalSize(): String = str("physicalSize")
    override fun readOriginals(): String = str("readOriginals")

    override fun apply(width: Int, height: Int, density: Int, rotationMode: String): Boolean =
        bool("apply", width, height, density, rotationMode)

    override fun setOrientation(mode: String): Boolean = bool("setOrientation", mode)
    override fun setNavBar(hidden: Boolean): Boolean = bool("setNavBar", hidden)
    override fun restore(originals: String): Boolean = bool("restore", originals)
}

/**
 * Punto d'accesso unico. Va inizializzato una volta (MainActivity).
 */
object DisplayBridge {

    private const val IMPL_CLASS = "com.nos.motobitride.display.DisplayEngineImpl"

    @Volatile
    private var engine: DisplayEngine? = null

    val current: DisplayEngine
        get() = engine ?: NoOpDisplayEngine

    fun init(context: Context) {
        if (engine != null) return
        engine = runCatching {
            val cls = Class.forName(IMPL_CLASS)
            val instance = cls.getConstructor(Context::class.java)
                .newInstance(context.applicationContext)
                ?: return@runCatching NoOpDisplayEngine as DisplayEngine
            ReflectiveDisplayEngine(instance) as DisplayEngine
        }.getOrElse {
            AppLog.i(TAG, "Modulo :display non presente (${it.javaClass.simpleName}) → motore vuoto")
            NoOpDisplayEngine
        }
        AppLog.i(TAG, "Motore display: ${engine?.javaClass?.simpleName ?: "nessuno"}")
        syncShizukuAllowed()
    }

    /** Allinea il motore all'interruttore "Usa Shizuku". */
    fun syncShizukuAllowed() {
        runCatching { engine?.setShizukuAllowed(AppSettings.shizukuEnabled.value) }
    }
}
