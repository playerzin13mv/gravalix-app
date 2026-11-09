package com.gravalix.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast

class MainActivity : Activity() {

    private companion object {
        const val REQ_PERMS = 10
        const val REQ_PROJECTION = 11
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var audioGroup: RadioGroup
    private lateinit var qualityGroup: RadioGroup
    private lateinit var switchButton: Switch
    private lateinit var switchLandscape: Switch
    private lateinit var btnStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("gravalix", MODE_PRIVATE)
        audioGroup = findViewById(R.id.audioGroup)
        qualityGroup = findViewById(R.id.qualityGroup)
        switchButton = findViewById(R.id.switchButton)
        switchLandscape = findViewById(R.id.switchLandscape)
        btnStart = findViewById(R.id.btnStart)

        restorePrefs()

        btnStart.setOnClickListener {
            if (RecorderService.isActive) stopRecording() else startFlow()
        }
        findViewById<Button>(R.id.btnRecordings).setOnClickListener {
            startActivity(Intent(this, RecordingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStartButton()
    }

    private fun updateStartButton() {
        btnStart.setText(if (RecorderService.isActive) R.string.btn_stop else R.string.btn_start)
    }

    // ------------------------------------------------------------- preferências

    private fun restorePrefs() {
        val audio = when (prefs.getInt("audio", RecorderService.AUDIO_INTERNAL)) {
            RecorderService.AUDIO_MIC -> R.id.rbMic
            RecorderService.AUDIO_BOTH -> R.id.rbBoth
            RecorderService.AUDIO_NONE -> R.id.rbMute
            else -> R.id.rbInternal
        }
        audioGroup.check(audio)

        val quality = when (prefs.getInt("quality", RecorderService.QUALITY_FHD)) {
            RecorderService.QUALITY_HD -> R.id.rbHd
            RecorderService.QUALITY_FHD60 -> R.id.rbFhd60
            else -> R.id.rbFhd
        }
        qualityGroup.check(quality)

        switchButton.isChecked = prefs.getBoolean("floating", true)
        switchLandscape.isChecked = prefs.getBoolean("landscape", false)
    }

    private fun selectedAudioMode(): Int = when (audioGroup.checkedRadioButtonId) {
        R.id.rbMic -> RecorderService.AUDIO_MIC
        R.id.rbBoth -> RecorderService.AUDIO_BOTH
        R.id.rbMute -> RecorderService.AUDIO_NONE
        else -> RecorderService.AUDIO_INTERNAL
    }

    private fun selectedQuality(): Int = when (qualityGroup.checkedRadioButtonId) {
        R.id.rbHd -> RecorderService.QUALITY_HD
        R.id.rbFhd60 -> RecorderService.QUALITY_FHD60
        else -> RecorderService.QUALITY_FHD
    }

    private fun savePrefs() {
        prefs.edit()
            .putInt("audio", selectedAudioMode())
            .putInt("quality", selectedQuality())
            .putBoolean("floating", switchButton.isChecked)
            .putBoolean("landscape", switchLandscape.isChecked)
            .apply()
    }

    // ------------------------------------------------------------------- fluxo

    private fun startFlow() {
        savePrefs()

        // 1) Permissão para aparecer por cima de outros apps (botão flutuante e contagem)
        if (switchButton.isChecked && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        // 2) Microfone/áudio e notificações
        val needed = ArrayList<String>()
        if (selectedAudioMode() != RecorderService.AUDIO_NONE &&
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

        // 3) Permissão de gravação de tela do Android
        requestProjection()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMS) return

        if (selectedAudioMode() != RecorderService.AUDIO_NONE &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, R.string.need_mic, Toast.LENGTH_LONG).show()
            return
        }
        requestProjection()
    }

    private fun requestProjection() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PROJECTION) return

        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, R.string.projection_denied, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, RecorderService::class.java).apply {
            action = RecorderService.ACTION_START
            putExtra(RecorderService.EXTRA_RESULT_CODE, resultCode)
            putExtra(RecorderService.EXTRA_RESULT_DATA, data)
            putExtra(RecorderService.EXTRA_AUDIO_MODE, selectedAudioMode())
            putExtra(RecorderService.EXTRA_QUALITY, selectedQuality())
            putExtra(RecorderService.EXTRA_SHOW_BUTTON, switchButton.isChecked)
            putExtra(RecorderService.EXTRA_LANDSCAPE, switchLandscape.isChecked)
        }
        startForegroundService(intent)
        moveTaskToBack(true)
    }

    private fun stopRecording() {
        startService(
            Intent(this, RecorderService::class.java).setAction(RecorderService.ACTION_STOP)
        )
        btnStart.postDelayed({ updateStartButton() }, 600)
    }
}
