package com.nos.motobitride.core.display

import android.content.Context
import com.nos.motobitride.core.hud.HudSession
import com.nos.motobitride.core.log.AppLog
import com.nos.motobitride.core.settings.AppSettings
import kotlin.math.roundToInt

private const val TAG = "DisplayMaster"

/**
 * Orchestratore del display: **unica porta** per applicare e ripristinare.
 *
 * Modello (quello della app di riferimento, con in più le misure del TFT):
 *  - dimensioni = **TFT negoziato × percentuale**, rapporto conservato → sul TFT
 *    l'immagine riempie tutto;
 *  - **densità opzionale**: spenta la densità non si tocca; accesa si calcola
 *    per mantenere la larghezza in dp (e si **rifiuta** se il risultato non è
 *    verificabile);
 *  - verifica dopo l'applicazione: se il display non è quello richiesto si
 *    ripristina subito;
 *  - il ripristino è `wm size reset`: non dipende da nessun valore salvato,
 *    quindi funziona sempre (Shizuku spento, prima dello sblocco, ecc.);
 *  - ogni comando tocca **solo il proprio parametro**: l'oscuramento è un
 *    overlay separato, la barra un'altra chiamata, l'orientamento un'altra.
 *
 * ⚠️ Metodi bloccanti: chiamarli da un thread di background.
 */
object DisplayMaster {

    enum class Result {
        APPLIED,
        RESTORED,
        /** Calcolo non sicuro: **nulla toccato**. */
        UNSAFE,
        /** Motore assente o permessi mancanti: nulla toccato. */
        NOT_READY,
        /** Il display non ha preso la modifica: riportato indietro. */
        FAILED
    }

    data class Plan(
        val width: Int,
        val height: Int,
        /** 0 = densità non toccata. */
        val density: Int,
        val dpWidth: Float,
        val percent: Int,
        val rotationMode: String
    )

    // ------------------------------------------------------------------
    // Formato del TFT
    // ------------------------------------------------------------------

    /**
     * Risoluzione **realmente negoziata** dal TFT.
     *
     * ⚠️ Solo se la negoziazione è avvenuta davvero: `negotiatedWidth` del
     * controller, quando non si è ancora connessi, restituisce un default
     * (800x384, orizzontale) che **non è il formato del TFT** e produceva sul
     * telefono un rettangolo sbagliato. Senza negoziazione si usa il formato
     * dichiarato: 592×752 verticale.
     */
    fun tftSize(): Pair<Int, Int> {
        val stream = runCatching { HudSession.stream }.getOrNull()
        if (stream != null && stream.isResolutionNegotiated) {
            val w = stream.negotiatedWidth
            val h = stream.negotiatedHeight
            if (w > 0 && h > 0) return w to h
        }
        return DisplayGeometry.TFT_W to DisplayGeometry.TFT_H
    }

    // ------------------------------------------------------------------
    // Piano
    // ------------------------------------------------------------------

    /**
     * Larghezza in dp del telefono **allo stato originale**.
     *
     * Si legge dalle metriche **solo finché il display non è modificato**, poi
     * resta memorizzata. Leggerla mentre l'override è attivo darebbe un valore
     * già compensato: la densità verrebbe compensata due volte e l'interfaccia
     * diventerebbe inutilizzabile.
     */
    private fun baseDpWidth(ctx: Context): Float {
        val stored = AppSettings.displayOrigDp.value
        if (stored > 0) return stored.toFloat()

        val dm = ctx.resources.displayMetrics
        val dp = DisplayGeometry.dpWidthOf(minOf(dm.widthPixels, dm.heightPixels), dm.densityDpi)
        if (dp > 0f && !AppSettings.displayOverride.value) {
            AppSettings.setDisplayOrigDp(dp.roundToInt())
        }
        return dp
    }

    /** Lato corto fisico del pannello (per capire i dp con densità non toccata). */
    private fun physicalShortSide(ctx: Context): Int {
        val physical = DisplayGeometry.parseSize(DisplayBridge.current.physicalSize())
        if (physical != null) return minOf(physical.first, physical.second)
        val dm = ctx.resources.displayMetrics
        return minOf(dm.widthPixels, dm.heightPixels)
    }

