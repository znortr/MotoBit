package com.nos.ridedaemon.core.hud

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.view.Surface
import androidx.core.graphics.withTranslation

enum class HudScaleMode {
    STRETCH, // scale x and y independently - can cause distortion
    FIT,     // uniform scale - keeping aspect ratio, letterbox if needed
    FILL     // uniform scale - fill entire canvas, crop if needed
}

data class HudScene(
    val clearColor: Int = Color.TRANSPARENT,
    val elements: List<HudElement> = emptyList(),

    // virtual res
    val designWidth: Float,
    val designHeight: Float,
    val scaleMode: HudScaleMode = HudScaleMode.FIT
)

sealed class HudElement {
    abstract val z: Int // Z order: higher on top

    data class Rect(
        override val z: Int,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val color: Int,
        val cornerRadius: Float = 0f,
        val strokeWidth: Float = 0f // 0 = fill
    ) : HudElement()

    data class Line(
        override val z: Int,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val color: Int,
        val strokeWidth: Float
    ) : HudElement()

    data class Circle(
        override val z: Int,
        val cx: Float,
        val cy: Float,
        val radius: Float,
        val color: Int,
        val strokeWidth: Float = 0f // 0 = fill
    ) : HudElement()

    data class Text(
        override val z: Int,
        val text: String,
        val x: Float,
        val y: Float,
        val sizePx: Float,
        val color: Int,
        val align: android.graphics.Paint.Align = android.graphics.Paint.Align.LEFT
    ) : HudElement()

    data class Image(
        override val z: Int,
        @androidx.annotation.DrawableRes
        val resId: Int,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val alpha: Int = 255
    ) : HudElement()
}

class Hud2DSceneRenderer (
    private val assets: HudAssets
) {
    // Reuse paints to avoid allocations in the render loop
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
    }

    private val tmpRectF = RectF()

    private data class Quadruple(
        val a: Float,
        val b: Float,
        val c: Float,
        val d: Float
    )

    private fun drawSceneElements(canvas: Canvas, scene: HudScene) {
        //order by z
        val ordered = scene.elements.sortedBy { it.z }
        ordered.forEach { element ->
            when (element) {
                is HudElement.Rect -> drawRect(canvas, element)
                is HudElement.Circle -> drawCircle(canvas, element)
                is HudElement.Image -> drawImage(canvas, element)
                is HudElement.Line -> drawLine(canvas, element)
                is HudElement.Text -> drawText(canvas, element)
            }
        }
    }

    fun render(canvas: Canvas, scene: HudScene) {
        // clear the canvas
        canvas.drawColor(scene.clearColor, PorterDuff.Mode.SRC)

        if (scene.elements.isEmpty()) return // skip if no elements on scene

        // canvas sizes
        val cw = canvas.width.toFloat()
        val ch = canvas.height.toFloat()

        // design sizes
        val dw = scene.designWidth
        val dh = scene.designHeight

        // scale
        val sx = cw/dw
        val sy = ch/dh
        val (scaleX, scaleY, tx, ty) = when (scene.scaleMode) {
            HudScaleMode.FIT -> {
                val s = minOf(sx, sy)
                val scaledW = dw * s
                val scaledH = dh * s
                val txFit = (cw - scaledW)/2f
                val tyFit = (ch - scaledH)/2f
                Quadruple(s, s, txFit, tyFit)
            }
            HudScaleMode.FILL -> {
                val s = maxOf(sx, sy)
                val scaledW = dw * s
                val scaledH = dh * s
                val txFill = (cw - scaledW)/2f
                val tyFill = (ch - scaledH)/2f
                Quadruple(s, s, txFill, tyFill)
            }
            HudScaleMode.STRETCH -> {
                Quadruple(sx, sy, 0f, 0f)
            }
        }

        canvas.withTranslation(tx, ty) {
            canvas.scale(scaleX, scaleY) // scale virtual coordinates to pixels
            drawSceneElements(canvas, scene)
        }
    }

    private fun drawRect(canvas: Canvas, e: HudElement.Rect) {
        tmpRectF.set(e.left, e.top, e.right, e.bottom)

        if (e.strokeWidth <= 0f) {
            fillPaint.color = e.color
            fillPaint.style = Paint.Style.FILL
            if (e.cornerRadius > 0f) {
                canvas.drawRoundRect(tmpRectF, e.cornerRadius, e.cornerRadius, fillPaint)
            } else {
                canvas.drawRect(tmpRectF, fillPaint)
            }
        } else {
            strokePaint.color = e.color
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = e.strokeWidth
            if (e.cornerRadius > 0f) {
                canvas.drawRoundRect(tmpRectF, e.cornerRadius, e.cornerRadius, strokePaint)
            } else {
                canvas.drawRect(tmpRectF, strokePaint)
            }
        }
    }

    private fun drawLine(canvas: Canvas, e: HudElement.Line) {
        strokePaint.color = e.color
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = e.strokeWidth
        canvas.drawLine(e.startX, e.startY, e.endX, e.endY, strokePaint)
    }

    private fun drawCircle(canvas: Canvas, e: HudElement.Circle) {
        if (e.strokeWidth <= 0f) {
            fillPaint.color = e.color
            fillPaint.style = Paint.Style.FILL
            canvas.drawCircle(e.cx, e.cy, e.radius, fillPaint)
        } else {
            strokePaint.color = e.color
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = e.strokeWidth
            canvas.drawCircle(e.cx, e.cy, e.radius, strokePaint)
        }
    }

    private fun drawText(canvas: Canvas, e: HudElement.Text) {
        textPaint.color = e.color
        textPaint.textSize = e.sizePx
        textPaint.textAlign = e.align
        canvas.drawText(e.text, e.x, e.y, textPaint)
    }

    private fun drawImage(canvas: Canvas, e: HudElement.Image) {
        val drawable = assets.drawable(e.resId)
        drawable.setBounds(
            e.left,
            e.top,
            e.left + e.width,
            e.top + e.height
        )
        drawable.alpha = e.alpha
        drawable.draw(canvas)
    }
}

class Hud2DRenderer(
    surface: Surface,
    fps: Int,
    private val defaultWidth: Float,
    private val defaultHeight: Float,
    private val sceneRenderer: Hud2DSceneRenderer
) : HudRenderer(surface, fps) {
    @Volatile
    var scene: HudScene = HudScene(designWidth=defaultWidth, designHeight=defaultHeight)
        private set

    fun setScene(newScene: HudScene) {
        scene = newScene
    }

    override fun onDrawFrame(canvas: Canvas, frameIndex: Long, elapsedNs: Long) {
        val snapshot = scene
        sceneRenderer.render(canvas, snapshot)
    }
}