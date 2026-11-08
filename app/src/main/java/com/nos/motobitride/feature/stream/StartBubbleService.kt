package com.nos.motobitride.feature.stream

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.nos.motobitride.R
import com.nos.motobitride.core.hud.HudSession
import com.nos.motobitride.core.hud.StreamState
import com.nos.motobitride.core.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pulsante flottante **AVVIA**, mostrato sopra l'app dell'utente quando il TFT
 * non è connesso (scenario 2).
 *
 * - è **trascinabile** (drag) e resta dove lo lasci;
 * - al tocco lancia [StreamLaunchActivity], che esegue connessione → conferma →
 *   consenso → streaming;
 * - si rimuove da sola appena lo streaming parte (`MIRRORING`).
 *
 * Richiede il permesso "Visualizza sopra altre app" (`SYSTEM_ALERT_WINDOW`),
 * concedibile da Impostazioni → Permessi.
 */
class StartBubbleService : Service() {

    companion object {
        private const val TAG = "StartBubble"
        private const val CHANNEL_ID = "bubble_channel"
        private const val NOTIFICATION_ID = 3001
        private const val EXTRA_PACKAGE = "extra_package"

        const val ACTION_HIDE = "com.nos.motobitride.action.HIDE_BUBBLE"

        /** Mostra la bolla per [packageName] (l'app da riaprire dopo l'avvio). */
        fun show(context: Context, packageName: String) {
            val canDraw = Settings.canDrawOverlays(context)
            AppLog.i(TAG, "show(): canDrawOverlays=$canDraw pkg=$packageName")
            if (!canDraw) {
                AppLog.w(TAG, "Permesso overlay mancante: bolla non mostrata")
                return
            }
            val intent = Intent(context, StartBubbleService::class.java).apply {
                putExtra(EXTRA_PACKAGE, packageName)
            }
            runCatching {
                context.startForegroundService(intent)
            }.onFailure {
                AppLog.e(TAG, "Avvio bolla fallito", it)
            }
        }

        fun hide(context: Context) {
            runCatching {
                context.stopService(Intent(context, StartBubbleService::class.java))
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var targetPackage: String = ""

    private var bubbleText: TextView? = null
    private var bubbleSpinner: ProgressBar? = null
    private var working = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            stopSelf()
            return START_NOT_STICKY
        }

        targetPackage = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        AppLog.i(TAG, "onStartCommand: avvio foreground + bolla (pkg=$targetPackage)")
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (t: Throwable) {
            // Tipico: manca FOREGROUND_SERVICE_SPECIAL_USE o il tipo nel manifest
            AppLog.e(TAG, "startForeground FALLITO: la bolla non puo' apparire", t)
            stopSelf()
            return START_NOT_STICKY
        }
        showBubble()
        watchStreamState()
        return START_NOT_STICKY
    }

    /**
     * L'utente ha rimosso il task di MotoBit dal registro app: la bolla non ha
     * piu' senso, va distrutta insieme al servizio.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        AppLog.i(TAG, "Task rimossa: chiudo la bolla")
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        watcher?.cancel()
        bubble?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        bubble = null
        windowManager = null
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Bolla
    // ------------------------------------------------------------------

    private fun showBubble() {
        if (bubble != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val size = (56 * resources.displayMetrics.density).toInt()
        val margin = (16 * resources.displayMetrics.density).toInt()

        val density = resources.displayMetrics.density

        val label = TextView(this).apply {
            text = "AVVIA"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val spinnerView = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                (22 * density).toInt(),
                (22 * density).toInt(),
                Gravity.CENTER
            )
        }

        val view = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F9A825"))
                setStroke((2 * density).toInt(), Color.parseColor("#33FFFFFF"))
            }
            addView(
                label,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
            addView(spinnerView)
        }
        bubbleText = label
        bubbleSpinner = spinnerView

        val layoutParams = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = margin
            y = resources.displayMetrics.heightPixels / 2
        }

        attachDrag(view, layoutParams, size)

        runCatching {
            wm.addView(view, layoutParams)
            bubble = view
            params = layoutParams
            AppLog.i(TAG, "Bolla AVVIA mostrata")
        }.onFailure {
            AppLog.e(TAG, "addView bolla fallito", it)
            stopSelf()
        }
    }

    /** Drag + tap: si muove seguendo il dito, al rilascio senza spostamento scatta il tocco. */
    private fun attachDrag(
        view: View,
        layoutParams: WindowManager.LayoutParams,
        size: Int
    ) {
        val threshold = (8 * resources.displayMetrics.density)
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams.x
                    startY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > threshold || abs(dy) > threshold) moved = true
                    layoutParams.x = (startX + dx).toInt()
                    layoutParams.y = (startY + dy).toInt()
                    runCatching { windowManager?.updateViewLayout(view, layoutParams) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) onBubbleTap()
                    true
                }

                else -> false
            }
        }
    }

    /**
     * Mostra lo spinner al posto della scritta mentre consenso + connessione
     * sono in corso (la bolla sparisce da sola al `MIRRORING`).
     */
    private fun setWorking(value: Boolean) {
        if (working == value) return
        working = value
        bubbleText?.visibility = if (value) View.GONE else View.VISIBLE
        bubbleSpinner?.visibility = if (value) View.VISIBLE else View.GONE
    }

    private fun onBubbleTap() {
        AppLog.i(TAG, "Bolla AVVIA premuta")
        setWorking(true)
        runCatching {
            startActivity(
                StreamLaunchActivity.intent(
                    context = this,
                    packageName = targetPackage,
                    openApp = false
                )
            )
        }.onFailure {
            AppLog.e(TAG, "Avvio StreamLaunchActivity fallito", it)
        }
    }

    /** Si toglie da sola quando il mirroring è attivo. */
    private fun watchStreamState() {
        if (watcher != null) return
        watcher = scope.launch {
            while (isActive) {
                when (HudSession.stream?.state?.value) {
                    StreamState.MIRRORING -> {
                        AppLog.i(TAG, "Mirroring attivo: rimuovo la bolla")
                        stopSelf()
                        return@launch
                    }
                    StreamState.IDLE, StreamState.ERROR, null -> setWorking(false)
                    else -> setWorking(true)
                }
                delay(600)
            }
        }
    }

    // ------------------------------------------------------------------
    // Notifica del foreground service
    // ------------------------------------------------------------------

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Pulsante AVVIA",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mostra il pulsante per avviare il mirroring"
                setShowBadge(false)
            }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_link_off)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Tocca AVVIA sopra l'app per trasmettere al TFT")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
