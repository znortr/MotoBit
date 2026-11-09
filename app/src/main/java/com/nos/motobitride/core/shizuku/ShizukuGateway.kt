package com.nos.motobitride.core.shizuku

import android.content.Context
import com.nos.motobitride.core.log.AppLog

private const val TAG = "Shizuku"

/** Stato del ponte verso Shizuku, come lo vede l'app. */
enum class ShizukuStatus {
    /** Shizuku non è installato sul telefono. */
    NOT_INSTALLED,

    /** Installato, ma il servizio non è avviato. */
    NOT_RUNNING,

    /** Avviato, ma l'app non ha il permesso. */
    NOT_AUTHORIZED,

    /** Tutto pronto: i comandi sono eseguibili. */
    READY,

    /** Disattivato dall'utente (switch nella pagina Risoluzione). */
    DISABLED;

    val isReady: Boolean get() = this == READY
}

/**
 * Contratto verso Shizuku.
 *
 * L'implementazione reale vive nel modulo `:shizuku` e viene raggiunta per
 * **riflessione**: così, togliendo il modulo, l'app compila e funziona con
 * [NoOpShizukuGateway] (tutte le funzioni che dipendono da Shizuku si
 * dichiarano non disponibili, senza crash).
 */
interface ShizukuGateway {
    fun currentStatus(): ShizukuStatus
    fun start()
    fun requestPermission()
    fun openShizukuApp(): Boolean
    fun openInstallPage(): Boolean
    /** Esegue un comando di shell. Vuoto se non disponibile o fallito. */
    fun runShell(command: String): String
}

/** Usato quando il modulo `:shizuku` non è nella build. */
object NoOpShizukuGateway : ShizukuGateway {
    override fun currentStatus(): ShizukuStatus = ShizukuStatus.NOT_INSTALLED
    override fun start() = Unit
    override fun requestPermission() = Unit
    override fun openShizukuApp(): Boolean = false
    override fun openInstallPage(): Boolean = false
    override fun runShell(command: String): String = ""
}

/**
 * Adattatore che parla con `ShizukuGatewayImpl` (modulo `:shizuku`) senza
 * averlo a compile-time: solo metodi e stringhe, nessun tipo condiviso.
 */
private class ReflectiveShizukuGateway(private val impl: Any) : ShizukuGateway {

    private fun callVoid(name: String) {
        runCatching { impl.javaClass.getMethod(name).invoke(impl) }
            .onFailure { AppLog.w(TAG, "$name fallita: ${it.message}") }
    }

    private fun callBoolean(name: String): Boolean =
        runCatching { impl.javaClass.getMethod(name).invoke(impl) as? Boolean ?: false }
            .getOrDefault(false)

    override fun currentStatus(): ShizukuStatus {
        val raw = runCatching {
            impl.javaClass.getMethod("currentStatus").invoke(impl) as? String
        }.getOrNull()
        return when (raw) {
            "READY" -> ShizukuStatus.READY
            "NOT_AUTHORIZED" -> ShizukuStatus.NOT_AUTHORIZED
            "NOT_RUNNING" -> ShizukuStatus.NOT_RUNNING
            "NOT_INSTALLED" -> ShizukuStatus.NOT_INSTALLED
            else -> ShizukuStatus.NOT_INSTALLED
        }
    }

    override fun start() = callVoid("start")
    override fun requestPermission() = callVoid("requestPermission")
    override fun openShizukuApp(): Boolean = callBoolean("openShizukuApp")
    override fun openInstallPage(): Boolean = callBoolean("openInstallPage")

    override fun runShell(command: String): String = runCatching {
        impl.javaClass.getMethod("runShell", String::class.java)
            .invoke(impl, command) as? String ?: ""
    }.getOrDefault("")
}

/**
 * Punto d'accesso unico. Va inizializzato una volta (MainActivity).
 * Se il modulo `:shizuku` non è compilato, resta il gateway "vuoto".
 */
object ShizukuBridge {

    private const val IMPL_CLASS = "com.nos.motobitride.shizuku.ShizukuGatewayImpl"

    @Volatile
    private var gateway: ShizukuGateway? = null

    val current: ShizukuGateway
        get() = gateway ?: NoOpShizukuGateway

    fun init(context: Context) {
        if (gateway != null) return
        gateway = create(context)
        gateway?.start()
        AppLog.i(TAG, "Ponte Shizuku: ${gateway?.javaClass?.simpleName ?: "nessuno"}")
    }

    private fun create(context: Context): ShizukuGateway = runCatching {
        val cls = Class.forName(IMPL_CLASS)
        val ctor = cls.getConstructor(Context::class.java)
        val instance = ctor.newInstance(context.applicationContext)
            ?: return@runCatching NoOpShizukuGateway as ShizukuGateway
        ReflectiveShizukuGateway(instance) as ShizukuGateway
    }.getOrElse {
        // Modulo assente: non è un errore, è la configurazione "senza Shizuku".
        AppLog.i(TAG, "Modulo :shizuku non presente (${it.javaClass.simpleName}) → gateway vuoto")
        NoOpShizukuGateway
    }
}
