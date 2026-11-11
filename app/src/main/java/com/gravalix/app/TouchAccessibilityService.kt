package com.gravalix.app

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * "Mostrar toques" do próprio Gravalix.
 *
 * O Android não deixa um app comum ver onde o dedo toca dentro de OUTROS apps. O que um
 * serviço de acessibilidade consegue é ser avisado quando algo é tocado (botão, item da
 * lista, link...) e saber onde esse elemento está. O Gravalix desenha um círculo no centro
 * do que foi tocado, só enquanto você está gravando.
 *
 * Não lê nem guarda nenhum conteúdo da tela: só usa a posição do que foi tocado.
 */
class TouchAccessibilityService : AccessibilityService() {

    companion object {
        /** Verdadeiro enquanto o serviço está ligado nas configurações de Acessibilidade. */
        @Volatile var connected = false
    }

    private lateinit var wm: WindowManager
    private var density = 1f

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        density = resources.displayMetrics.density
        connected = true
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        connected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = false
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !connected) return
        if (!RecorderService.isRecording || RecorderService.isPaused) return
        if (!Prefs.showTouches(this)) return
        // Se o sistema já está mostrando os toques (permissão especial), não duplica
        if (TouchIndicator.canToggle(this)) return

        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
        ) return

        val node = event.source ?: return
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return
        ripple(bounds.centerX(), bounds.centerY(), event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)
    }

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    /** Círculo que cresce e some no ponto tocado. */
    private fun ripple(cx: Int, cy: Int, long: Boolean) {
        val size = dp(if (long) 110 else 88)
        val v = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66FFFFFF)
                setStroke(dp(3), 0xCCFFFFFF.toInt())
            }
            scaleX = 0.35f
            scaleY = 0.35f
            alpha = 1f
        }
        val p = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cx - size / 2
            y = cy - size / 2
        }
        try {
            wm.addView(v, p)
        } catch (_: Exception) {
            return
        }
        v.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(0f)
            .setDuration(if (long) 700L else 450L)
            .withEndAction {
                try {
                    wm.removeView(v)
                } catch (_: Exception) {
                }
            }
            .start()
    }
}
