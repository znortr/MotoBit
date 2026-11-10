package com.nos.motobitride.core.display

import android.content.Context
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import com.nos.motobitride.core.settings.DisplayMode

private const val TAG = "DisplayMode"

/**
 * Allinea il mondo fisico alla modalità scelta.
 *
 * Le tre modalità sono **mutuamente esclusive per costruzione**: esiste un solo
 * valore salvato, e questa funzione spegne sempre quello che non serve.
 *
 * | Modalità | Schermo | Oscuramento | Risoluzione |
 * |---|---|---|---|
 * | Telefono in tasca | spento dal sensore (`PROXIMITY_SCREEN_OFF`) | no | applicata |
 * | Sempre acceso (bolla) | tenuto acceso dall'overlay | deciso dalla bolla | con la bolla |
 *
 * Il ripristino non passa da qui: è il **tasto accensione**.
 */
object DisplayModeController {

    @Synchronized
    fun apply(context: Context) {
        val app = context.applicationContext
        val mode = AppSettings.displayMode.value
        AppLog.i(TAG, "Allineo la modalità display: $mode")

        when (mode) {
            DisplayMode.POCKET -> {
                AlwaysOnBubbleService.hide(app)
                DimOverlay.hide(app)
                // Si arma SOLO il sensore: attivare la modalità non deve
                // ridimensionare il telefono (segnalato come difetto).
                ProximityMode.start(app)
            }

            DisplayMode.ALWAYS_ON -> {
                ProximityMode.stop()
                // La bolla comanda: all'avvio si mostra SOLO la bolla.
                // Applicare qui significava modificare il display ogni volta che
                // si apriva l'app — non richiesto e sorprendente.
                AlwaysOnBubbleService.show(app)
            }
        }
    }

    /** Cambio di modalità richiesto dall'utente. */
    fun select(context: Context, mode: DisplayMode) {
        AppSettings.setDisplayMode(mode)
        apply(context)
    }

    /**
     * Applica la risoluzione per una modalità automatica. Se il piano non è
     * sicuro non si tocca niente: la modalità resta scelta, ma il display non
     * viene deformato.
     */
    private fun applyResolution(context: Context, dimPercent: Int) {
        val result = DisplayMaster.applyAll(context, dimPercent)
        AppLog.i(TAG, "Risoluzione della modalità: $result")
    }
}
