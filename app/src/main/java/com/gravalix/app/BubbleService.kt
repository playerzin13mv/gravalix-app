package com.gravalix.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Bolha flutuante do Gravalix. Fica por cima dos apps ANTES e DURANTE a gravação.
 *
 * Toque na bolha para abrir o menu: gravar/parar, pausar, caneta, mostrar toques,
 * abrir o app e fechar a bolha. Dá para arrastar.
 *
 * Durante a gravação, a bolha e a barra da caneta usam FLAG_SECURE: o Android não
 * as inclui no vídeo. O desenho da caneta, ao contrário, aparece de propósito.
 */
class BubbleService : Service() {

    companion object {
        const val ACTION_CLOSE = "com.gravalix.app.BUBBLE_CLOSE"
        private const val CHANNEL = "gravalix_bubble"
        private const val NOTIF_ID = 1003

        @Volatile var isRunning = false

        fun start(c: Context) {
            if (!Settings.canDrawOverlays(c)) return
            try {
                c.startForegroundService(Intent(c, BubbleService::class.java))
            } catch (_: Exception) {
            }
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, BubbleService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var density = 1f
    private var slop = 8

    // bolha + menu
    private var root: LinearLayout? = null
    private lateinit var bubbleBtn: IconButton
    private lateinit var recBtn: IconButton
    private lateinit var pauseBtn: IconButton
    private lateinit var penBtn: IconButton
    private lateinit var touchBtn: IconButton
    private lateinit var homeBtn: IconButton
    private lateinit var closeBtn: IconButton
    private lateinit var params: WindowManager.LayoutParams
    private var expanded = false

    // caneta
    private var canvas: PenCanvas? = null
    private var canvasParams: WindowManager.LayoutParams? = null
    private var toolbar: LinearLayout? = null
    private var toolbarParams: WindowManager.LayoutParams? = null
    private var colorBtn: IconButton? = null
    private var fadeBtn: IconButton? = null
    private var modeBtn: IconButton? = null
    private var penOn = false
    private var passThrough = false

    // último estado conhecido da gravação
    private var lastBusy = false
    private var lastRec = false
    private var lastPaused = false
    private var firstRun = true

    private val poll = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 400)
        }
    }

    private val autoCollapse = Runnable { if (expanded) setExpanded(false) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        density = resources.displayMetrics.density
        slop = ViewConfiguration.get(this).scaledTouchSlop
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            Prefs.setBubble(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this) || (intent == null && !Prefs.bubble(this))) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (root == null) buildBubble()
        isRunning = true
        handler.removeCallbacks(poll)
        handler.post(poll)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hidePen()
        root?.let { removeWindow(it) }
        root = null
        isRunning = false
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    // ---------------------------------------------------------------- notificação

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel_bubble), NotificationManager.IMPORTANCE_MIN)
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), flags)
        val close = PendingIntent.getService(
            this, 11, Intent(this, BubbleService::class.java).setAction(ACTION_CLOSE), flags
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rec)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_bubble_on))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_action_stop),
                    getString(R.string.notif_bubble_close),
                    close
                ).build()
            )
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ----------------------------------------------------------------- janelas

    private fun baseFlags(secure: Boolean): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (secure) f = f or WindowManager.LayoutParams.FLAG_SECURE
        return f
    }

    private fun wantSecure(): Boolean =
        RecorderService.isActive && Prefs.bubbleMode(this) == Prefs.BUBBLE_HIDDEN_FROM_VIDEO

    private fun addWindow(v: View, p: WindowManager.LayoutParams) {
        try {
            wm.addView(v, p)
        } catch (_: Exception) {
        }
    }

    private fun removeWindow(v: View) {
        try {
            wm.removeViewImmediate(v)
        } catch (_: Exception) {
        }
    }

    private fun updateWindow(v: View, p: WindowManager.LayoutParams) {
        try {
            wm.updateViewLayout(v, p)
        } catch (_: Exception) {
        }
    }

    // -------------------------------------------------------------------- bolha

    private fun buildBubble() {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        bubbleBtn = IconButton(this, IconButton.CAMERA, 0xE68781FF.toInt())
        r.addView(bubbleBtn, LinearLayout.LayoutParams(dp(52), dp(52)))
        bubbleBtn.setOnTouchListener(DragTap())

        recBtn = menuButton(IconButton.REC) { onRecClick() }
        pauseBtn = menuButton(IconButton.PAUSE) { sendToRecorder(RecorderService.ACTION_PAUSE_RESUME) }
        penBtn = menuButton(IconButton.PEN) { setPen(!penOn) }
        touchBtn = menuButton(IconButton.TOUCH) {
            touchBtn.active = TouchIndicator.toggleFromUser(this)
        }
        homeBtn = menuButton(IconButton.HOME) {
            setExpanded(false)
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        closeBtn = menuButton(IconButton.CLOSE) {
            Prefs.setBubble(this, false)
            stopSelf()
        }
        for (b in listOf(recBtn, pauseBtn, penBtn, touchBtn, homeBtn, closeBtn)) {
            r.addView(b, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(6) })
            b.visibility = View.GONE
        }
        penBtn.tint = PenCanvas.COLORS[Prefs.penColor(this) % PenCanvas.COLORS.size]
        touchBtn.active = Prefs.showTouches(this)

        val sw = resources.displayMetrics.widthPixels
        val sx = Prefs.bubbleX(this)
        val sy = Prefs.bubbleY(this)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags(wantSecure()),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (sx >= 0) sx else sw - dp(64)
            y = if (sy >= 0) sy else dp(220)
        }
        root = r
        addWindow(r, params)
        firstRun = true
        refresh()
    }

    private fun menuButton(icon: Int, click: () -> Unit): IconButton {
        val b = IconButton(this, icon)
        b.setOnClickListener {
            click()
            scheduleCollapse()
        }
        return b
    }

    private fun scheduleCollapse() {
        handler.removeCallbacks(autoCollapse)
        if (expanded) handler.postDelayed(autoCollapse, 7000)
    }

    private fun setExpanded(value: Boolean) {
        expanded = value
        val r = root ?: return
        val vis = if (value) View.VISIBLE else View.GONE
        recBtn.visibility = vis
        penBtn.visibility = vis
        touchBtn.visibility = vis
        homeBtn.visibility = vis
        closeBtn.visibility = vis
        pauseBtn.visibility = if (value && RecorderService.isRecording) View.VISIBLE else View.GONE
        r.background = if (value) {
            GradientDrawable().apply {
                setColor(0xE61B1830.toInt())
                cornerRadius = dp(32).toFloat()
            }
        } else {
            null
        }
        r.post { clampToScreen() }
        scheduleCollapse()
    }

    private fun clampToScreen() {
        val r = root ?: return
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels
        val w = r.width
        val h = r.height
        if (w == 0) return
        params.x = params.x.coerceIn(0, maxOf(0, sw - w))
        params.y = params.y.coerceIn(0, maxOf(0, sh - h))
        updateWindow(r, params)
    }

    private inner class DragTap : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val r = root ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX
                    val dy = e.rawY - touchY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        updateWindow(r, params)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        clampToScreen()
                        Prefs.setBubblePos(this@BubbleService, params.x, params.y)
                    } else {
                        setExpanded(!expanded)
                    }
                    return true
                }
            }
            return false
        }
    }

    // ------------------------------------------------------------------- ações

    private fun onRecClick() {
        if (RecorderService.isActive) {
            sendToRecorder(RecorderService.ACTION_STOP)
        } else {
            setExpanded(false)
            startActivity(
                Intent(this, StartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun sendToRecorder(action: String) {
        try {
            startService(Intent(this, RecorderService::class.java).setAction(action))
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------ estado

    private fun refresh() {
        val busy = RecorderService.isActive
        val rec = RecorderService.isRecording
        val paused = RecorderService.isPaused
        if (!firstRun && busy == lastBusy && rec == lastRec && paused == lastPaused) return
        firstRun = false
        lastBusy = busy
        lastRec = rec
        lastPaused = paused
        applyState()
    }

    private fun applyState() {
        val r = root ?: return
        recBtn.icon = if (lastBusy) IconButton.STOP else IconButton.REC
        pauseBtn.icon = if (lastPaused) IconButton.PLAY else IconButton.PAUSE
        pauseBtn.visibility = if (expanded && lastRec) View.VISIBLE else View.GONE
        bubbleBtn.circleColor = when {
            lastRec && lastPaused -> 0xE6777777.toInt()
            lastRec -> 0xE6FF5470.toInt()
            else -> 0xE68781FF.toInt()
        }
        touchBtn.active = Prefs.showTouches(this)

        val hideAll = lastBusy && Prefs.bubbleMode(this) == Prefs.BUBBLE_GONE
        r.visibility = if (hideAll) View.GONE else View.VISIBLE
        toolbar?.visibility = if (hideAll) View.GONE else View.VISIBLE

        val secure = wantSecure()
        setSecure(r, params, secure)
        val tb = toolbar
        val tbp = toolbarParams
        if (tb != null && tbp != null) setSecure(tb, tbp, secure)
        r.post { clampToScreen() }
    }

    private fun setSecure(v: View, p: WindowManager.LayoutParams, secure: Boolean) {
        val has = (p.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        if (has == secure) return
        p.flags = if (secure) {
            p.flags or WindowManager.LayoutParams.FLAG_SECURE
        } else {
            p.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
        }
        updateWindow(v, p)
    }

    // ------------------------------------------------------------------- caneta

    private fun setPen(on: Boolean) {
        if (on == penOn) return
        penOn = on
        penBtn.active = on
        if (on) showPen() else hidePen()
    }

    private fun showPen() {
        val c = PenCanvas(this).apply {
            color = PenCanvas.COLORS[Prefs.penColor(this@BubbleService) % PenCanvas.COLORS.size]
            fade = Prefs.penFade(this@BubbleService)
        }
        passThrough = false
        val cp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        canvas = c
        canvasParams = cp
        addWindow(c, cp)

        buildToolbar()
        bringBubbleToFront()
    }

    private fun buildToolbar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                setColor(0xE61B1830.toInt())
                cornerRadius = dp(30).toFloat()
            }
        }
        val color = IconButton(this, IconButton.PEN)
        color.tint = PenCanvas.COLORS[Prefs.penColor(this) % PenCanvas.COLORS.size]
        color.setOnClickListener {
            val next = (Prefs.penColor(this) + 1) % PenCanvas.COLORS.size
            Prefs.setPenColor(this, next)
            color.tint = PenCanvas.COLORS[next]
            penBtn.tint = PenCanvas.COLORS[next]
            canvas?.color = PenCanvas.COLORS[next]
        }
        val undo = IconButton(this, IconButton.UNDO)
        undo.setOnClickListener { canvas?.undo() }
        val trash = IconButton(this, IconButton.TRASH)
        trash.setOnClickListener { canvas?.clear() }
        val fade = IconButton(this, IconButton.FADE)
        fade.active = Prefs.penFade(this)
        fade.setOnClickListener {
            val v = !Prefs.penFade(this)
            Prefs.setPenFade(this, v)
            fade.active = v
            canvas?.fade = v
        }
        val mode = IconButton(this, IconButton.HAND)
        mode.setOnClickListener { setPassThrough(!passThrough) }
        val close = IconButton(this, IconButton.CLOSE)
        close.setOnClickListener { setPen(false) }

        for (b in listOf(color, undo, trash, fade, mode, close)) {
            bar.addView(b, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginStart = dp(4); marginEnd = dp(4) })
        }
        colorBtn = color
        fadeBtn = fade
        modeBtn = mode

        val bp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags(wantSecure()),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }
        toolbar = bar
        toolbarParams = bp
        addWindow(bar, bp)
    }

    /** Modo "mão": você mexe nos apps normalmente e o desenho continua na tela. */
    private fun setPassThrough(value: Boolean) {
        passThrough = value
        modeBtn?.active = value
        val c = canvas ?: return
        val p = canvasParams ?: return
        p.flags = if (value) {
            p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        updateWindow(c, p)
    }

    private fun hidePen() {
        toolbar?.let { removeWindow(it) }
        toolbar = null
        toolbarParams = null
        canvas?.let { removeWindow(it) }
        canvas = null
        canvasParams = null
        penOn = false
        passThrough = false
        if (root != null) penBtn.active = false
    }

    /** A janela mais nova fica por cima: recoloca a bolha depois da caneta. */
    private fun bringBubbleToFront() {
        val r = root ?: return
        removeWindow(r)
        addWindow(r, params)
    }
}