    /** Calcola cosa applicare. `null` = **non è sicuro applicare**. */
    fun plan(ctx: Context): Plan? {
        val engine = DisplayBridge.current
        if (engine is NoOpDisplayEngine) return null

        val (tftW, tftH) = tftSize()
        val percent = AppSettings.displayScale.value
        val (width, height) = DisplayGeometry.targetSize(tftW, tftH, percent)

        val baseDp = baseDpWidth(ctx)
        val densityOn = AppSettings.displayDensityScale.value
        var density = 0
        var dpWidth = 0f

        if (densityOn) {
            val d = DisplayGeometry.densityKeepingDp(width, baseDp)
            if (d == null) {
                AppLog.e(TAG, "Nessuna densità sicura per ${width}px con ${baseDp.toInt()}dp")
                return null
            }
            density = d
            dpWidth = DisplayGeometry.dpWidthOf(width, d)
        } else {
            // Densità non toccata: la densità resta quella fisica, quindi
            // dp = larghezza_nuova × dp_originali / lato_corto_fisico.
            val shortSide = physicalShortSide(ctx).coerceAtLeast(1)
            dpWidth = width * baseDp / shortSide
            if (dpWidth < DisplayGeometry.MIN_SAFE_DP) {
                AppLog.e(
                    TAG,
                    "Rifiutato: ${width}px darebbero ${dpWidth.toInt()}dp " +
                            "(minimo ${DisplayGeometry.MIN_SAFE_DP.toInt()}dp) senza densità:" +
                            " attivare la densità o alzare la percentuale."
                )
                return null
            }
        }

        val plan = Plan(
            width = width,
            height = height,
            density = density,
            dpWidth = dpWidth,
            percent = percent,
            rotationMode = AppSettings.displayRotation.value
        )
        AppLog.i(
            TAG,
            "Piano: ${plan.width}x${plan.height} · " +
                    if (plan.density > 0) "densità ${plan.density}dpi (${plan.dpWidth.toInt()}dp)"
                    else "densità NON toccata (${plan.dpWidth.toInt()}dp)"
        )
        return plan
    }

    /** Vero se il piano è calcolabile in sicurezza (per abilitare i comandi). */
    fun isReady(ctx: Context): Boolean = plan(ctx) != null

    // ------------------------------------------------------------------
    // Applica / ripristina
    // ------------------------------------------------------------------

    /**
     * Applica risoluzione (+ densità se prevista) e orientamento.
     *
     * @param dimPercent oscuramento da mostrare dopo l'applicazione:
     *        `null` = quello impostato in pagina, `0` = nessuno
     *        (modalità "sempre acceso").
     */
    @Synchronized
    fun applyAll(ctx: Context, dimPercent: Int? = null): Result {
        val plan = plan(ctx) ?: return Result.UNSAFE
        val engine = DisplayBridge.current

        val ok = runCatching {
            engine.apply(plan.width, plan.height, plan.density, plan.rotationMode)
        }.getOrDefault(false)

        if (!ok) {
            AppLog.e(TAG, "Il display non ha preso la modifica: ripristino immediato")
            // Ripristino senza toccare le preferenze di pagina: l'utente non ha
            // scelto lui di tornare indietro.
            runCatching { engine.restore(originals(ctx, refresh = false)) }
            DimOverlay.hide(ctx)
            AppSettings.setDisplayOverride(false)
            return Result.FAILED
        }

        AppSettings.setDisplayOverride(true)
        markOverride(ctx, true)
        mirrorOriginals(ctx)

        val dim = dimPercent ?: AppSettings.displayDimPercent.value
        DimOverlay.show(ctx, dimPercent = dim.coerceIn(0, 50))

        // Il cambio di risoluzione ricrea la configurazione e il sistema
        // riporta la barra: va riasserita ORA, non solo quando si tocca lo switch.
        runCatching { engine.setNavBar(AppSettings.displayHideNavBar.value) }

        AppLog.i(TAG, "Display applicato (via ${engine.lastVia})")
        return Result.APPLIED
    }

    /** Ripristino totale: risoluzione, densità, orientamento, barra, oscuramento. */
    @Synchronized
    fun restoreAll(ctx: Context): Result {
        val ok = restoreWith(ctx, originals(ctx, refresh = false))
        return if (ok) Result.RESTORED else Result.NOT_READY
    }

    /**
     * Ripristino con originali espliciti. È la via usata **al riavvio, prima
     * dello sblocco**, quando le preferenze cifrate non sono leggibili: dipende
     * solo da `wm size reset`, quindi non ha bisogno di nulla di salvato.
     */
    @Synchronized
    fun restoreWith(ctx: Context, originals: String): Boolean {
        val ok = runCatching { DisplayBridge.current.restore(originals) }.getOrDefault(false)

        DimOverlay.hide(ctx)
        runCatching { AppSettings.setDisplayOverride(false) }
        markOverride(ctx, false)

        AppLog.i(TAG, "Ripristino totale: " + if (ok) "ok" else "FALLITO")
        return ok
    }

    // ------------------------------------------------------------------
    // Comandi singoli (ognuno tocca solo il suo parametro)
    // ------------------------------------------------------------------

