package com.nos.motobitride.core.display

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings

private const val TAG = "ScreenOffGuard"

/**
 * Tasto accensione = ripristino totale.
 *
 * `ACTION_SCREEN_OFF` non viene consegnato ai receiver dichiarati nel manifest
 * da Android 8 in poi: va registrato a runtime da un componente vivo.
 *
 * Prima era registrato solo da `MainActivity`, che lo toglieva in `onDestroy`:
 * chiudendo l'app (pulsante «Chiudi» = `finishAndRemoveTask`) la rete di
 * sicurezza spariva e premero il tasto accensione non ripristinava più nulla.
 *
 * Adesso è un oggetto riusabile, registrato da:
 *  - [com.nos.motobitride.MainActivity] → quando l'app è aperta;
 *  - [com.nos.motobitride.core.hud.ScreenCaptureService] → finché il servizio
 *    di mirroring è vivo, cioè per tutta la sessione in moto.
 *
 * Doppia registrazione = due consegne: il ripristino è idempotente e
 * [DisplayMaster] serializza le operazioni, quindi non è un problema.
 */
object ScreenOffRestoreGuard {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            val ctx = context?.applicationContext ?: return

            // Due verità possibili: preferenze normali (app inizializzata) e
            // copia non cifrata (leggibile anche prima dello sblocco).
            val modified = runCatching { AppSettings.displayOverride.value }.getOrDefault(false) ||
                    runCatching { DisplayDeviceStore.overrideActive(ctx) }.getOrDefault(false)
            if (!modified) return

            AppLog.i(TAG, "Schermo spento: ripristino totale del display")
            val pending = goAsync()
            Thread {
                runCatching { DisplayMaster.restoreAll(ctx) }
                runCatching { pending.finish() }
            }.start()
        }
    }

    /**
     * Conteggio dei "padroni" della guardia (Activity + servizio): la
     * registrazione si toglie solo quando l'ultimo la rilascia, altrimenti la
     * morte del servizio spegnerebbe la rete di sicurezza anche con l'app
     * aperta.
     */
    private var holders = 0

    @Synchronized
    fun register(context: Context) {
        holders++
        if (holders > 1) return
        runCatching {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            androidx.core.content.ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            AppLog.i(TAG, "Guardia tasto accensione attiva")
        }.onFailure {
            holders = 0
            AppLog.w(TAG, "Guardia non registrata: ${it.message}")
        }
    }

    @Synchronized
    fun unregister(context: Context) {
        if (holders == 0) return
        holders--
        if (holders > 0) return
        runCatching { context.applicationContext.unregisterReceiver(receiver) }
    }
}
