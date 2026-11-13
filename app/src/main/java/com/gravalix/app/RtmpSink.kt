package com.gravalix.app

import java.io.ByteArrayOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Transmite o que o ScreenRecorder produz para um servidor RTMP.
 * A conexão acontece em segundo plano; o vídeo só começa a sair a partir de um quadro-chave.
 */
class RtmpSink(
    private val url: String,
    private val key: String,
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val onStatus: (State, String) -> Unit
) : LiveSink {

    enum class State { CONNECTING, LIVE, ERROR }

    private class Item(val type: Int, val ts: Long, val data: ByteArray, val key: Boolean)

    private val queue = LinkedBlockingQueue<Item>()
    @Volatile private var closed = false
    @Volatile private var ready = false
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    private var asc: ByteArray? = null
    private var thread: Thread? = null

    fun start() {
        onStatus(State.CONNECTING, "")
        thread = Thread({ run() }, "gravalix-rtmp").also { it.start() }
    }

    private fun run() {
        val client = RtmpClient(url, key)
        try {
            client.connect()
            AppLog.i("live: conectado e publicando")
            // metadados
            val meta = Amf.string("@setDataFrame") + Amf.string("onMetaData") + Amf.ecma(
                "width" to width, "height" to height, "framerate" to fps,
                "videocodecid" to 7, "audiocodecid" to 10, "audiosamplerate" to 44100,
                "audiosamplesize" to 16, "stereo" to true
            )
            client.sendMedia(RtmpClient.TYPE_DATA, 0, meta)

            // espera os dados de configuração do codificador (podem já ter chegado)
            var waited = 0
            while ((sps == null || pps == null) && !closed && waited < 100) {
                Thread.sleep(50)
                waited++
            }
            val s = sps
            val p = pps
            if (s != null && p != null) client.sendMedia(RtmpClient.TYPE_VIDEO, 0, videoHeader(s, p))
            asc?.let { client.sendMedia(RtmpClient.TYPE_AUDIO, 0, byteArrayOf(0xAF.toByte(), 0) + it) }
            ready = true
            onStatus(State.LIVE, "")
            Thread({ client.drainIncoming() }, "gravalix-rtmp-in").also { it.isDaemon = true }.start()

            while (!closed) {
                val it = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                // se a internet estiver devagar, descarta quadros comuns para não atrasar a live
                if (queue.size > 240 && it.type == RtmpClient.TYPE_VIDEO && !it.key) continue
                client.sendMedia(it.type, it.ts, it.data)
            }
        } catch (e: Exception) {
            if (!closed) {
                AppLog.e("live: erro", e)
                onStatus(State.ERROR, friendly(e))
            }
        } finally {
            ready = false
            client.close()
        }
    }

    private fun friendly(e: Exception): String {
        val m = e.message ?: ""
        return when {
            e is IllegalArgumentException -> m
            e is java.net.UnknownHostException -> "Servidor não encontrado. Confira o endereço e a internet."
            e is java.net.SocketTimeoutException -> "O servidor demorou a responder."
            m.isNotEmpty() -> m
            else -> "Falha na conexão da live."
        }
    }

    // ---------------------------------------------------------------- LiveSink

    override fun onVideoConfig(sps: ByteArray, pps: ByteArray) {
        this.sps = stripStartCode(sps)
        this.pps = stripStartCode(pps)
    }

    override fun onAudioConfig(asc: ByteArray) {
        this.asc = asc
    }

    override fun onVideo(data: ByteArray, ptsUs: Long, key: Boolean) {
        if (closed) return
        if (!ready && !key) return
        val body = annexBToAvcc(data)
        val head = byteArrayOf(((if (key) 1 else 2) shl 4 or 7).toByte(), 1, 0, 0, 0)
        if (!ready) queue.clear()
        queue.offer(Item(RtmpClient.TYPE_VIDEO, ptsUs / 1000, head + body, key))
    }

    override fun onAudio(data: ByteArray, ptsUs: Long) {
        if (closed || !ready) return
        queue.offer(Item(RtmpClient.TYPE_AUDIO, ptsUs / 1000, byteArrayOf(0xAF.toByte(), 1) + data, false))
    }

    override fun close() {
        closed = true
        thread?.interrupt()
    }

    // ---------------------------------------------------------------- formatos

    private fun stripStartCode(b: ByteArray): ByteArray {
        var i = 0
        if (b.size > 4 && b[0].toInt() == 0 && b[1].toInt() == 0 && b[2].toInt() == 0 && b[3].toInt() == 1) i = 4
        else if (b.size > 3 && b[0].toInt() == 0 && b[1].toInt() == 0 && b[2].toInt() == 1) i = 3
        return b.copyOfRange(i, b.size)
    }

    private fun videoHeader(sps: ByteArray, pps: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(byteArrayOf(0x17, 0, 0, 0, 0))
        o.write(1)
        o.write(sps[1].toInt())
        o.write(sps[2].toInt())
        o.write(sps[3].toInt())
        o.write(0xFF)
        o.write(0xE1)
        o.write(sps.size shr 8); o.write(sps.size and 0xFF)
        o.write(sps)
        o.write(1)
        o.write(pps.size shr 8); o.write(pps.size and 0xFF)
        o.write(pps)
        return o.toByteArray()
    }

    /** Troca os marcadores 00 00 00 01 do H.264 pelo tamanho de cada bloco (formato do RTMP/FLV). */
    private fun annexBToAvcc(d: ByteArray): ByteArray {
        val starts = ArrayList<Int>()
        var i = 0
        while (i + 3 < d.size) {
            if (d[i].toInt() == 0 && d[i + 1].toInt() == 0) {
                if (d[i + 2].toInt() == 1) { starts.add(i); i += 3; continue }
                if (d[i + 2].toInt() == 0 && d[i + 3].toInt() == 1) { starts.add(i); i += 4; continue }
            }
            i++
        }
        if (starts.isEmpty()) {
            return byteArrayOf((d.size shr 24).toByte(), (d.size shr 16).toByte(), (d.size shr 8).toByte(), d.size.toByte()) + d
        }
        val out = ByteArrayOutputStream(d.size + 16)
        for (k in starts.indices) {
            val s = starts[k]
            val codeLen = if (d[s + 2].toInt() == 1) 3 else 4
            val from = s + codeLen
            val to = if (k + 1 < starts.size) starts[k + 1] else d.size
            val n = to - from
            if (n <= 0) continue
            out.write(n shr 24); out.write((n shr 16) and 0xFF); out.write((n shr 8) and 0xFF); out.write(n and 0xFF)
            out.write(d, from, n)
        }
        return out.toByteArray()
    }
}
