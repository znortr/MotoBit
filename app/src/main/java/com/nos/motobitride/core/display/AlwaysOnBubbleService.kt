package com.nos.motobitride.core.display

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
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.nos.motobitride.R
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

private const val TAG = "AlwaysOnBubble"

/**
 * Bolla della modalità **"Sempre acceso"**: bolla **separata** da quella
 * "AVVIA" (che serve allo streaming e sparisce quando parte il mirroring).
 *
 * Un tocco alterna risoluzione attiva / ripristino. La bolla **mostra lo stato**
 * (blu = display modificato, grigio = normale): prima non cambiava mai e un
 * tocco senza effetto sembrava un guasto.
 */
class AlwaysOnBubbleService : Service() {

    companion object {
        private const val CHANNEL_ID = "always_on_bubble_channel"
        private const val NOTIFICATION_ID = 3002
        const val ACTION_HIDE = "com.nos.motobitride.action.HIDE_ALWAYS_ON_BUBBLE"

        fun show(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                AppLog.w(TAG, "Permesso overlay mancante: bolla 'sempre acceso' non mostrata")
                return
            }
            runCatching {
                context.startForegroundService(
                    Intent(context, AlwaysOnBubbleService::class.java)
                )
            }.onFailure { AppLog.w(TAG, "Avvio bolla fallito: ${it.message}") }
        }

        fun hide(context: Context) {
            runCatching {
                context.stopService(Intent(context, AlwaysOnBubbleService::class.java))
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var bubble: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var label: TextView? = null
    private val busy = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble != null) return START_NOT_STICKY

        // Può nascere in un processo fresco: le impostazioni servono subito.
        runCatching { AppSettings.init(this) }

        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (t: Throwable) {
            AppLog.e(TAG, "startForeground fallito: la bolla non può apparire", t)
            stopSelf()
            return START_NOT_STICKY
        }

        showBubble()
        // La bolla deve essere **sempre presente** in questa modalità: se il
        // sistema uccide il servizio, START_STICKY lo fa ripartire e la bolla
        // torna.
        return START_STICKY
    }

    /**
     * L'utente chiude l'app (swipe dal registro o pulsante «Chiudi»): la bolla
     * è il **telecomando** di quella sessione, quindi muore con lei.
     *
     * Si ripristina anche il display: lasciarlo modificato senza la bolla
     * significherebbe un telefono ridimensionato e scurito senza comando per
     * tornare indietro (resterebbe solo il tasto accensione).
     *
     * Nota: se invece è il **sistema** a uccidere il servizio, `START_STICKY`
     * lo fa ripartire e la bolla torna: quello è il caso da coprire.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        AppLog.i(TAG, "App chiusa: rimuovo la bolla e ripristino il display")
        Thread {
            runCatching { DisplayMaster.restoreAll(applicationContext) }
            runCatching { stopSelf() }
        }.start()
        super.onTaskRemoved(rootIntent)
    }

    /**
     * Quando la risoluzione (o la densità) cambia, lo schermo diventa più
     * piccolo: la bolla va riportata **dentro** i nuovi limiti, altrimenti resta
     * fuori e non si vede più.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        runOnMain { clampToScreen() }
    }

    private fun clampToScreen() {
        val lp = params ?: return
        val wm = windowManager ?: return
        val view = bubble ?: return
        val dm = resources.displayMetrics
        val size = lp.width
        lp.x = lp.x.coerceIn(0, (dm.widthPixels - size).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (dm.heightPixels - size).coerceAtLeast(0))
        runCatching { wm.updateViewLayout(view, lp) }
    }

    override fun onDestroy() {
        bubble?.let { v -> runCatching { windowManager?.removeView(v) } }
        bubble = null
        windowManager = null
        label = null
        super.onDestroy()
    }

    private fun showBubble() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm
        val density = resources.displayMetrics.density
        val size = (52 * density).toInt()
        val margin = (16 * density).toInt()

        val text = TextView(this).apply {
            this.text = "TFT"
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        label = text

        val container = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#546E7A"))
                setStroke((2 * density).toInt(), Color.parseColor("#33FFFFFF"))
            }
            addView(
                text,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }

        // NIENTE FLAG_LAYOUT_NO_LIMITS: permetteva alla finestra di restare
        // fuori dallo schermo quando la risoluzione diventava più piccola.
        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = margin
            // In basso a sinistra: non copre la bolla AVVIA (al centro a sinistra).
            y = resources.displayMetrics.heightPixels - size - margin * 3
        }

        attachDrag(container, lp)
        lp.x = lp.x.coerceIn(0, (resources.displayMetrics.widthPixels - size).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (resources.displayMetrics.heightPixels - size).coerceAtLeast(0))
        runCatching {
            wm.addView(container, lp)
            bubble = container
            params = lp
            updateLook()
            AppLog.i(TAG, "Bolla 'sempre acceso' mostrata")
        }.onFailure {
            AppLog.e(TAG, "addView bolla fallito", it)
            stopSelf()
        }
    }

    /**
     * Stato visibile a colpo d'occhio:
     *  - **giallo** = display modificato E schermo oscurato (massima visibilità,
     *    perché lo schermo è scuro e la bolla deve saltare all'occhio);
     *  - **blu** = display modificato ma non oscurato;
     *  - **grigio** = display normale.
     */
    private fun updateLook() {
        val active = runCatching { AppSettings.displayOverride.value }.getOrDefault(false)
        val dimmed = active && runCatching { DimOverlay.isVisible }.getOrDefault(false)
        val color = when {
            dimmed -> "#FFC107"   // giallo
            active -> "#1E88E5"   // blu
            else -> "#546E7A"     // grigio
        }
        val bg = runCatching { bubble?.background as? GradientDrawable }.getOrNull()
        bg?.setColor(Color.parseColor(color))
    }


    private fun attachDrag(view: View, layoutParams: WindowManager.LayoutParams) {
        val density = resources.displayMetrics.density
        val threshold = 8 * density
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
                    if (!moved) toggle()
                    true
                }
                else -> false
            }
        }
    }

    private fun toggle() {
        if (!busy.compareAndSet(false, true)) return
        AppLog.i(TAG, "Bolla premuta")
        Thread {
            try {
                val result = if (AppSettings.displayOverride.value) {
                    DisplayMaster.restoreAll(applicationContext)
                } else {
                    // Risoluzione + oscuramento **solo se** lo switch
                    // "la bolla oscura" è attivo.
                    DisplayMaster.applyAll(
                        applicationContext,
                        dimPercent = DisplayMaster.bubbleDimPercent()
                    )
                }
                AppLog.i(TAG, "Esito: $result")
                // Lo stato può essere cambiato: si ridisegna la bolla e si
                // controlla che sia ancora dentro lo schermo (la risoluzione
                // è appena cambiata).
                runOnMain {
                    updateLook()
                    clampToScreen()
                }
            } finally {
                busy.set(false)
            }
        }.start()
    }

    private fun runOnMain(block: () -> Unit) {
        runCatching {
            android.os.Handler(mainLooper).post(block)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Modalità sempre acceso",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_link_off)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Bolla TFT: un tocco attiva o ripristina il display")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
