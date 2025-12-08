package com.nos.ridedaemon.hud

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.Surface

class HudRenderer (
    private val surface: Surface,
    private val fps: Int = 30
) {

    @Volatile
    private var running = false
    private var renderThread: Thread? = null

    fun start() {
        if (running) return
        running = true
        renderThread = Thread(::renderLoop, "HudRenderer").apply {
            start()
        }
    }

    fun stop() {
        running = false
        renderThread?.join()
        renderThread = null
    }

    private fun renderLoop() {
        val frameDurationNs = 1_000_000_000 / fps
        var nextFrameTime = System.nanoTime()
        var frameCount = 0L

        // Pre-allocate paint to avoid GC pressure
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f
        }

        while (running) {
            val now = System.nanoTime()
            if (now < nextFrameTime) {
                val sleepMs = (nextFrameTime - now) / 1_000_000L
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs)
                    } catch (_: InterruptedException){}
                }
            }
            nextFrameTime += frameDurationNs

            var canvas: Canvas? = null
            try {
                //Lock the canvas to draw
                canvas = surface.lockCanvas(null)

                // set background color to clear frame
                canvas.drawColor(Color.DKGRAY)

                val text = "HUD frame: $frameCount"
                canvas.drawText(text, 50f, 100f, textPaint)

                frameCount++
            } catch (t: Throwable) {
                // this could happen with an invalid surface which
                // can happen if the encoder stops. TODO detect and exit
                Log.e("HudRenderer", "Render error, stopping renderer", t)
                break // or: running = false; break
            } finally {
                if (canvas != null) {
                    try {
                        surface.unlockCanvasAndPost(canvas)
                    } catch (_: Throwable) {
                        // surface may have nil'd, ignore
                    }
                }
            }
        }
    }
}