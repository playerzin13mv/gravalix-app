package com.gravalix.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class RecordConfig(
    val width: Int,
    val height: Int,
    val dpi: Int,
    val fps: Int,
    val bitrate: Int
)

/**
 * Motor de gravação do Gravalix.
 *
 * Vídeo: a tela é espelhada (VirtualDisplay) direto para um codificador H.264.
 * Áudio: som interno (AudioPlaybackCapture) e/ou microfone, misturados e codificados em AAC.
 * Tudo é gravado ao vivo em um .mp4 (MediaMuxer) dentro de Filmes/Gravalix.
 * Nenhuma marca d'água é desenhada em nenhum momento.
 */
class ScreenRecorder(
    private val context: Context,
    private val projection: MediaProjection,
    private val config: RecordConfig,
    private val audioMode: Int,
    val output: Uri,
    private val live: LiveSink? = null,
    private val onMessage: (String) -> Unit
) {

    companion object {
        private const val TAG = "Gravalix"
        private const val SAMPLE_RATE = 44100
        private const val AUDIO_FRAMES = 1024

        /** Cria o arquivo .mp4 vazio (pendente) em Filmes/Gravalix. */
        fun createOutput(context: Context): Uri {
            val name = "Gravalix_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Gravalix")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            return context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            ) ?: throw IOException("Não foi possível criar o arquivo de vídeo")
        }
    }

    private var pfd: ParcelFileDescriptor? = null
    private var muxer: MediaMuxer? = null
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var internalRecord: AudioRecord? = null
    private var micRecord: AudioRecord? = null
    private var videoThread: Thread? = null
    private var audioThread: Thread? = null

    private val muxLock = Any()
    private val muxReady = CountDownLatch(1)
    @Volatile private var muxerStarted = false
    private var videoTrack = -1
    private var audioTrack = -1
    @Volatile private var expectAudio = false

    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var aborted = false
    @Volatile private var stopped = false
    @Volatile private var waitKeyframe = false

    private var startUs = 0L
    private var pauseStartUs = 0L
    @Volatile private var pausedTotalUs = 0L
    private var videoBaseUs = Long.MIN_VALUE
    private var lastAudioPts = -1L

    private fun nowUs(): Long = System.nanoTime() / 1000

    private fun bytesOf(b: ByteBuffer): ByteArray {
        val d = b.duplicate()
        d.rewind()
        val out = ByteArray(d.remaining())
        d.get(out)
        return out
    }

    // ------------------------------------------------------------------ início

    fun start() {
        try {
            doStart()
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar", e)
            abort()
            throw e
        }
    }

    private fun doStart() {
        val file = context.contentResolver.openFileDescriptor(output, "rw")
            ?: throw IOException("Não foi possível abrir o arquivo")
        pfd = file
        muxer = MediaMuxer(file.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        setupAudio()

        val videoFormat = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC, config.width, config.height
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val venc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoEncoder = venc
        venc.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = venc.createInputSurface()
        venc.start()

        startUs = nowUs()
        running = true

        virtualDisplay = projection.createVirtualDisplay(
            "Gravalix",
            config.width,
            config.height,
            config.dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface,
            null,
            null
        )

        videoThread = Thread({ videoLoop() }, "gravalix-video").also { it.start() }
        if (expectAudio) {
            audioThread = Thread({ audioLoop() }, "gravalix-audio").also { it.start() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupAudio() {
        expectAudio = false
        if (audioMode == RecorderService.AUDIO_NONE) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onMessage(context.getString(R.string.toast_audio_unavailable))
            return
        }
        try {
            val wantInternal = audioMode == RecorderService.AUDIO_INTERNAL ||
                audioMode == RecorderService.AUDIO_BOTH
            val wantMic = audioMode == RecorderService.AUDIO_MIC ||
                audioMode == RecorderService.AUDIO_BOTH

            if (wantInternal) {
                val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build()
                val minSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
                )
                val rec = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(captureConfig)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(maxOf(minSize * 4, 16384))
                    .build()
                if (rec.state != AudioRecord.STATE_INITIALIZED) {
                    rec.release()
                    throw IllegalStateException("Captura do som interno indisponível")
                }
                internalRecord = rec
            }

            if (wantMic) {
                val minSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                val rec = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minSize * 4, 8192)
                )
                if (rec.state != AudioRecord.STATE_INITIALIZED) {
                    rec.release()
                    throw IllegalStateException("Microfone indisponível")
                }
                micRecord = rec
            }

            val audioFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 2
            ).apply {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val aenc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            aenc.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aenc.start()
            audioEncoder = aenc
            expectAudio = true
        } catch (e: Exception) {
            Log.w(TAG, "Áudio indisponível, gravando sem som", e)
            releaseAudio()
            expectAudio = false
            onMessage(context.getString(R.string.toast_audio_unavailable))
        }
    }

    // ------------------------------------------------------------ pausar / voltar

    fun pause() {
        if (!running || paused) return
        pauseStartUs = nowUs()
        paused = true
        setVideoSuspended(true)
    }

    fun resume() {
        if (!running || !paused) return
        pausedTotalUs += nowUs() - pauseStartUs
        waitKeyframe = true
        paused = false
        setVideoSuspended(false)
        requestSyncFrame()
    }

    /** Tempo gravado (sem contar as pausas), em milissegundos. */
    fun elapsedMs(): Long {
        if (!running) return 0
        val now = nowUs()
        val activePause = if (paused) now - pauseStartUs else 0L
        return maxOf(0L, (now - startUs - pausedTotalUs - activePause) / 1000)
    }

    private fun setVideoSuspended(suspend: Boolean) {
        try {
            val b = Bundle()
            b.putInt(MediaCodec.PARAMETER_KEY_SUSPEND, if (suspend) 1 else 0)
            videoEncoder?.setParameters(b)
        } catch (e: Exception) {
            Log.w(TAG, "suspend não suportado", e)
        }
    }

    private fun requestSyncFrame() {
        try {
            val b = Bundle()
            b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            videoEncoder?.setParameters(b)
        } catch (e: Exception) {
            Log.w(TAG, "sync frame não suportado", e)
        }
    }

    // ------------------------------------------------------------------- muxer

    private fun addTrack(format: MediaFormat, video: Boolean) {
        synchronized(muxLock) {
            val m = muxer ?: return
            if (video) {
                videoTrack = m.addTrack(format)
            } else {
                audioTrack = m.addTrack(format)
            }
            startMuxerIfReadyLocked()
        }
    }

    private fun startMuxerIfReadyLocked() {
        if (muxerStarted) return
        if (videoTrack >= 0 && (!expectAudio || audioTrack >= 0)) {
            muxer?.start()
            muxerStarted = true
            muxReady.countDown()
        }
    }

    private fun audioFailed() {
        synchronized(muxLock) {
            expectAudio = false
            startMuxerIfReadyLocked()
        }
    }

    private fun awaitMuxer(): Boolean {
        if (muxerStarted) return true
        try {
            muxReady.await(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
        }
        return muxerStarted
    }

    private fun writeSample(track: Int, buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        synchronized(muxLock) {
            if (muxerStarted) muxer?.writeSampleData(track, buf, info)
        }
    }

    // ------------------------------------------------------------------- vídeo

    private fun videoLoop() {
        val enc = videoEncoder ?: return
        val info = MediaCodec.BufferInfo()
        var lastPts = -1L
        try {
            while (!aborted) {
                val idx = enc.dequeueOutputBuffer(info, 10_000)
                if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) continue
                if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    addTrack(enc.outputFormat, true)
                    try {
                        val f = enc.outputFormat
                        val sps = f.getByteBuffer("csd-0")
                        val pps = f.getByteBuffer("csd-1")
                        if (sps != null && pps != null) live?.onVideoConfig(bytesOf(sps), bytesOf(pps))
                    } catch (e: Exception) {
                        AppLog.e("live: config de vídeo", e)
                    }
                    continue
                }
                if (idx < 0) continue

                val buf = enc.getOutputBuffer(idx)
                val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                var write = buf != null && info.size > 0 &&
                    (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                if (write && paused) write = false
                if (write && waitKeyframe) {
                    if ((info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                        waitKeyframe = false
                    } else {
                        write = false
                    }
                }
                if (write && awaitMuxer()) {
                    if (videoBaseUs == Long.MIN_VALUE) {
                        videoBaseUs = if (Math.abs(info.presentationTimeUs - startUs) < 5_000_000L) {
                            startUs
                        } else {
                            info.presentationTimeUs
                        }
                    }
                    var pts = info.presentationTimeUs - videoBaseUs - pausedTotalUs
                    if (pts < 0) pts = 0
                    if (pts <= lastPts) pts = lastPts + 1
                    lastPts = pts
                    info.presentationTimeUs = pts
                    buf!!.position(info.offset)
                    buf.limit(info.offset + info.size)
                    if (live != null) {
                        try {
                            val copy = ByteArray(info.size)
                            buf.get(copy)
                            buf.position(info.offset)
                            live.onVideo(copy, pts, (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0)
                        } catch (e: Exception) {
                            AppLog.e("live: vídeo", e)
                        }
                    }
                    writeSample(videoTrack, buf, info)
                }
                enc.releaseOutputBuffer(idx, false)
                if (eos) break
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro no vídeo", e)
        }
    }

    // ------------------------------------------------------------------- áudio

    private fun audioLoop() {
        val enc = audioEncoder ?: return
        val internal = internalRecord
        val mic = micRecord
        val info = MediaCodec.BufferInfo()
        val internalBuf = ShortArray(AUDIO_FRAMES * 2)
        val micBuf = ShortArray(AUDIO_FRAMES)
        val mixed = ShortArray(AUDIO_FRAMES * 2)
        var framesFed = 0L
        var baseUs = -1L
        try {
            internal?.startRecording()
            mic?.startRecording()
            while (running) {
                if (internal != null) readFully(internal, internalBuf)
                if (mic != null) readFully(mic, micBuf)

                for (i in 0 until AUDIO_FRAMES) {
                    var l = 0
                    var r = 0
                    if (internal != null) {
                        l += internalBuf[2 * i].toInt()
                        r += internalBuf[2 * i + 1].toInt()
                    }
                    if (mic != null) {
                        val m = micBuf[i].toInt()
                        l += m
                        r += m
                    }
                    mixed[2 * i] = clamp16(l)
                    mixed[2 * i + 1] = clamp16(r)
                }
                if (mic != null) AudioLevel.feed(micBuf, AUDIO_FRAMES)

                if (baseUs < 0) baseUs = nowUs()
                if (paused) continue

                val ptsUs = (baseUs - startUs) + framesFed * 1_000_000L / SAMPLE_RATE
                var inIdx = enc.dequeueInputBuffer(10_000)
                if (inIdx < 0) {
                    drainAudio(enc, info, false)
                    inIdx = enc.dequeueInputBuffer(10_000)
                }
                if (inIdx >= 0) {
                    val inBuf = enc.getInputBuffer(inIdx)!!
                    inBuf.clear()
                    inBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(mixed)
                    enc.queueInputBuffer(inIdx, 0, mixed.size * 2, ptsUs, 0)
                    framesFed += AUDIO_FRAMES
                }
                drainAudio(enc, info, false)
            }

            // fim do áudio: avisa o codificador e esvazia a fila
            val eosIdx = enc.dequeueInputBuffer(100_000)
            if (eosIdx >= 0) {
                val endPts = (maxOf(baseUs, startUs) - startUs) + framesFed * 1_000_000L / SAMPLE_RATE
                enc.queueInputBuffer(eosIdx, 0, 0, endPts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drainAudio(enc, info, true)
        } catch (e: Exception) {
            Log.e(TAG, "Erro no áudio", e)
            audioFailed()
        } finally {
            try { internal?.stop() } catch (_: Exception) {}
            try { mic?.stop() } catch (_: Exception) {}
        }
    }

    private fun clamp16(v: Int): Short =
        when {
            v > Short.MAX_VALUE.toInt() -> Short.MAX_VALUE
            v < Short.MIN_VALUE.toInt() -> Short.MIN_VALUE
            else -> v.toShort()
        }

    private fun readFully(rec: AudioRecord, buf: ShortArray) {
        var off = 0
        while (off < buf.size && running) {
            val n = rec.read(buf, off, buf.size - off, AudioRecord.READ_BLOCKING)
            if (n <= 0) {
                try { Thread.sleep(20) } catch (_: InterruptedException) {}
                break
            }
            off += n
        }
        if (off < buf.size) Arrays.fill(buf, off, buf.size, 0.toShort())
    }

    private fun drainAudio(enc: MediaCodec, info: MediaCodec.BufferInfo, endOfStream: Boolean) {
        var tries = 0
        while (true) {
            val idx = enc.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) return
                tries++
                if (tries > 300) return
                continue
            }
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                addTrack(enc.outputFormat, false)
                try {
                    enc.outputFormat.getByteBuffer("csd-0")?.let { live?.onAudioConfig(bytesOf(it)) }
                } catch (e: Exception) {
                    AppLog.e("live: config de áudio", e)
                }
                continue
            }
            if (idx < 0) continue

            val buf = enc.getOutputBuffer(idx)
            val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
            if (buf != null && info.size > 0 &&
                (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                awaitMuxer()
            ) {
                var pts = info.presentationTimeUs
                if (pts <= lastAudioPts) pts = lastAudioPts + 1
                lastAudioPts = pts
                info.presentationTimeUs = pts
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                if (live != null) {
                    try {
                        val copy = ByteArray(info.size)
                        buf.get(copy)
                        buf.position(info.offset)
                        live.onAudio(copy, pts)
                    } catch (e: Exception) {
                        AppLog.e("live: áudio", e)
                    }
                }
                writeSample(audioTrack, buf, info)
            }
            enc.releaseOutputBuffer(idx, false)
            if (eos) return
        }
    }

    // ------------------------------------------------------------------ parar

    /** Finaliza o vídeo. Retorna o Uri do arquivo pronto, ou null se nada foi gravado. */
    fun stop(): Uri? {
        if (stopped) return null
        stopped = true
        running = false
        paused = false

        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { videoEncoder?.signalEndOfInputStream() } catch (_: Exception) {}

        try { audioThread?.join(4000) } catch (_: InterruptedException) {}
        try { videoThread?.join(6000) } catch (_: InterruptedException) {}
        aborted = true

        var ok = false
        synchronized(muxLock) {
            ok = muxerStarted
            try {
                if (muxerStarted) muxer?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "muxer.stop falhou", e)
                ok = false
            }
            try { muxer?.release() } catch (_: Exception) {}
            muxer = null
        }

        releaseVideo()
        releaseAudio()
        try { pfd?.close() } catch (_: Exception) {}
        pfd = null

        return if (ok) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(output, values, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "Não consegui publicar o vídeo", e)
            }
            output
        } else {
            deleteOutput()
            null
        }
    }

    /** Cancela tudo e apaga o arquivo (usado quando o início falha). */
    fun abort() {
        if (stopped) return
        stopped = true
        running = false
        aborted = true
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        synchronized(muxLock) {
            try { muxer?.release() } catch (_: Exception) {}
            muxer = null
        }
        releaseVideo()
        releaseAudio()
        try { pfd?.close() } catch (_: Exception) {}
        pfd = null
        deleteOutput()
    }

    private fun deleteOutput() {
        try {
            context.contentResolver.delete(output, null, null)
        } catch (_: Exception) {
        }
    }

    private fun releaseVideo() {
        try { videoEncoder?.stop() } catch (_: Exception) {}
        try { videoEncoder?.release() } catch (_: Exception) {}
        videoEncoder = null
        try { inputSurface?.release() } catch (_: Exception) {}
        inputSurface = null
    }

    private fun releaseAudio() {
        try { audioEncoder?.stop() } catch (_: Exception) {}
        try { audioEncoder?.release() } catch (_: Exception) {}
        audioEncoder = null
        try { internalRecord?.release() } catch (_: Exception) {}
        internalRecord = null
        try { micRecord?.release() } catch (_: Exception) {}
        micRecord = null
    }
}
