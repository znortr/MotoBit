package com.nos.motobitride.core.hud

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nos.motobitride.core.log.AppLog

/**
 * Azioni della notifica di mirroring.
 *
 * Perche' un receiver e non il servizio: `PendingIntent.getService()` su un
 * servizio gia' in foreground funziona, ma se il servizio non fosse attivo
 * Android lo creerebbe e servirebbe comunque promuoverlo a foreground entro
 * pochi secondi. Con un receiver l'azione e' sempre sicura e istantanea.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> {
                AppLog.i(TAG, "Azione notifica: STOP")
                HudSession.stopStream()
            }

            ACTION_RECONNECT -> {
                AppLog.i(TAG, "Azione notifica: RICONNETTI")
                HudSession.requestStream()
                val link = HudSession.link
                if (link != null && !link.isHudConnected) {
                    link.reconnectSaved()
                } else {
                    HudSession.stream?.start()
                }
            }
        }
    }

    companion object {
        private const val TAG = "NotifAction"

        const val ACTION_STOP = "com.nos.motobitride.action.STOP"
        const val ACTION_RECONNECT = "com.nos.motobitride.action.RECONNECT"
    }
}
