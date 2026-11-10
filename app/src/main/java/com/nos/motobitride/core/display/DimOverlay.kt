package com.nos.motobitride.core.display

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.nos.motobitride.core.log.AppLog

private const val TAG = "DimOverlay"
private const val PREFS = "dim_state"
private const val KEY_BASE = "base_brightness"
private const val KEY_BASE_MODE = "base_mode"

/**
 * Oscuramento **della luminosità reale** dello schermo (retroilluminazione).
 *
 * Perché non la `screenBrightness` della finestra overlay: su ColorOS/OPPO
 * l'override di finestra viene applicato in modo incoerente (schermo nero pieno
 * a qualunque valore, oppure nessun effetto). Scrivere `SCREEN_BRIGHTNESS`
 * invece funziona sempre, **non è un overlay nero** (quindi i pixel trasmessi al
 * TFT restano identici: si abbassa solo la luce) ed è quello che diceva la
 * direttiva.
 *
 * L'overlay trasparente serve **solo** a tenere lo schermo acceso
 * (`FLAG_KEEP_SCREEN_ON`): non altera nulla e non viene trasmesso.
 *
 * La luminosità di partenza viene memorizzata **una volta sola**, così:
 *  - applicare due volte non scurisce due volte;
 *  - al ripristino torna esattamente com'era (anche dopo un riavvio dell'app).
 */
object DimOverlay {

    private var wakeView: View? = null
    private var windowManager: WindowManager? = null
    private var wakeParams: WindowManager.LayoutParams? = null

    @Volatile
    var isVisible: Boolean = false
        private set

    /** Vero se la luminosità di sistema è scrivibile (permesso WRITE_SETTINGS). */
    fun canDim(context: Context): Boolean = runCatching {
        Settings.System.canWrite(context.applicationContext)
    }.getOrDefault(false)

    /**
     * Applica l'oscuramento.
     * @param dimPercent 0..50 = percentuale di buio (0 = luminosità originale,
     *        ma lo schermo resta comunque acceso).
     */
    fun show(context: Context, dimPercent: Int): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cr = app.contentResolver
        val dim = dimPercent.coerceIn(0, 50)

        // Luminosità e modo di partenza: memorizzati una volta sola.
        if (!prefs.contains(KEY_BASE)) {
            val base = runCatching {
                Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
            }.getOrDefault(128)
            val mode = runCatching {
                Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 1)
            }.getOrDefault(1)
            prefs.edit().putInt(KEY_BASE, base).putInt(KEY_BASE_MODE, mode).apply()
            AppLog.i(TAG, "Luminosità di partenza memorizzata: $base (modo $mode)")
        }

        // ⚠️ NIENTE clamp a 255: molti telefoni (ColorOS/OPPO) usano una scala
        // estesa (qui 2912 sul massimo). Limitarne il valore a 255 significa
        // scrivere quasi zero → schermo completamente nero a qualsiasi slider.
        // La barra indica la **luminosità**: 0 = nero, 50 = metà, 100 = piena.
        // (Prima la interpretavo al contrario: 0 = nessun buio.)
        val base = prefs.getInt(KEY_BASE, 128).coerceAtLeast(1)
        val target = (base.toLong() * dim / 100L).toInt().coerceAtLeast(1)

        val written = runCatching {
            // Modo manuale: con l'automatico attivo la luminosità scritta
            // verrebbe subito sovrascritta dal sensore.
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, target)
            true
        }.getOrElse {
            AppLog.w(TAG, "Luminosità non scrivibile (manca WRITE_SETTINGS): ${it.message}")
            false
        }

        // Se la luminosità di sistema non è scrivibile (WRITE_SETTINGS negato)
        // si ripiega sulla luminosità della FINESTRA: non è nero e non finisce
        // nel flusso video, perché è un override di luce, non un velo nero.
        val useWindow = !written
        ensureWakeWindow(app, dim, useWindow)
        isVisible = true
        AppLog.i(
            TAG,
            "Oscuramento $dim% → luminosità $target/$base " +
                    "(sistema=$written, finestra=$useWindow)"
        )
        return written || useWindow
    }

    fun hide(context: Context? = null) {
        val app = context?.applicationContext
        if (app != null) {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_BASE)) {
                val base = prefs.getInt(KEY_BASE, 128).coerceAtLeast(1)
                val mode = prefs.getInt(KEY_BASE_MODE, 1)
                runCatching {
                    val cr = app.contentResolver
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, base)
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
                }
                prefs.edit().remove(KEY_BASE).remove(KEY_BASE_MODE).apply()
                AppLog.i(TAG, "Luminosità ripristinata a $base (modo $mode)")
            }
        }

        removeWakeWindow()
        isVisible = false
    }

    // ------------------------------------------------------------------
    // Finestra trasparente (solo keep-screen-on)
    // ------------------------------------------------------------------

    private fun ensureWakeWindow(app: Context, dim: Int, useBrightnessOverride: Boolean) {
        if (!Settings.canDrawOverlays(app)) {
            AppLog.w(TAG, "Permesso 'sopra altre app' mancante: schermo non tenuto acceso")
            return
        }
        val brightnessParam = if (useBrightnessOverride) {
            // Stessa semantica della barra: valore = luminosità.
            (dim / 100f).coerceIn(0f, 1f)
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }

        // Già presente: si aggiorna solo la luminosità della finestra.
        val existing = wakeView
        val existingParams = wakeParams
        val existingWm = windowManager
        if (existing != null && existingParams != null && existingWm != null) {
            existingParams.screenBrightness = brightnessParam
            runCatching { existingWm.updateViewLayout(existing, existingParams) }
            return
        }

        val wm = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val v = View(app).apply {
            setBackgroundColor(Color.TRANSPARENT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            screenBrightness = brightnessParam
        }
        runCatching {
            wm.addView(v, lp)
            windowManager = wm
            wakeView = v
            wakeParams = lp
        }.onFailure { AppLog.w(TAG, "Finestra keep-screen-on non aggiunta: ${it.message}") }
    }

    private fun removeWakeWindow() {
        val v = wakeView ?: return
        val wm = windowManager
        wakeView = null
        wakeParams = null
        windowManager = null
        if (wm != null) runCatching { wm.removeView(v) }
    }
}
