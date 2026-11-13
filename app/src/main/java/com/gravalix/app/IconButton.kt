package com.gravalix.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.View

/**
 * Botão redondo com um ícone desenhado à mão (não depende de fontes nem de imagens).
 * Usado na bolha, no menu e na barra da caneta.
 */
class IconButton(context: Context, icon: Int, color: Int = 0xFF3A3658.toInt()) :
    View(context) {

    companion object {
        const val REC = 0
        const val STOP = 1
        const val PAUSE = 2
        const val PLAY = 3
        const val PEN = 4
        const val TOUCH = 5
        const val CLOSE = 6
        const val UNDO = 7
        const val TRASH = 8
        const val HAND = 9
        const val HOME = 10
        const val CAMERA = 11
        const val FADE = 12
    }

    /** Cor do fundo redondo do botão. */
    var circleColor: Int = color
        set(value) {
            field = value
            applyBackground()
        }

    var icon: Int = icon
        set(value) {
            field = value
            invalidate()
        }

    /** Fica com um anel colorido quando o recurso está ligado. */
    var active: Boolean = false
        set(value) {
            field = value
            applyBackground()
            invalidate()
        }

    /** Foto no lugar do ícone da câmera (usada na bolha). */
    var photo: android.graphics.Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Cor de destaque do ícone (ex.: cor da caneta). */
    var tint: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    init {
        applyBackground()
    }

    private fun applyBackground() {
        val d = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(circleColor)
            if (active) setStroke((resources.displayMetrics.density * 2.5f).toInt(), 0xFF8781FF.toInt())
        }
        background = d
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = minOf(w, h)
        val pad = s * 0.28f
        paint.color = tint
        stroke.color = tint
        stroke.strokeWidth = s * 0.075f

        val ph = photo
        if (ph != null && icon == CAMERA) {
            // foto redonda; o anel na cor do botão mostra o estado (gravando, pausado...)
            path.reset()
            path.addCircle(w / 2, h / 2, s / 2, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(path)
            paint.isFilterBitmap = true
            rect.set(0f, 0f, w, h)
            canvas.drawBitmap(ph, null, rect, paint)
            canvas.restore()
            stroke.color = circleColor or 0xFF000000.toInt()
            stroke.strokeWidth = s * 0.06f
            canvas.drawCircle(w / 2, h / 2, s / 2 - s * 0.03f, stroke)
            return
        }

        when (icon) {
            REC -> {
                paint.color = 0xFFFF5470.toInt()
                canvas.drawCircle(w / 2, h / 2, s * 0.23f, paint)
            }
            STOP -> {
                paint.color = Color.WHITE
                canvas.drawRoundRect(pad, pad, w - pad, h - pad, s * 0.05f, s * 0.05f, paint)
            }
            PAUSE -> {
                paint.color = Color.WHITE
                val bw = (w - 2 * pad) / 3f
                canvas.drawRect(pad, pad, pad + bw, h - pad, paint)
                canvas.drawRect(w - pad - bw, pad, w - pad, h - pad, paint)
            }
            PLAY -> {
                paint.color = Color.WHITE
                path.reset()
                path.moveTo(pad + s * 0.04f, pad)
                path.lineTo(w - pad + s * 0.04f, h / 2)
                path.lineTo(pad + s * 0.04f, h - pad)
                path.close()
                canvas.drawPath(path, paint)
            }
            PEN -> {
                // lápis na diagonal
                canvas.save()
                canvas.rotate(45f, w / 2, h / 2)
                val bw = s * 0.15f
                rect.set(w / 2 - bw, h / 2 - s * 0.30f, w / 2 + bw, h / 2 + s * 0.12f)
                paint.color = tint
                canvas.drawRoundRect(rect, bw * 0.3f, bw * 0.3f, paint)
                path.reset()
                path.moveTo(w / 2 - bw, h / 2 + s * 0.12f)
                path.lineTo(w / 2 + bw, h / 2 + s * 0.12f)
                path.lineTo(w / 2, h / 2 + s * 0.31f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.restore()
            }
            TOUCH -> {
                // dedo tocando: círculo com anel
                stroke.color = tint
                canvas.drawCircle(w / 2, h / 2, s * 0.10f, paint.apply { color = tint })
                canvas.drawCircle(w / 2, h / 2, s * 0.24f, stroke)
            }
            CLOSE -> {
                stroke.color = Color.WHITE
                canvas.drawLine(pad, pad, w - pad, h - pad, stroke)
                canvas.drawLine(w - pad, pad, pad, h - pad, stroke)
            }
            UNDO -> {
                stroke.color = Color.WHITE
                rect.set(pad, pad + s * 0.04f, w - pad, h - pad + s * 0.04f)
                canvas.drawArc(rect, 200f, 250f, false, stroke)
                paint.color = Color.WHITE
                path.reset()
                path.moveTo(pad - s * 0.04f, pad + s * 0.02f)
                path.lineTo(pad + s * 0.22f, pad - s * 0.02f)
                path.lineTo(pad + s * 0.06f, pad + s * 0.22f)
                path.close()
                canvas.drawPath(path, paint)
            }
            TRASH -> {
                stroke.color = Color.WHITE
                canvas.drawLine(pad, pad + s * 0.08f, w - pad, pad + s * 0.08f, stroke)
                canvas.drawLine(w / 2 - s * 0.07f, pad + s * 0.02f, w / 2 + s * 0.07f, pad + s * 0.02f, stroke)
                rect.set(pad + s * 0.04f, pad + s * 0.12f, w - pad - s * 0.04f, h - pad)
                canvas.drawRoundRect(rect, s * 0.04f, s * 0.04f, stroke)
            }
            HAND -> {
                // "mão": palma + dedos (modo de tocar nos apps por baixo)
                paint.color = Color.WHITE
                rect.set(w / 2 - s * 0.20f, h / 2 - s * 0.02f, w / 2 + s * 0.20f, h / 2 + s * 0.27f)
                canvas.drawRoundRect(rect, s * 0.09f, s * 0.09f, paint)
                for (i in 0..3) {
                    val x = w / 2 - s * 0.165f + i * s * 0.11f
                    rect.set(x - s * 0.04f, h / 2 - s * (0.26f - (if (i == 1 || i == 2) 0.04f else 0f)),
                        x + s * 0.04f, h / 2 + s * 0.05f)
                    canvas.drawRoundRect(rect, s * 0.04f, s * 0.04f, paint)
                }
            }
            HOME -> {
                stroke.color = Color.WHITE
                path.reset()
                path.moveTo(w / 2, pad - s * 0.02f)
                path.lineTo(w - pad, h / 2)
                path.lineTo(w - pad - s * 0.05f, h / 2)
                path.lineTo(w - pad - s * 0.05f, h - pad)
                path.lineTo(pad + s * 0.05f, h - pad)
                path.lineTo(pad + s * 0.05f, h / 2)
                path.lineTo(pad, h / 2)
                path.close()
                canvas.drawPath(path, stroke)
            }
            CAMERA -> {
                paint.color = Color.WHITE
                val l = w * 0.20f
                val t = h * 0.30f
                val r = w * 0.63f
                val b = h * 0.70f
                canvas.drawRoundRect(l, t, r, b, s * 0.07f, s * 0.07f, paint)
                path.reset()
                path.moveTo(r - s * 0.01f, h * 0.45f)
                path.lineTo(w * 0.80f, h * 0.33f)
                path.lineTo(w * 0.80f, h * 0.67f)
                path.lineTo(r - s * 0.01f, h * 0.55f)
                path.close()
                canvas.drawPath(path, paint)
            }
            FADE -> {
                paint.color = tint
                canvas.drawCircle(w / 2, h / 2, s * 0.22f, paint.apply { alpha = 120 })
                paint.alpha = 255
                stroke.color = tint
                canvas.drawCircle(w / 2, h / 2, s * 0.22f, stroke)
            }
        }
    }
}
