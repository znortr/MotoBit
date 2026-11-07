package com.nos.motobitride.core.hud

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nos.motobitride.R
import com.nos.motobitride.core.log.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service (type=mediaProjection|location) che acquisisce la MediaProjection.
 *
 * Android 14+: `getMediaProjection()` va chiamata SOLO quando il servizio
 * foreground e' gia' attivo, quindi `startForeground()` precede sempre.
 *
 * Il tipo `location` non serve alla cattura: serve a far considerare l'app
 * "in foreground" per le API Wi-Fi Direct (WifiP2pManager e' location-gated da
 * Android 10). Senza, con l'app in background discoverPeers()/connect()
 * falliscono con ERROR e il TFT non viene piu' ritrovato dopo uno spegnimento.
 *
 * La notifica mostra lo **stato reale** della connessione e offre due azioni
 * (Stop / Riconnetti) utilizzabili senza aprire l'app.
 */
class ScreenCaptureService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            try {
                startForeground(NOTIFICATION_ID, buildNotification(this, currentText), types)
            } catch (t: Throwable) {
                // Su Android 14+ il tipo location richiede il permesso di
                // posizione gia' concesso: se manca si resta almeno col
                // mirroring funzionante.
                AppLog.w(
                    TAG,
                    "startForeground(mediaProjection|location) rifiutato: ${t.message}. " +
                            "Fallback a solo mediaProjection (riconnessione in background limitata)"
                )
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(this, currentText),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            }
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(this, currentText))
        }

        // Da qui il servizio e' vivo: `updateStatus` puo' aggiornare la notifica.
        running = true

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED

        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }

        if (resultCode == Activity.RESULT_OK && data != null) {
            val manager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            try {
                val projection = manager.getMediaProjection(resultCode, data)
                _mediaProjectionState.value = projection
                AppLog.i(TAG, "MediaProjection acquisita nel foreground service")
            } catch (t: Throwable) {
                AppLog.e(TAG, "getMediaProjection fallita", t)
            }
        } else {
            AppLog.w(TAG, "Servizio avviato senza dati di cattura validi")
        }

        return START_NOT_STICKY
    }

    /**
     * L'utente ha rimosso il task dal registro app (swipe): per lui l'app e'
     * chiusa, quindi si chiude anche la sessione HUD e si rimuove la notifica.
     *
     * Senza questo override il foreground service continuava a vivere: la
     * notifica di mirroring restava per sempre con i pulsanti Stop/Riconnetti,
     * mostrando uno stato che non corrispondeva piu' a nulla (falsa
     * informazione).
     *
     * Nota: premere Home NON passa da qui — in quel caso l'app resta
     * volutamente viva in background (serve mentre si usa il navigatore).
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        AppLog.i(TAG, "Task rimossa dall'utente: chiudo sessione e notifica")
        runCatching { HudSession.closeAll() }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        // Sicurezza: nessuna notifica deve sopravvivere al servizio.
        runCatching {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        }
        AppLog.i(TAG, "ScreenCaptureService distrutto")
        _mediaProjectionState.value?.let {
            try {
                it.stop()
            } catch (_: Throwable) {
            }
        }
        _mediaProjectionState.value = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MotoBit Mirroring",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "ScreenCapture"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_DATA = "extra_data"

        /** Testo corrente della notifica (aggiornato dallo stato della sessione). */
        @Volatile
        private var currentText: String = "Invio dello schermo all'HUD..."

        /**
         * Vero solo mentre il servizio e' in foreground.
         *
         * Senza questa guardia `updateStatus()` poteva **ricreare** la notifica
         * dopo che il servizio era stato fermato: `finishAndRemoveTask()` chiama
         * `onTaskRemoved()` -> `HudSession.closeAll()` -> `updateStatus("Fermo")`
         * -> `notify()`, e la notifica riappariva ad app chiusa.
         */
        @Volatile
        private var running = false

        private val _mediaProjectionState = MutableStateFlow<MediaProjection?>(null)
        val mediaProjectionFlow: StateFlow<MediaProjection?> =
            _mediaProjectionState.asStateFlow()

        fun startService(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            // La notifica va rimossa SUBITO: se il servizio non fosse in primo
            // piano, `stopService` non la toccherebbe.
            runCatching {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            }
            try {
                context.stopService(Intent(context, ScreenCaptureService::class.java))
            } catch (t: Throwable) {
                AppLog.e(TAG, "stopService fallita", t)
            }
            _mediaProjectionState.value = null
        }

        /**
         * Aggiorna il testo della notifica di mirroring con lo stato reale.
         * Non fa nulla se il testo non e' cambiato (evita notify inutili).
         */
        fun updateStatus(context: Context, text: String) {
            if (!running) return // servizio morto: nessuna notifica da aggiornare
            if (text == currentText) return
            currentText = text
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            runCatching {
                nm.notify(NOTIFICATION_ID, buildNotification(context, text))
            }.onFailure {
                AppLog.w(TAG, "aggiornamento notifica fallito: ${it.message}")
            }
        }

        private fun buildNotification(context: Context, text: String): Notification {
            val stopIntent = PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, NotificationActionReceiver::class.java)
                    .setAction(NotificationActionReceiver.ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val reconnectIntent = PendingIntent.getBroadcast(
                context,
                2,
                Intent(context, NotificationActionReceiver::class.java)
                    .setAction(NotificationActionReceiver.ACTION_RECONNECT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("MotoBit")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(0, "Stop", stopIntent)
                .addAction(0, "Riconnetti", reconnectIntent)
                .build()
        }
    }
}
