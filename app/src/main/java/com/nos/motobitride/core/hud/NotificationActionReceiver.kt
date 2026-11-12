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
                // Il pacchetto va letto PRIMA: stopStream() azzera l'avvio
                // differito, quindi dopo non ci sarebbe più.
                val pkg = HudSession.lastLaunchedPackage

                HudSession.stopStream()

                // --- Chiusura dell'app in primo piano + ritorno a MotoBit ---
                // Senza Shizuku non si può fare un `force-stop`, ma l'app da
                // chiudere è già dietro di noi: `killBackgroundProcesses` è la
                // strada legale e quasi sempre efficace.
                if (!pkg.isNullOrBlank() && pkg != context.packageName) {
                    runCatching {
                        val am = context.getSystemService(
                            android.content.Context.ACTIVITY_SERVICE
                        ) as android.app.ActivityManager
                        am.killBackgroundProcesses(pkg)
                        AppLog.i(TAG, "App chiusa (kill background): $pkg")
                    }.onFailure {
                        AppLog.w(TAG, "kill background non riuscito: ${it.message}")
                    }
                }

                // La bolla AVVIA non serve più.
                runCatching {
                    com.nos.motobitride.feature.stream.StartBubbleService.hide(context)
                }

                // MotoBit torna davanti. L'azione parte da una notifica (tocco
                // dell'utente), quindi l'avvio dell'Activity è consentito.
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            context,
                            com.nos.motobitride.MainActivity::class.java
                        ).addFlags(
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    )
                    AppLog.i(TAG, "MotoBit riportato in primo piano")
                }.onFailure {
                    AppLog.w(TAG, "riapertura MotoBit non riuscita: ${it.message}")
                }
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
