package com.gravalix.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Bolha flutuante do Gravalix. Fica por cima dos apps ANTES e DEPOIS da gravação
 * (durante a gravação ela some, por padrão, para nunca aparecer no vídeo).
 *
 * - Fica encaixada só nas laterais da tela; ao soltar, ela gruda na borda mais perto.
 * - Ao arrastar aparece uma lixeira embaixo: solte a bolha nela para fechá-la.
 * - Toque na bolha para abrir o menu: gravar/parar, pausar, caneta, toques, abrir app, fechar.
 * - Tamanho, opacidade, cor e botões do menu são configuráveis.
 */
class BubbleService : Service() {

    companion object {
        const val ACTION_CLOSE = "com.gravalix.app.BUBBLE_CLOSE"
        const val ACTION_RELOAD = "com.gravalix.app.BUBBLE_RELOAD"
        private const val CHANNEL = "gravalix_bubble"
        private const val NOTIF_ID = 1003

        /** Cores da bolha: roxo, vermelho, verde, azul, laranja, cinza. */
        val COLORS = intArrayOf(
            0xFF8781FF.toInt(), 0xFFFF5470.toInt(), 0xFF30D158.toInt(),
            0xFF0A84FF.toInt(), 0xFFFF9F0A.toInt(), 0xFF48484A.toInt()
        )
        private val SIZES_DP = intArrayOf(44, 56, 68)
        private val ALPHAS = floatArrayOf(1f, 0.8f, 0.6f, 0.4f)

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

        /** Aplica na hora o que mudou na personalização. */
        fun reload(c: Context) {
            if (!isRunning) return
            try {
                c.startService(Intent(c, BubbleService::class.java).setAction(ACTION_RELOAD))
            } catch (_: Exception) {
            }
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
    private var dockRight = true

    // lixeira (aparece só enquanto arrasta)
    private var trash: IconButton? = null
    private var trashHot = false

    // caneta
    private var canvas: PenCanvas? = null
    private var canvasParams: WindowManager.LayoutParams? = null
    private var toolbar: LinearLayout? = null
    private var toolbarParams: WindowManager.LayoutParams? = null
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
        when (intent?.action) {
            ACTION_CLOSE -> {
                Prefs.setBubble(this, false)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                if (root != null) rebuild()
                return START_STICKY
            }
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        handler.postDelayed({ clampY() }, 300)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hideTrash()
        hidePen()
        root?.let { removeWindow(it) }
        root = null
        isRunning = false
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    private fun screen(): IntArray {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        return intArrayOf(dm.widthPixels, dm.heightPixels)
    }

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

    /** Só usado no modo experimental "tentar esconder do vídeo". */
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

    private fun baseColor(): Int {
        val c = COLORS[Prefs.bubbleColor(this).coerceIn(0, COLORS.size - 1)]
        return (c and 0x00FFFFFF) or 0xE6000000.toInt()
    }

    private fun buildBubble() {
        dockRight = Prefs.bubbleRight(this)
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        root = r

        bubbleBtn = IconButton(this, IconButton.CAMERA, baseColor())
        bubbleBtn.alpha = ALPHAS[Prefs.bubbleAlpha(this).coerceIn(0, ALPHAS.size - 1)]
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
        for (b in listOf(recBtn, pauseBtn, penBtn, touchBtn, homeBtn, closeBtn)) b.visibility = View.GONE

        penBtn.tint = PenCanvas.COLORS[Prefs.penColor(this) % PenCanvas.COLORS.size]
        penBtn.active = penOn
        touchBtn.active = Prefs.showTouches(this)

        layoutChildren()

        val sy = Prefs.bubbleY(this)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags(wantSecure()),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or (if (dockRight) Gravity.END else Gravity.START)
            x = 0
            y = if (sy >= 0) sy else dp(220)
        }
        addWindow(r, params)
        firstRun = true
        refresh()
        clampSoon()
    }

    /** Recria a bolha (usado quando a personalização muda). */
    private fun rebuild() {
        handler.removeCallbacks(autoCollapse)
        hideTrash()
        root?.let { removeWindow(it) }
        root = null
        expanded = false
        buildBubble()
    }

    /** Bolha no lado de dentro e o menu abrindo para o lado de fora da tela. */
    private fun layoutChildren() {
        val r = root ?: return
        r.removeAllViews()
        val size = dp(SIZES_DP[Prefs.bubbleSize(this).coerceIn(0, SIZES_DP.size - 1)])
        val menu = listOf(recBtn, pauseBtn, penBtn, touchBtn, homeBtn, closeBtn)
        val ordered = if (dockRight) menu.reversed() else menu
        if (!dockRight) r.addView(bubbleBtn, LinearLayout.LayoutParams(size, size))
        for (b in ordered) {
            r.addView(b, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
        }
        if (dockRight) r.addView(bubbleBtn, LinearLayout.LayoutParams(size, size))
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
        recBtn.visibility = if (value) View.VISIBLE else View.GONE
        penBtn.visibility = if (value && Prefs.menuPen(this)) View.VISIBLE else View.GONE
        touchBtn.visibility = if (value && Prefs.menuTouches(this)) View.VISIBLE else View.GONE
        homeBtn.visibility = if (value && Prefs.menuHome(this)) View.VISIBLE else View.GONE
        closeBtn.visibility = if (value) View.VISIBLE else View.GONE
        pauseBtn.visibility = if (value && RecorderService.isRecording) View.VISIBLE else View.GONE
        r.background = if (value) {
            GradientDrawable().apply {
                setColor(0xE61B1830.toInt())
                cornerRadius = dp(32).toFloat()
            }
        } else {
            null
        }
        clampSoon()
        scheduleCollapse()
    }

    /** Mantém a bolha dentro da tela (só na vertical: na horizontal ela já fica na borda). */
    private fun clampY() {
        val r = root ?: return
        if (r.height == 0) return
        val sh = screen()[1]
        val ny = params.y.coerceIn(0, max(0, sh - r.height))
        if (ny != params.y) {
            params.y = ny
            updateWindow(r, params)
        }
    }

    private fun clampSoon() {
        handler.postDelayed({ clampY() }, 80)
        handler.postDelayed({ clampY() }, 300)
    }

    private inner class DragTap : View.OnTouchListener {
        private var baseLeft = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val r = root ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val loc = IntArray(2)
                    bubbleBtn.getLocationOnScreen(loc)
                    baseLeft = loc[0] - dp(4) // posição da bolha, sem contar o menu
                    startY = params.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX
                    val dy = e.rawY - touchY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
                        moved = true
                        // começa a arrastar: fecha o menu e passa a posicionar pela esquerda
                        handler.removeCallbacks(autoCollapse)
                        expanded = false
                        recBtn.visibility = View.GONE
                        pauseBtn.visibility = View.GONE
                        penBtn.visibility = View.GONE
                        touchBtn.visibility = View.GONE
                        homeBtn.visibility = View.GONE
                        closeBtn.visibility = View.GONE
                        r.background = null
                        params.gravity = Gravity.TOP or Gravity.START
                        showTrash()
                    }
                    if (moved) {
                        params.x = baseLeft + dx.toInt()
                        params.y = startY + dy.toInt()
                        updateWindow(r, params)
                        updateTrash()
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (moved) {
                        val over = e.actionMasked == MotionEvent.ACTION_UP && isOverTrash()
                        hideTrash()
                        if (over) removeBubble() else snapToSide()
                    } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                        setExpanded(!expanded)
                    }
                    return true
                }
            }
            return false
        }
    }

    /** Ao soltar, a bolha gruda na lateral mais perto. */
    private fun snapToSide() {
        val r = root ?: return
        val sw = screen()[0]
        val centerX = params.x + dp(4) + bubbleBtn.width / 2
        dockRight = centerX > sw / 2
        layoutChildren()
        params.gravity = Gravity.TOP or (if (dockRight) Gravity.END else Gravity.START)
        params.x = 0
        updateWindow(r, params)
        clampY()
        Prefs.setBubbleSide(this, dockRight)
        Prefs.setBubbleY(this, params.y)
        clampSoon()
    }

    // ------------------------------------------------------------------ lixeira

    private fun showTrash() {
        if (trash != null) return
        val t = IconButton(this, IconButton.TRASH, TRASH_COLOR)
        val p = WindowManager.LayoutParams(
            dp(64),
            dp(64),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags(wantSecure()) or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(56)
        }
        trash = t
        trashHot = false
        addWindow(t, p)
    }

    private val TRASH_COLOR = 0xE62C2A45.toInt()

    private fun hideTrash() {
        trash?.let { removeWindow(it) }
        trash = null
        trashHot = false
    }

    private fun isOverTrash(): Boolean {
        val s = screen()
        val tx = s[0] / 2
        val ty = s[1] - dp(56) - dp(32)
        val loc = IntArray(2)
        bubbleBtn.getLocationOnScreen(loc)
        val bx = loc[0] + bubbleBtn.width / 2
        val by = loc[1] + bubbleBtn.height / 2
        val dx = (bx - tx).toDouble()
        val dy = (by - ty).toDouble()
        return sqrt(dx * dx + dy * dy) < dp(80)
    }

    private fun updateTrash() {
        val hot = isOverTrash()
        if (hot == trashHot) return
        trashHot = hot
        val t = trash ?: return
        t.circleColor = if (hot) 0xFFFF2D55.toInt() else TRASH_COLOR
        val scale = if (hot) 1.3f else 1f
        t.scaleX = scale
        t.scaleY = scale
    }

    private fun removeBubble() {
        Prefs.setBubble(this, false)
        Toast.makeText(applicationContext, R.string.toast_bubble_removed, Toast.LENGTH_LONG).show()
        stopSelf()
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
            else -> baseColor()
        }
        touchBtn.active = Prefs.showTouches(this)

        val hideAll = lastBusy && Prefs.bubbleMode(this) == Prefs.BUBBLE_GONE
        r.visibility = if (hideAll) View.GONE else View.VISIBLE
        toolbar?.visibility = if (hideAll) View.GONE else View.VISIBLE
        if (hideAll) {
            hideTrash()
            if (expanded) setExpanded(false)
        }

        val secure = wantSecure()
        setSecure(r, params, secure)
        val tb = toolbar
        val tbp = toolbarParams
        if (tb != null && tbp != null) setSecure(tb, tbp, secure)
        clampSoon()
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
        val trashBtn = IconButton(this, IconButton.TRASH)
        trashBtn.setOnClickListener { canvas?.clear() }
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

        for (b in listOf(color, undo, trashBtn, fade, mode, close)) {
            bar.addView(b, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            })
        }
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
