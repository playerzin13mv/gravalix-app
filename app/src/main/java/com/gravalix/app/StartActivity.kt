package com.gravalix.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/**
 * Tela invisível que pede o que falta (permissões e a autorização de gravar a tela)
 * e então inicia a gravação. É usada pelo botão "Iniciar gravação", pela bolha
 * flutuante e pelo botão do painel rápido.
 */
class StartActivity : Activity() {

    companion object {
        const val EXTRA_GO_HOME = "go_home"
        private const val REQ_PERMS = 1
        private const val REQ_PROJECTION = 2
        private const val REQ_OVERLAY = 3
    }

    private var askedOverlay = false

    private fun isLive() = intent.getBooleanExtra(RecorderService.EXTRA_LIVE, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (RecorderService.isActive) {
            finish()
            return
        }
        next()
    }

    private fun next() {
        // 1) Aparecer sobre outros apps (bolha e contagem)
        val needOverlay = Prefs.bubble(this) || Prefs.countdown(this) > 0 || isLive()
        if (needOverlay && !Settings.canDrawOverlays(this) && !askedOverlay) {
            askedOverlay = true
            Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show()
            try {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                    REQ_OVERLAY
                )
                return
            } catch (_: Exception) {
            }
        }

        // 2) Microfone/áudio e notificações
        val needed = ArrayList<String>()
        if ((Prefs.audio(this) != RecorderService.AUDIO_NONE || isLive()) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), REQ_PERMS)
            return
        }

        // 3) Autorização do Android para gravar a tela
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMS) return
        if ((Prefs.audio(this) != RecorderService.AUDIO_NONE || isLive()) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, R.string.need_mic, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        next()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_OVERLAY -> next()
            REQ_PROJECTION -> {
                if (resultCode != RESULT_OK || data == null) {
                    Toast.makeText(this, R.string.projection_denied, Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
                val intent = Intent(this, RecorderService::class.java).apply {
                    action = RecorderService.ACTION_START
                    putExtra(RecorderService.EXTRA_LIVE, isLive())
                    putExtra(RecorderService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(RecorderService.EXTRA_RESULT_DATA, data)
                }
                startForegroundService(intent)

                if (Prefs.bubble(this) && Settings.canDrawOverlays(this) && !BubbleService.isRunning) {
                    BubbleService.start(this)
                }
                if (getIntent().getBooleanExtra(EXTRA_GO_HOME, false)) {
                    startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                finish()
            }
        }
    }
}
