package com.gravalix.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/**
 * Tela transparente onde você desenha ou circula coisas com o dedo.
 * Fica numa janela SEM proteção, por isso o desenho APARECE no vídeo gravado.
 */
class PenCanvas(context: Context) : View(context) {

    companion object {
        val COLORS = intArrayOf(
            0xFFFF3B3B.toInt(), // vermelho
            0xFFFFD60A.toInt(), // amarelo
            0xFF30D158.toInt(), // verde
            0xFF0A84FF.toInt(), // azul
            0xFFFFFFFF.toInt()  // branco
        )
        private const val LIFE_MS = 4000L
        private const val FADE_MS = 1000L
    }

    private class Stroke(val path: Path, val color: Int, val width: Float) {
        var endedAt = 0L // 0 = ainda desenhando
    }

    private val strokes = ArrayList<Stroke>()
    private var current: Stroke? = null
    private var lastX = 0f
    private var lastY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    var color: Int = COLORS[0]
    var fade: Boolean = false
    var strokeWidthPx: Float = resources.displayMetrics.density * 5f

    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1)
        invalidate()
    }

    fun clear() {
        strokes.clear()
        current = null
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val s = Stroke(Path(), color, strokeWidthPx)
                s.path.moveTo(e.x, e.y)
                s.path.lineTo(e.x + 0.1f, e.y + 0.1f) // ponto simples já aparece
                strokes.add(s)
                current = s
                lastX = e.x
                lastY = e.y
            }
            MotionEvent.ACTION_MOVE -> {
                val s = current ?: return true
                for (i in 0 until e.historySize) {
                    quad(s, e.getHistoricalX(i), e.getHistoricalY(i))
                }
                quad(s, e.x, e.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                current?.let {
                    it.path.lineTo(lastX, lastY)
                    it.endedAt = SystemClock.uptimeMillis()
                }
                current = null
            }
        }
        invalidate()
        return true
    }

    private fun quad(s: Stroke, x: Float, y: Float) {
        s.path.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f)
        lastX = x
        lastY = y
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        var animate = false
        val it = strokes.iterator()
        while (it.hasNext()) {
            val s = it.next()
            var alpha = 255
            if (fade && s.endedAt != 0L) {
                val age = now - s.endedAt
                if (age >= LIFE_MS) {
                    it.remove()
                    continue
                }
                if (age > LIFE_MS - FADE_MS) {
                    alpha = (255 * (LIFE_MS - age) / FADE_MS).toInt().coerceIn(0, 255)
                }
                animate = true
            }
            paint.color = s.color
            paint.alpha = alpha
            paint.strokeWidth = s.width
            canvas.drawPath(s.path, paint)
        }
        if (animate) postInvalidateOnAnimation()
    }
}