    /**
     * Oscuramento: **solo** overlay.
     *
     * Non tocca risoluzione, densità, rotazione né barra: prima ogni controllo
     * riapplicava tutto e questo rendeva l'interfaccia imprevedibile.
     */
    @Synchronized
    fun applyDimOnly(ctx: Context, dimPercent: Int): Boolean {
        val value = dimPercent.coerceIn(0, 50)
        AppSettings.setDisplayDimPercent(value)
        // 0% dallo slider = nessun oscuramento: si toglie anche la finestra,
        // così il telefono può tornare a spegnersi normalmente.
        // (Nella modalità "sempre acceso" invece la finestra si tiene a 0% di
        // proposito, per tenere lo schermo acceso: quello passa da applyAll.)
        if (value == 0) {
            DimOverlay.hide(ctx)
            return true
        }
        return DimOverlay.show(ctx, value)
    }

    /**
     * Barra comandi: solo barra, e **solo col display modificato**.
     *
     * A risoluzione normale la barra deve restare: serve a usare il telefono.
     * Sparisce quando il display è ridimensionato (allora è solo un peso nel
     * video, perché il TFT non ha trasparenza e la rende nera piena).
     */
    @Synchronized
    fun setNavBar(ctx: Context, hidden: Boolean): Boolean {
        AppSettings.setDisplayHideNavBar(hidden)
        val active = AppSettings.displayOverride.value
        return runCatching {
            DisplayBridge.current.setNavBar(hidden && active)
        }.getOrDefault(false)
    }

    /** Orientamento: solo orientamento. */
    @Synchronized
    fun setOrientation(ctx: Context, mode: String): Boolean {
        val value = if (mode == "landscape") "landscape" else "portrait"
        AppSettings.setDisplayRotation(value)
        return runCatching { DisplayBridge.current.setOrientation(value) }.getOrDefault(false)
    }

    // ------------------------------------------------------------------
    // Stato (per il tasto Volume)
    // ------------------------------------------------------------------

    /**
     * Vero se il display **non è normale**.
     *
     * È il predicato che decide il verso di Volume +: non normale → ripristina,
     * normale → applica. Guarda lo stato **reale** (dimensione + flag), non solo
     * una preferenza: così regge anche se qualcosa è cambiato da fuori.
     */
    fun isModified(ctx: Context): Boolean {
        if (AppSettings.displayOverride.value) return true
        val engine = DisplayBridge.current
        val current = DisplayGeometry.parseSize(engine.currentSize()) ?: return false
        val plan = plan(ctx) ?: return false
        return DisplayGeometry.normalized(current) !=
                DisplayGeometry.normalized(plan.width to plan.height)
    }

    /**
     * Oscuramento usato dalla bolla: **quello della barra 0–50%**, non un valore
     * fisso. Se lo switch "la bolla oscura" è spento, la bolla cambia solo la
     * risoluzione.
     */
    fun bubbleDimPercent(): Int? =
        if (AppSettings.displayBubbleDim.value) {
            AppSettings.displayDimPercent.value.coerceIn(0, 50)
        } else {
            0
        }

    /** Oscuramento della barra 0–50% (nessun valore fisso imposto). */
    fun dimPercentFromSlider(): Int = AppSettings.displayDimPercent.value.coerceIn(0, 50)

    // ------------------------------------------------------------------
    // Originali (per la rotazione; la risoluzione si ripristina con `reset`)
    // ------------------------------------------------------------------

    /**
     * Stato da conservare (rotazione e barra). Se non c'è se ne legge uno nuovo
     * e lo si copia **anche nella memoria non cifrata** (serve al riavvio).
     */
    fun originals(ctx: Context, refresh: Boolean): String {
        val stored = AppSettings.displayOriginals.value
        // Esattamente 4 campi: il formato vecchio (7 campi) NON è compatibile e
        // se passasse farebbe scrivere valori sbagliati nelle impostazioni.
        if (!refresh && stored.split("|").size == 4) return stored

        val deviceCopy = DisplayDeviceStore.originals(ctx)
        if (!refresh && deviceCopy.split("|").size == 4) {
            AppSettings.setDisplayOriginals(deviceCopy)
            return deviceCopy
        }

        val fresh = runCatching { DisplayBridge.current.readOriginals() }.getOrDefault("")
        if (fresh.isBlank()) return ""
        AppSettings.setDisplayOriginals(fresh)
        runCatching { DisplayDeviceStore.saveOriginals(ctx, fresh) }
        return fresh
    }

    private fun mirrorOriginals(ctx: Context) {
        val stored = AppSettings.displayOriginals.value
        if (stored.isBlank()) return
        runCatching { DisplayDeviceStore.saveOriginals(ctx, stored) }
    }

    private fun markOverride(ctx: Context, active: Boolean) {
        runCatching { DisplayDeviceStore.setOverride(ctx, active) }
    }
}
