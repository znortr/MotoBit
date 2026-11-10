package com.nos.motobitride.core.display

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Aritmetica del display. Pura, senza Android: è l'**unico** punto in cui si
 * calcolano le misure, così pagina, tasto Volume, sensore e bolla usano la
 * stessa regola.
 *
 * Modello: **il formato lo detta il TFT**. Si parte dalla risoluzione che il TFT
 * negozia e la si moltiplica per la percentuale scelta, **conservando il
 * rapporto**: così sul TFT l'immagine riempie tutto, senza bande.
 *
 * Sulla densità: è **opzionale**. Se non la si tocca (densità = 0) la larghezza
 * in dp del telefono diventa `larghezza_px × 160 / densità_fisica`; sotto una
 * soglia l'interfaccia non sta più in piedi (è la "barra larga e bassa" già
 * vista). In quel caso si **rifiuta** invece di deformare il telefono.
 */
object DisplayGeometry {

    /** Formato che il TFT negozia: 100% = 592×752. */
    const val TFT_W = 592
    const val TFT_H = 752

    /** Scala ammessa (%). 100% = formato TFT esatto, oltre si guadagna dettaglio. */
    const val MIN_SCALE = 100
    const val MAX_SCALE = 200

    /** Larghezza minima in dp perché l'interfaccia del telefono resti usabile. */
    const val MIN_SAFE_DP = 300f

    /** Tolleranza sulla densità calcolata. */
    private const val DP_TOLERANCE = 0.06

    const val MIN_DENSITY = 120
    const val MAX_DENSITY = 640

    /** Arrotonda a un numero pari (richiesto dagli encoder video). */
    fun roundEven(value: Double): Int {
        val v = value.roundToInt().coerceAtLeast(2)
        return if (v % 2 == 0) v else v - 1
    }

    /**
     * Dimensioni da impostare sul telefono per riempire il TFT.
     *
     * La larghezza viene dalla percentuale; l'altezza si ricava **dal rapporto
     * del TFT**, non dalla sua altezza in pixel: così il riempimento è esatto a
     * qualunque percentuale.
     */
    fun targetSize(tftW: Int, tftH: Int, scalePct: Int): Pair<Int, Int> {
        val w0 = if (tftW > 0) tftW else TFT_W
        val h0 = if (tftH > 0) tftH else TFT_H
        val pct = scalePct.coerceIn(MIN_SCALE, MAX_SCALE)
        val width = roundEven(w0 * pct / 100.0)
        val height = roundEven(width.toDouble() * h0 / w0)
        return width to height
    }

    /** Larghezza in dp di una coppia (pixel, densità). */
    fun dpWidthOf(widthPx: Int, densityDpi: Int): Float =
        if (widthPx > 0 && densityDpi > 0) widthPx * 160f / densityDpi else 0f

    /**
     * Densità che **conserva** la larghezza in dp [dpWidth] con la nuova
     * larghezza [widthPx].
     * @return null se non è calcolabile in sicurezza (in quel caso non si applica).
     */
    fun densityKeepingDp(widthPx: Int, dpWidth: Float): Int? {
        if (widthPx <= 0 || dpWidth < MIN_SAFE_DP) return null
        val density = (widthPx * 160.0 / dpWidth).roundToInt()
        if (density < MIN_DENSITY || density > MAX_DENSITY) return null
        val resultingDp = dpWidthOf(widthPx, density)
        if (resultingDp <= 0f) return null
        if (abs(resultingDp - dpWidth) / dpWidth > DP_TOLERANCE) return null
        return density
    }

    /** Estrae `(larghezza, altezza)` da `"WxH"`. */
    fun parseSize(raw: String?): Pair<Int, Int>? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val parts = text.split('x', 'X')
        if (parts.size != 2) return null
        val w = parts[0].trim().toIntOrNull() ?: return null
        val h = parts[1].trim().toIntOrNull() ?: return null
        if (w <= 0 || h <= 0) return null
        return w to h
    }

    /** Ordina la coppia: la rotazione inverte larghezza e altezza. */
    fun normalized(size: Pair<Int, Int>): Pair<Int, Int> =
        if (size.first <= size.second) size.first to size.second
        else size.second to size.first
}
