package com.gravalix.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * Desenha a cena da Live. É usada no editor (com toque: arrastar e pinça) e na tela
 * inteira como camada (sem toque), onde a gravação/live captura tudo.
 */
class SceneView(
    context: Context,
    var items: MutableList<SceneItem>,
    private val editable: Boolean
) : View(context) {

    var selected: SceneItem? = null
        set(v) { field = v; invalidate() }
    var onSelect: ((SceneItem?) -> Unit)? = null
    var onChange: (() -> Unit)? = null
    var forceTalk = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = 0xFF8781FF.toInt()
    }
    private var talkUntil = 0L
    private var bounce = 0f
    private var dragging: SceneItem? = null
    private var lastX = 0f
    private var lastY = 0f
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val s = selected ?: return false
            s.size = (s.size * d.scaleFactor).coerceIn(0.05f, 1.2f)
            onChange?.invoke()
            invalidate()
            return true
        }
    })

    private fun rectOf(it: SceneItem): RectF {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = it.x * w
        val cy = it.y * h
        return when (it.type) {
            SceneItem.TEXT -> {
                textPaint.textSize = it.size * w * 0.35f
                val tw = textPaint.measureText(it.text)
                RectF(cx - tw / 2, cy - textPaint.textSize, cx + tw / 2, cy + textPaint.textSize * 0.3f)
            }
            else -> {
                val b = it.bmpA
                val ratio = if (b != null && b.width > 0) b.height.toFloat() / b.width else 1f
                val iw = it.size * w
                val ih = iw * ratio
                RectF(cx - iw / 2, cy - ih / 2, cx + iw / 2, cy + ih / 2)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.currentTimeMillis()
        val lvl = AudioLevel.level
        if (forceTalk || lvl > 0.18f) talkUntil = now + 140
        val talking = now < talkUntil
        bounce = if (talking) bounce * 0.6f + (lvl.coerceAtLeast(if (forceTalk) 0.6f else 0f)) * 0.4f else bounce * 0.7f

        var animate = false
        for (it in items) {
            val r = rectOf(it)
            when (it.type) {
                SceneItem.TEXT -> {
                    textPaint.textSize = it.size * width * 0.35f
                    textPaint.color = it.color
                    textPaint.setShadowLayer(6f, 0f, 2f, Color.BLACK)
                    canvas.drawText(it.text, r.left, r.bottom - textPaint.textSize * 0.3f, textPaint)
                }
                else -> {
                    val bmp = if (it.type == SceneItem.PNGTUBER && talking && it.bmpB != null) it.bmpB else it.bmpA
                    if (bmp == null) {
                        paint.color = 0x55FFFFFF
                        canvas.drawRoundRect(r, 16f, 16f, paint)
                    } else {
                        canvas.save()
                        var dy = 0f
                        if (it.type == SceneItem.PNGTUBER) {
                            dy = -bounce * r.height() * 0.04f
                            animate = true
                        }
                        canvas.translate(0f, dy)
                        if (it.flip) canvas.scale(-1f, 1f, r.centerX(), r.centerY())
                        paint.color = Color.WHITE
                        canvas.drawBitmap(bmp, null, r, paint)
                        canvas.restore()
                    }
                }
            }
            if (editable && it === selected) canvas.drawRect(r, outline)
        }
        if (animate || editable) postInvalidateOnAnimation()
        else postInvalidateDelayed(66)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editable) return false
        scaler.onTouchEvent(e)
        if (scaler.isInProgress) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = items.lastOrNull { rectOf(it).contains(e.x, e.y) }
                selected = hit
                onSelect?.invoke(hit)
                dragging = hit
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_MOVE -> {
                val d = dragging
                if (d != null && e.pointerCount == 1) {
                    d.x = (d.x + (e.x - lastX) / width).coerceIn(0f, 1f)
                    d.y = (d.y + (e.y - lastY) / height).coerceIn(0f, 1f)
                    lastX = e.x; lastY = e.y
                    onChange?.invoke()
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = null
        }
        return true
    }
}
