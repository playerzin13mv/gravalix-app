package com.gravalix.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Barrinha flutuante (tempo + pausar + parar) que aparece por cima dos outros apps.
 *
 * Com [hideFromVideo] ligado a janela recebe FLAG_SECURE, que pede ao Android para
 * não incluí-la em capturas de tela / gravações. Funciona na maioria dos aparelhos;
 * em alguns pode aparecer um quadrado escuro no lugar. Por isso existe a opção de
 * desligar o botão e controlar pela notificação.
 */
class FloatingControls(
    private val context: Context,
    private val hideFromVideo: Boolean,
    private val onPauseResume: () -> Unit,
    private val onStop: () -> Unit
) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private var root: LinearLayout? = null
    private var timerView: TextView? = null
    private var pauseIcon: IconView? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        windowFlags(),
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(16)
        y = dp(140)
    }

    private fun windowFlags(): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (hideFromVideo) f = f or WindowManager.LayoutParams.FLAG_SECURE
        return f
    }

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    fun show() {
        if (root != null) return

        val background = GradientDrawable().apply {
            setColor(0xE61B1830.toInt())
            cornerRadius = dp(28).toFloat()
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            this.background = background
            setPadding(dp(14), dp(6), dp(8), dp(6))
        }

        val timer = TextView(context).apply {
            text = "00:00"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dp(10), 0)
        }
        timerView = timer

        val pause = makeButton(IconView.PAUSE, 0xFF3A3658.toInt()) { onPauseResume() }
        pauseIcon = pause.first
        val stop = makeButton(IconView.STOP, 0xFFFF5470.toInt()) { onStop() }

        row.addView(timer)
        row.addView(pause.second)
        row.addView(stop.second)

        val drag = DragListener()
        row.setOnTouchListener(drag)
        timer.setOnTouchListener(drag)

        try {
            wm.addView(row, params)
            root = row
        } catch (e: Exception) {
            root = null
        }
    }

    private fun makeButton(kind: Int, color: Int, click: () -> Unit): Pair<IconView, View> {
        val size = dp(40)
        val icon = IconView(context, kind)
        icon.setOnClickListener { click() }
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        icon.background = circle
        val lp = LinearLayout.LayoutParams(size, size).apply { marginStart = dp(6) }
        icon.layoutParams = lp
        return Pair(icon, icon)
    }

    fun setTime(ms: Long) {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        timerView?.text = if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    fun setPaused(paused: Boolean) {
        pauseIcon?.kind = if (paused) IconView.PLAY else IconView.PAUSE
    }

    fun remove() {
        val r = root ?: return
        try {
            wm.removeView(r)
        } catch (_: Exception) {
        }
        root = null
    }

    private inner class DragListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = e.rawX
                    touchY = e.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    val r = root
                    if (r != null) {
                        try {
                            wm.updateViewLayout(r, params)
                        } catch (_: Exception) {
                        }
                    }
                    return true
                }
            }
            return false
        }
    }

    /** Ícone desenhado à mão (pausar, continuar, parar) para não depender de fontes. */
    private class IconView(context: Context, initialKind: Int) : View(context) {

        companion object {
            const val PAUSE = 0
            const val PLAY = 1
            const val STOP = 2
        }

        var kind: Int = initialKind
            set(value) {
                field = value
                invalidate()
            }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val pad = w * 0.30f
            when (kind) {
                PAUSE -> {
                    val barW = (w - 2 * pad) / 3f
                    canvas.drawRect(pad, pad, pad + barW, h - pad, paint)
                    canvas.drawRect(w - pad - barW, pad, w - pad, h - pad, paint)
                }
                PLAY -> {
                    path.reset()
                    path.moveTo(pad + w * 0.04f, pad)
                    path.lineTo(w - pad + w * 0.04f, h / 2f)
                    path.lineTo(pad + w * 0.04f, h - pad)
                    path.close()
                    canvas.drawPath(path, paint)
                }
                else -> {
                    val r = w * 0.06f
                    canvas.drawRoundRect(pad, pad, w - pad, h - pad, r, r, paint)
                }
            }
        }
    }
}
