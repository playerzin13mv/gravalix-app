package com.gravalix.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** Mede o volume do microfone quando NÃO está gravando (para testar o PNGTuber). */
class AudioMeter(private val c: Context) {
    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        if (c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        running = true
        thread = Thread({
            var rec: AudioRecord? = null
            try {
                val min = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                rec = AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 4096))
                val ok = rec.state == AudioRecord.STATE_INITIALIZED
                if (ok) rec.startRecording()
                val buf = ShortArray(1024)
                while (running && ok) {
                    if (RecorderService.isRecording) { Thread.sleep(200); continue }
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) AudioLevel.feed(buf, n) else Thread.sleep(20)
                }
            } catch (e: Exception) {
                AppLog.e("medidor de microfone", e)
            } finally {
                try { rec?.stop() } catch (_: Exception) {}
                try { rec?.release() } catch (_: Exception) {}
            }
        }, "gravalix-meter").also { it.start() }
    }

    fun stop() {
        running = false
    }
}
