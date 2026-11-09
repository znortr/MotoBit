package com.nos.motobitride.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import rikka.shizuku.Shizuku
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Ponte verso Shizuku — vive nel modulo `:shizuku`.
 *
 * **Nessun tipo dell'app qui dentro** (niente dipendenza da `:app`, niente
 * cicli): solo metodi e stringhe. L'app la istanzia per riflessione e legge lo
 * stato come stringa, traducendolo nel proprio enum.
 *
 * Stato restituito da [currentStatus]:
 *  - `NOT_INSTALLED`  Shizuku non è installato
 *  - `NOT_RUNNING`    installato ma il servizio non è avviato
 *  - `NOT_AUTHORIZED` avviato ma il permesso non è stato concesso
 *  - `READY`          avviato e autorizzato
 */
class ShizukuGatewayImpl(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Non serve mantenere listener: lo stato si legge a richiesta. */
    fun start() = Unit

    fun currentStatus(): String {
        if (!isInstalled()) return "NOT_INSTALLED"
        return try {
            if (!Shizuku.pingBinder()) {
                "NOT_RUNNING"
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                "NOT_AUTHORIZED"
            } else {
                "READY"
            }
        } catch (t: Throwable) {
            // Binder morto a metà lettura: equivale a servizio non attivo.
            "NOT_RUNNING"
        }
    }

    /** Mostra il dialogo di autorizzazione di Shizuku. */
    fun requestPermission() {
        runCatching {
            if (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        }
    }

    /** Apre l'app Shizuku (per avviarne il servizio). */
    fun openShizukuApp(): Boolean = runCatching {
        val intent = appContext.packageManager
            .getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        true
    }.getOrDefault(false)

    /** Apre la pagina di installazione di Shizuku. */
    fun openInstallPage(): Boolean = runCatching {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        true
    }.getOrDefault(false)

    /**
     * Esegue un comando come shell (uid shell/root via Shizuku).
     * Restituisce l'output; stringa vuota se il comando fallisce.
     */
    fun runShell(command: String): String {
        return try {
            // API 13.1.5: `newProcess` è privato in Shizuku → va invocato per
            // riflessione (unico modo con questa versione dell'API).
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

            // I due stream vanno svuotati IN PARALLELO: se un pipe si riempie
            // il processo resta bloccato e la lettura non termina mai.
            val stdout = CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            val stderr = CompletableFuture.supplyAsync {
                process.errorStream.bufferedReader().use { it.readText() }
            }
            process.waitFor()

            val out = runCatching { stdout.get(5, TimeUnit.SECONDS) }.getOrDefault("")
            val err = runCatching { stderr.get(5, TimeUnit.SECONDS) }.getOrDefault("")
            if (out.isNotBlank()) out.trim() else err.trim()
        } catch (t: Throwable) {
            ""
        }
    }

    private fun isInstalled(): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    }.getOrDefault(false)

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE = 4201
    }
}
