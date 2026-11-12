package com.nos.motobitride.core.display

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings

private const val TAG = "ProximityMode"

/**
 * Modalità **"Telefono in tasca"**.
 *
 * Comportamento richiesto (non quello del wake lock di sistema):
 *  - sensore **coperto** → oscura **secondo la barra 0–50%** + cambia la
 *    risoluzione;
 *  - sensore **scoperto** → ripristino totale.
 *
 * Non si usa `PROXIMITY_SCREEN_OFF_WAKE_LOCK`: quello **spegne** lo schermo e
 * ignorava completamente la barra di oscuramento (era il difetto segnalato).
 *
 * Il primo evento dopo l'attivazione serve solo a conoscere lo stato: non tocca
 * il display, così attivare la modalità non fa lampeggiare nulla.
 */
object ProximityMode {

    private var sensorManager: SensorManager? = null
    private var listener: SensorEventListener? = null

    /** true = coperto / in tasca. */
    @Volatile
    private var covered: Boolean? = null

    @Volatile
    private var running = false

    fun isSupported(context: Context): Boolean = runCatching {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sm?.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null
    }.getOrDefault(false)

    @Synchronized
    fun start(context: Context) {
        if (running) return
        val app = context.applicationContext
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (sm == null || sensor == null) {
            AppLog.w(TAG, "Sensore di prossimità assente: modalità non disponibile")
            return
        }

        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val value = event?.values?.firstOrNull() ?: return
                // Coperto = valore vicino al minimo. La soglia a metà scala regge
                // sia i sensori 0/1 sia quelli in centimetri.
                val max = sensor.maximumRange.takeIf { it > 0f } ?: 1f
                onTransition(app, value < (max / 2f))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        listener = l
        sensorManager = sm
        covered = null
        runCatching {
            sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            running = true
            AppLog.i(TAG, "Modalità 'telefono in tasca' attiva (sensore)")
        }.onFailure {
            listener = null
            sensorManager = null
            AppLog.w(TAG, "Sensore non registrabile: ${it.message}")
        }
    }

    @Synchronized
    fun stop() {
        cancelPendingCover()
        val sm = sensorManager
        val l = listener
        if (sm != null && l != null) runCatching { sm.unregisterListener(l) }
        sensorManager = null
        listener = null
        covered = null
        if (running) AppLog.i(TAG, "Modalità 'telefono in tasca' spenta")
        running = false
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingCover: Runnable? = null

    private fun cancelPendingCover() {
        pendingCover?.let { handler.removeCallbacks(it) }
        pendingCover = null
    }

    /**
     * Ritardo **solo in copertura**.
     *
     * Scoprendo il sensore non si aspetta nulla: si ripristina subito. Il
     * ritardo serve solo a non far scattare tutto per uno sfioramento.
     */
    private fun onTransition(context: Context, isCovered: Boolean) {
        if (covered == null) {
            covered = isCovered
            AppLog.i(TAG, "Stato iniziale: " + if (isCovered) "coperto" else "scoperto")
            return
        }
        if (covered == isCovered) return
        covered = isCovered

        if (!isCovered) {
            // SCOPRIRE: nessun ritardo. Annulla l'attesa e ripristina subito.
            cancelPendingCover()
            AppLog.i(TAG, "Scoperto: ripristino immediato")
            Thread { DisplayMaster.restoreAll(context) }.start()
            return
        }

        // COPRIRE: si attende il ritardo scelto (0-5 s).
        val dim = AppSettings.displayDimPercent.value.coerceIn(0, 50)
        val delayMs = AppSettings.displayPocketDelaySec.value.coerceIn(0, 5) * 1000L
        val apply = Runnable {
            pendingCover = null
            AppLog.i(TAG, "Coperto: oscuro al $dim% + risoluzione (dopo ${delayMs}ms)")
            Thread { DisplayMaster.applyAll(context, dimPercent = dim) }.start()
        }
        cancelPendingCover()
        if (delayMs <= 0L) {
            apply.run()
        } else {
            pendingCover = apply
            handler.postDelayed(apply, delayMs)
            AppLog.i(TAG, "Coperto: attendo ${delayMs}ms prima di applicare")
        }
    }
}
