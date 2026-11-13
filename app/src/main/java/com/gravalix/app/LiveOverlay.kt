package com.gravalix.app

import android.content.Context
import android.graphics.PixelFormat
import android.view.WindowManager

/** Coloca a cena da Live por cima de tudo, para ela entrar no vídeo e na transmissão. */
object LiveOverlay {
    private var view: SceneView? = null
    private var meter: AudioMeter? = null
    private var wm: WindowManager? = null

    fun show(c: Context) {
        hide()
        val app = c.applicationContext
        if (!android.provider.Settings.canDrawOverlays(app)) return
        try {
            val items = LiveScene.load(app)
            if (items.isEmpty()) return
            val manager = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val v = SceneView(app, items, false)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            manager.addView(v, lp)
            wm = manager
            view = v
        } catch (e: Exception) {
            AppLog.e("cena: overlay", e)
        }
    }

    fun hide() {
        try { view?.let { wm?.removeView(it) } } catch (_: Exception) {}
        view = null
        meter?.stop()
        meter = null
    }
}
