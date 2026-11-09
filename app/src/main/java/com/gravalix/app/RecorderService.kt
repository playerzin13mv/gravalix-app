package com.gravalix.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max
import kotlin.math.min

/**
 * Serviço em primeiro plano que controla a gravação:
 * contagem regressiva, botão flutuante, notificação, pausar/continuar e parar.
 */
class RecorderService : Service() {

    companion object {
        const val ACTION_START = "com.gravalix.app.START"
        const val ACTION_STOP = "com.gravalix.app.STOP"
        const val ACTION_PAUSE_RESUME = "com.gravalix.app.PAUSE_RESUME"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_AUDIO_MODE = "audio_mode"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_SHOW_BUTTON = "show_button"
        const val EXTRA_LANDSCAPE = "landscape"

        const val AUDIO_NONE = 0
        const val AUDIO_INTERNAL = 1
        const val AUDIO_MIC = 2
        const val AUDIO_BOTH = 3

        const val QUALITY_HD = 0
        const val QUALITY_FHD = 1
        const val QUALITY_FHD60 = 2

        private const val CHANNEL_REC = "gravalix_rec"
        private const val CHANNEL_DONE = "gravalix_done"
        private const val NOTIF_ID = 1001
        private const val NOTIF_DONE_ID = 1002

        /** Verdadeiro desde o toque em Iniciar até o vídeo ser salvo. */
        @Volatile var isActive = false
    }

    private val handler = Handler(Looper.getMainLooper())

    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var recorder: ScreenRecorder? = null
    private var overlay: FloatingControls? = null

    private var countdownView: TextView? = null
    private var countdownTick: Runnable? = null
    private var pendingStart: Runnable? = null

    private var stopping = false
    private var paused = false
    private var audioMode = AUDIO_NONE
    private var quality = QUALITY_FHD
    private var showButton = true
    private var landscape = false

