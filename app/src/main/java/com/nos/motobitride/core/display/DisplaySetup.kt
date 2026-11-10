package com.nos.motobitride.core.display

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.shizuku.ShizukuBridge
import com.nos.motobitride.core.settings.AppSettings

private const val TAG = "DisplaySetup"

/**
 * Preparazione **una volta sola** di MotoBit.
 *
 * Il display (risoluzione, densità, rotazione, barra, luminosità) si può
 * cambiare solo con due permessi speciali che Android non concede dalle
 * impostazioni normali. Li concediamo **dall'app**, usando Shizuku una volta:
 *
 * ```
 * pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS
 * appops set <pkg> WRITE_SETTINGS allow
 * ```
 *
 * Da quel momento Shizuku **non serve più**: i permessi sopravvivono ai
 * riavvii e il ripristino è sempre possibile (anche a Shizuku spento).
 * Resta comunque installato come "backup" per riconcederli se servisse.
 */
object DisplaySetup {

    /** Permesso per risoluzione/densità/barra (impostazioni "protette"). */
    const val PERM_SECURE = "android.permission.WRITE_SECURE_SETTINGS"

    /** Permesso per rotazione/luminosità (impostazioni normali). */
    const val PERM_WRITE = "android.permission.WRITE_SETTINGS"

    /** Vero se possiamo cambiare risoluzione/densità/barra. */
    fun hasSecure(context: Context): Boolean = runCatching {
        context.checkSelfPermission(PERM_SECURE) == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** Vero se possiamo cambiare rotazione/luminosità. */
    fun hasWrite(context: Context): Boolean = runCatching {
        Settings.System.canWrite(context)
    }.getOrDefault(false)

    /** Vero se **entrambi** sono attivi: da qui in poi Shizuku non serve. */
    fun isReady(context: Context): Boolean = hasSecure(context) && hasWrite(context)

    /**
     * Esegue i due comandi tramite Shizuku e verifica il risultato.
     * @return true se al termine entrambi i permessi risultano concessi.
     */
    fun run(context: Context): Boolean {
        val pkg = context.packageName
        AppLog.i(TAG, "Preparazione MotoBit per il display (una volta sola)")

        // Già pronti: da qui in poi Shizuku NON serve più (A4, F3).
        // Questo controllo viene PRIMA dell'interruttore: se i permessi ci sono
        // la preparazione è conclusa e Shizuku non va interrogato (A3 → i).
        if (isReady(context)) {
            AppLog.i(TAG, "Permessi display già presenti: nessun comando necessario")
            return true
        }

        // Interruttore "Usa Shizuku" OFF: se i permessi non ci sono non possiamo
        // concederli, e NON dobbiamo toccare Shizuku. Si dichiara il perché.
        if (!AppSettings.shizukuEnabled.value) {
            AppLog.w(
                TAG,
                "Shizuku disattivato dall'utente: la preparazione una-tantum non è possibile " +
                        "finché non viene riattivato (una volta sola, poi non serve più)"
            )
            return false
        }

        val gateway = ShizukuBridge.current

        if (!hasSecure(context)) {
            val out = gateway.runShell("pm grant $pkg $PERM_SECURE")
            AppLog.i(TAG, "pm grant WRITE_SECURE_SETTINGS → ${out.ifBlank { "ok" }}")
        }
        if (!hasWrite(context)) {
            val out = gateway.runShell("appops set $pkg WRITE_SETTINGS allow")
            AppLog.i(TAG, "appops WRITE_SETTINGS allow → ${out.ifBlank { "ok" }}")
        }

        val ready = isReady(context)
        AppLog.i(
            TAG,
            "Preparazione: secure=${hasSecure(context)} write=${hasWrite(context)} → ready=$ready"
        )
        return ready
    }

    /** Revoca i permessi (utile per le prove). */
    fun revoke(context: Context): Boolean {
        val pkg = context.packageName
        val gateway = ShizukuBridge.current
        gateway.runShell("pm revoke $pkg $PERM_SECURE")
        gateway.runShell("appops set $pkg WRITE_SETTINGS deny")
        return !isReady(context)
    }
}
