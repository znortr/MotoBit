package com.nos.motobitride.core.display

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nos.motobitride.core.log.AppLog

private const val TAG = "BootRestore"

/**
 * Copia degli originali nella memoria **non cifrata** (device protected).
 *
 * Serve perché prima dello sblocco l'app non può leggere i propri dati (sono
 * cifrati): solo la device-protected storage è accessibile.
 *
 * Nota di progetto: qui si salvano **solo** gli originali e lo stato
 * dell'override, e sono un'unica verità condivisa con [DisplayMaster]. Prima
 * esistevano due copie che potevano divergere (il flag restava "attivo" dopo un
 * ripristino normale e al riavvio successivo partiva un ripristino inutile).
 */
object DisplayDeviceStore {

    private const val PREFS = "motobit_device_prefs"
    private const val KEY_ORIGINALS = "display_originals"
    private const val KEY_OVERRIDE = "display_override"

    private fun prefs(context: Context) = context
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Salva gli originali **senza** toccare lo stato dell'override. */
    fun saveOriginals(context: Context, originals: String) {
        prefs(context).edit()
            .putString(KEY_ORIGINALS, originals)
            .apply()
    }

    fun originals(context: Context): String =
        prefs(context).getString(KEY_ORIGINALS, "").orEmpty()

    fun overrideActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERRIDE, false)

    /** Allinea lo stato (true = display modificato, false = ripristinato). */
    fun setOverride(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(KEY_OVERRIDE, active).apply()
    }
}

/**
 * Ripristino del display **al riavvio, prima dello sblocco**.
 *
 * Dichiarato `directBootAware` nel manifest e in ascolto su
 * `LOCKED_BOOT_COMPLETED`: parte appena il sistema è su, **prima** che tu possa
 * digitare la password. Se trova un override attivo, rimette il display a posto
 * → la lock screen torna leggibile e puoi inserire la password.
 *
 * ⚠️ Prima dello sblocco Shizuku non esiste e le preferenze cifrate non sono
 * leggibili: si usano gli originali della memoria non cifrata e la via
 * "permessi" (WRITE_SECURE_SETTINGS + chiamata diretta al servizio di sistema).
 */
class BootRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_BOOT_COMPLETED
        ) {
            return
        }

        DisplayBridge.init(context)

        val originals = DisplayDeviceStore.originals(context)
        if (originals.isBlank()) {
            AppLog.i(TAG, "Nessun originale salvato: nulla da ripristinare")
            return
        }
        if (!DisplayDeviceStore.overrideActive(context)) {
            AppLog.i(TAG, "Nessun override attivo: nulla da fare")
            return
        }

        AppLog.w(TAG, "Riavvio con display modificato: ripristino prima dello sblocco")
        val pending = goAsync()
        Thread {
            // restoreWith non legge le preferenze cifrate: è l'unica via
            // utilizzabile prima dello sblocco.
            val ok = DisplayMaster.restoreWith(context, originals)
            AppLog.i(
                TAG,
                "Ripristino al boot: " + if (ok) "ok" else "FALLITO (via permessi non disponibile)"
            )
            runCatching { pending.finish() }
        }.start()
    }
}