    private val ticker = object : Runnable {
        override fun run() {
            val rec = recorder
            if (rec != null) overlay?.setTime(rec.elapsedMs())
            handler.postDelayed(this, 500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE_RESUME -> togglePause()
            else -> if (!isActive) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeCountdownView()
        overlay?.remove()
        overlay = null
        val rec = recorder
        recorder = null
        if (rec != null) {
            Thread {
                try { rec.stop() } catch (_: Exception) {}
            }.start()
        }
        projectionCallback?.let { cb -> projection?.unregisterCallback(cb) }
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        isActive = false
        super.onDestroy()
    }

    // ------------------------------------------------------------------ início

    @Suppress("DEPRECATION")
    private fun readProjectionData(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        }

    private fun handleStart(intent: Intent) {
        if (isActive) return

        val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = readProjectionData(intent)
        audioMode = intent.getIntExtra(EXTRA_AUDIO_MODE, AUDIO_INTERNAL)
        quality = intent.getIntExtra(EXTRA_QUALITY, QUALITY_FHD)
        showButton = intent.getBooleanExtra(EXTRA_SHOW_BUTTON, true)
        landscape = intent.getBooleanExtra(EXTRA_LANDSCAPE, false)
        stopping = false
        paused = false

        createChannels()

        // O Android exige o serviço em primeiro plano ANTES de usar a projeção de tela.
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (Build.VERSION.SDK_INT >= 30 &&
            (audioMode == AUDIO_MIC || audioMode == AUDIO_BOTH)
        ) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        try {
            startForeground(NOTIF_ID, buildNotification(false), type)
        } catch (e: Exception) {
            toast(getString(R.string.toast_error_start))
            stopSelf()
            return
        }
        isActive = true

        if (data == null || code != Activity.RESULT_OK) {
            toast(getString(R.string.projection_denied))
            finishUp(null)
            return
        }

        try {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mpm.getMediaProjection(code, data)
            val cb = object : MediaProjection.Callback() {
                override fun onStop() {
                    // O usuário encerrou o compartilhamento pelo sistema
                    handler.post { if (!stopping) stopRecording() }
                }
            }
            // Obrigatório no Android 14+: registrar o callback antes de criar o display virtual
            proj.registerCallback(cb, handler)
            projection = proj
            projectionCallback = cb
        } catch (e: Exception) {
            toast(getString(R.string.toast_error_start))
            finishUp(null)
            return
        }

        runCountdown(Runnable { beginRecording() })
    }

    private fun runCountdown(onDone: Runnable) {
        if (!Settings.canDrawOverlays(this)) {
            toast(getString(R.string.toast_starting_3))
            pendingStart = onDone
            handler.postDelayed(onDone, 3000)
            return
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val tv = TextView(this).apply {
            textSize = 96f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setShadowLayer(16f, 0f, 0f, Color.BLACK)
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        try {
            wm.addView(tv, lp)
        } catch (e: Exception) {
            pendingStart = onDone
            handler.postDelayed(onDone, 3000)
            return
        }
        countdownView = tv

        var n = 3
        val tick = object : Runnable {
            override fun run() {
                if (n > 0) {
                    tv.text = n.toString()
                    n--
                    handler.postDelayed(this, 1000)
                } else {
                    removeCountdownView()
                    pendingStart = onDone
                    // pequena folga para o número sumir antes de começar a gravar
                    handler.postDelayed(onDone, 350)
                }
            }
        }
        countdownTick = tick
        handler.post(tick)
    }

    private fun removeCountdownView() {
        countdownTick?.let { handler.removeCallbacks(it) }
        countdownTick = null
        val v = countdownView ?: return
        countdownView = null
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun configFor(forceHd: Boolean): RecordConfig {
        val dm = DisplayMetrics()
        val display = (getSystemService(DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY)
        @Suppress("DEPRECATION")
        display.getRealMetrics(dm)

        val longSide = max(dm.widthPixels, dm.heightPixels)
        val shortSide = min(dm.widthPixels, dm.heightPixels)
        val hd = forceHd || quality == QUALITY_HD
        val targetShort = if (hd) 720f else 1080f
        val scale = min(1f, targetShort / shortSide.toFloat())

        val longScaled = ((longSide * scale).toInt() / 2) * 2
        val shortScaled = ((shortSide * scale).toInt() / 2) * 2

        val w = if (landscape) longScaled else shortScaled
        val h = if (landscape) shortScaled else longScaled

        val fps: Int
        val bitrate: Int
        if (hd) {
            fps = 30
            bitrate = 6_000_000
        } else if (quality == QUALITY_FHD) {
            fps = 30
            bitrate = 10_000_000
        } else {
            fps = 60
            bitrate = 14_000_000
        }
        return RecordConfig(w, h, dm.densityDpi, fps, bitrate)
    }

    private fun beginRecording() {
        pendingStart = null
        if (stopping) return
        val proj = projection ?: return

        val attempts = ArrayList<RecordConfig>()
        attempts.add(configFor(false))
        val fallback = configFor(true)
        if (fallback != attempts[0]) attempts.add(fallback)

        var started: ScreenRecorder? = null
        for (cfg in attempts) {
            val uri: Uri = try {
                ScreenRecorder.createOutput(this)
            } catch (e: Exception) {
                break
            }
            val candidate = ScreenRecorder(this, proj, cfg, audioMode, uri) { msg ->
                handler.post { toast(msg) }
            }
            try {
                candidate.start()
                started = candidate
                break
            } catch (e: Exception) {
                // tenta de novo com configuração menor (a próxima da lista)
            }
        }

        if (started == null) {
            toast(getString(R.string.toast_error_start))
            finishUp(null)
            return
        }

        recorder = started
        paused = false
        notificationManager().notify(NOTIF_ID, buildNotification(false))

        if (showButton && Settings.canDrawOverlays(this)) {
            val controls = FloatingControls(
                this,
                true,
                { togglePause() },
                { stopRecording() }
            )
            controls.show()
            overlay = controls
        }
        handler.post(ticker)
    }

    // ----------------------------------------------------------- pausar / parar

    private fun togglePause() {
        val rec = recorder ?: return
        if (paused) rec.resume() else rec.pause()
        paused = !paused
        overlay?.setPaused(paused)
        notificationManager().notify(NOTIF_ID, buildNotification(paused))
    }

    private fun stopRecording() {
        if (stopping) return
        stopping = true

        pendingStart?.let { handler.removeCallbacks(it) }
        pendingStart = null
        removeCountdownView()
        handler.removeCallbacks(ticker)
        overlay?.remove()
        overlay = null

        val rec = recorder
        recorder = null
        if (rec == null) {
            finishUp(null)
            return
        }
        Thread {
            val uri = try {
                rec.stop()
            } catch (e: Exception) {
                null
            }
            handler.post { finishUp(uri) }
        }.start()
    }

    private fun finishUp(uri: Uri?) {
        handler.removeCallbacks(ticker)
        removeCountdownView()
        overlay?.remove()
        overlay = null

        projectionCallback?.let { cb -> projection?.unregisterCallback(cb) }
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        projectionCallback = null

        isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)

        if (uri != null) {
            toast(getString(R.string.toast_saved))
            showSavedNotification()
        }
        stopSelf()
    }

    // ------------------------------------------------------------- notificações

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    private fun createChannels() {
        val nm = notificationManager()
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REC,
                getString(R.string.notif_channel_rec),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DONE,
                getString(R.string.notif_channel_done),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun buildNotification(paused: Boolean): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), flags
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, RecorderService::class.java).setAction(ACTION_STOP), flags
        )
        val toggle = PendingIntent.getService(
            this, 2,
            Intent(this, RecorderService::class.java).setAction(ACTION_PAUSE_RESUME), flags
        )

        val builder = Notification.Builder(this, CHANNEL_REC)
            .setSmallIcon(R.drawable.ic_stat_rec)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(if (paused) R.string.notif_paused else R.string.notif_recording))
            .setContentIntent(open)
            .setOngoing(true)

        if (recorder != null) {
            val icon = Icon.createWithResource(
                this,
                if (paused) R.drawable.ic_action_play else R.drawable.ic_action_pause
            )
            val label = getString(if (paused) R.string.notif_resume else R.string.notif_pause)
            builder.addAction(Notification.Action.Builder(icon, label, toggle).build())
        }
        builder.addAction(
            Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_action_stop),
                getString(R.string.notif_stop),
                stop
            ).build()
        )
        return builder.build()
    }

    private fun showSavedNotification() {
        val open = PendingIntent.getActivity(
            this, 3, Intent(this, RecordingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_rec)
            .setContentTitle(getString(R.string.notif_saved_title))
            .setContentText(getString(R.string.notif_saved_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            notificationManager().notify(NOTIF_DONE_ID, n)
        } catch (_: Exception) {
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }
}
