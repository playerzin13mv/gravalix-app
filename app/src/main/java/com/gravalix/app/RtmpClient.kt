package com.gravalix.app

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.SSLSocketFactory

/**
 * Cliente RTMP mínimo, só para PUBLICAR (enviar) vídeo e áudio para Twitch, YouTube, Kick, etc.
 * Suporta rtmp:// e rtmps://.
 */
class RtmpClient(private val url: String, private val key: String) {

    companion object {
        const val TYPE_AUDIO = 8
        const val TYPE_VIDEO = 9
        const val TYPE_DATA = 18
        private const val TYPE_SET_CHUNK = 1
        private const val TYPE_ACK = 3
        private const val TYPE_WINDOW = 5
        private const val TYPE_COMMAND = 20
        private const val OUT_CHUNK = 4096
    }

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: OutputStream? = null
    private var streamId = 1
    private var inChunk = 128
    private var txn = 1.0

    private class InState {
        var ts = 0L
        var len = 0
        var type = 0
        var msid = 0
        var buf: ByteArrayOutputStream? = null
        var need = 0
    }

    private val inStates = HashMap<Int, InState>()
    private class Msg(val type: Int, val payload: ByteArray)

    @Volatile var connected = false
        private set

    // ------------------------------------------------------------------ conexão

    fun connect() {
        val u = parseUrl(url)
        val s: Socket = if (u.secure) {
            SSLSocketFactory.getDefault().createSocket() as Socket
        } else {
            Socket()
        }
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(u.host, u.port), 10_000)
        if (u.secure) {
            (s as javax.net.ssl.SSLSocket).startHandshake()
        }
        s.soTimeout = 15_000
        socket = s
        input = DataInputStream(s.getInputStream().buffered(65536))
        output = s.getOutputStream().buffered(65536).let { it }

        handshake()

        // tamanho dos pacotes que vamos enviar
        sendRaw(2, 0, TYPE_SET_CHUNK, 0, intBytes(OUT_CHUNK))
        outChunk = OUT_CHUNK

        // connect
        val connectObj = Amf.obj(
            "app" to u.app,
            "type" to "nonprivate",
            "flashVer" to "FMLE/3.0 (compatible; Gravalix)",
            "tcUrl" to u.tcUrl
        )
        sendCommand(3, 0, Amf.string("connect") + Amf.number(txn++) + connectObj)
        waitFor("_result")

        sendCommand(3, 0, Amf.string("releaseStream") + Amf.number(txn++) + Amf.NULL + Amf.string(key))
        sendCommand(3, 0, Amf.string("FCPublish") + Amf.number(txn++) + Amf.NULL + Amf.string(key))

        val createTxn = txn++
        sendCommand(3, 0, Amf.string("createStream") + Amf.number(createTxn) + Amf.NULL)
        val res = waitFor("_result", createTxn)
        streamId = Amf.lastNumber(res).toInt().coerceAtLeast(1)

        sendCommand(
            4, streamId,
            Amf.string("publish") + Amf.number(0.0) + Amf.NULL + Amf.string(key) + Amf.string("live")
        )
        waitStatus("NetStream.Publish.Start")
        connected = true
        s.soTimeout = 0
    }

    private class Url(val secure: Boolean, val host: String, val port: Int, val app: String, val tcUrl: String)

    private fun parseUrl(raw: String): Url {
        val t = raw.trim()
        val secure = t.startsWith("rtmps://", true)
        if (!secure && !t.startsWith("rtmp://", true)) throw IllegalArgumentException("O endereço precisa começar com rtmp:// ou rtmps://")
        val rest = t.substring(t.indexOf("://") + 3)
        val slash = rest.indexOf('/')
        if (slash < 0) throw IllegalArgumentException("Endereço sem nome de aplicação (ex.: rtmp://servidor/live)")
        val hostPort = rest.substring(0, slash)
        var app = rest.substring(slash + 1).trimEnd('/')
        val host: String
        var port = if (secure) 443 else 1935
        val colon = hostPort.lastIndexOf(':')
        if (colon > 0) {
            host = hostPort.substring(0, colon)
            port = hostPort.substring(colon + 1).toIntOrNull() ?: port
        } else {
            host = hostPort
        }
        if (app.isEmpty()) throw IllegalArgumentException("Endereço sem nome de aplicação (ex.: rtmp://servidor/live)")
        val scheme = if (secure) "rtmps" else "rtmp"
        val tc = "$scheme://$hostPort/$app"
        return Url(secure, host, port, app, tc)
    }

    private fun handshake() {
        val o = output!!
        val i = input!!
        val c1 = ByteArray(1536)
        SecureRandom().nextBytes(c1)
        for (k in 0 until 8) c1[k] = 0
        o.write(3)
        o.write(c1)
        o.flush()
        val s0 = i.read()
        if (s0 != 3) throw java.io.IOException("Servidor RTMP respondeu algo inesperado")
        val s1 = ByteArray(1536)
        i.readFully(s1)
        val s2 = ByteArray(1536)
        i.readFully(s2)
        o.write(s1)
        o.flush()
    }

    // ------------------------------------------------------------------ envio

    private var outChunk = 128
    private val sendLock = Any()

    private fun intBytes(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())

    private fun sendCommand(csid: Int, msid: Int, payload: ByteArray) =
        sendRaw(csid, 0, TYPE_COMMAND, msid, payload)

    /** Envia uma mensagem (vídeo, áudio ou dados) no fluxo publicado. */
    fun sendMedia(type: Int, timestampMs: Long, payload: ByteArray) {
        val csid = when (type) {
            TYPE_AUDIO -> 7
            TYPE_VIDEO -> 6
            else -> 5
        }
        sendRaw(csid, timestampMs, type, streamId, payload)
    }

    private fun sendRaw(csid: Int, ts: Long, type: Int, msid: Int, payload: ByteArray) {
        synchronized(sendLock) {
            val o = output ?: throw java.io.IOException("desconectado")
            val tsw = ts and 0xFFFFFFFFL
            val ext = tsw >= 0xFFFFFFL
            var pos = 0
            var first = true
            while (pos < payload.size || (first && payload.isEmpty())) {
                val n = minOf(outChunk, payload.size - pos)
                if (first) {
                    o.write((0 shl 6) or csid)
                    val t3 = if (ext) 0xFFFFFFL else tsw
                    o.write((t3 shr 16).toInt() and 0xFF)
                    o.write((t3 shr 8).toInt() and 0xFF)
                    o.write(t3.toInt() and 0xFF)
                    o.write((payload.size shr 16) and 0xFF)
                    o.write((payload.size shr 8) and 0xFF)
                    o.write(payload.size and 0xFF)
                    o.write(type)
                    // id do fluxo em little-endian
                    o.write(msid and 0xFF)
                    o.write((msid shr 8) and 0xFF)
                    o.write((msid shr 16) and 0xFF)
                    o.write((msid shr 24) and 0xFF)
                } else {
                    o.write((3 shl 6) or csid)
                }
                if (ext) o.write(intBytes(tsw.toInt()))
                o.write(payload, pos, n)
                pos += n
                first = false
            }
            o.flush()
        }
    }

    // ---------------------------------------------------------------- recepção

    private fun readMessage(): Msg {
        val i = input!!
        while (true) {
            val b0 = i.readUnsignedByte()
            val fmt = b0 shr 6
            var csid = b0 and 0x3F
            if (csid == 0) csid = i.readUnsignedByte() + 64
            else if (csid == 1) csid = i.readUnsignedByte() + (i.readUnsignedByte() shl 8) + 64
            val st = inStates.getOrPut(csid) { InState() }

            var tsField = 0L
            if (fmt <= 2) {
                tsField = readU24(i).toLong()
                if (fmt <= 1) {
                    st.len = readU24(i)
                    st.type = i.readUnsignedByte()
                    if (fmt == 0) {
                        st.msid = i.readUnsignedByte() or (i.readUnsignedByte() shl 8) or
                            (i.readUnsignedByte() shl 16) or (i.readUnsignedByte() shl 24)
                    }
                }
                if (tsField == 0xFFFFFFL) tsField = i.readInt().toLong() and 0xFFFFFFFFL
                st.ts = tsField
            } else if (st.buf == null && st.ts == 0xFFFFFFL) {
                i.readInt()
            }

            if (st.buf == null) {
                st.buf = ByteArrayOutputStream(maxOf(16, st.len))
                st.need = st.len
            }
            val n = minOf(inChunk, st.need)
            val tmp = ByteArray(n)
            i.readFully(tmp)
            st.buf!!.write(tmp)
            st.need -= n
            if (st.need <= 0) {
                val data = st.buf!!.toByteArray()
                st.buf = null
                val m = Msg(st.type, data)
                when (m.type) {
                    TYPE_SET_CHUNK -> if (data.size >= 4) {
                        inChunk = ((data[0].toInt() and 0xFF) shl 24) or ((data[1].toInt() and 0xFF) shl 16) or
                            ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
                    }
                    TYPE_WINDOW, 6, TYPE_ACK, 4 -> {}
                    else -> return m
                }
            }
        }
    }

    private fun readU24(i: InputStream): Int =
        (i.read() shl 16) or (i.read() shl 8) or i.read()

    private fun waitFor(name: String, txnId: Double? = null): ByteArray {
        repeat(40) {
            val m = readMessage()
            if (m.type == TYPE_COMMAND) {
                val cmd = Amf.firstString(m.payload)
                if (cmd == name) return m.payload
                if (cmd == "_error") throw java.io.IOException("O servidor recusou a conexão")
            }
        }
        throw java.io.IOException("Sem resposta do servidor")
    }

    private fun waitStatus(code: String) {
        repeat(40) {
            val m = readMessage()
            if (m.type == TYPE_COMMAND) {
                val text = String(m.payload, Charsets.ISO_8859_1)
                if (text.contains(code)) return
                if (text.contains("NetStream.Publish.BadName") || text.contains("NetStream.Publish.Denied") ||
                    text.contains("Rejected") || text.contains("Unauthorized")
                ) {
                    throw java.io.IOException("Chave da live recusada pelo servidor")
                }
            }
        }
        throw java.io.IOException("O servidor não aceitou publicar")
    }

    /** Lê e descarta o que o servidor mandar durante a live (evita travar a conexão). */
    fun drainIncoming() {
        try {
            while (connected) readMessage()
        } catch (_: Exception) {
        }
    }

    fun close() {
        connected = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        output = null
        input = null
    }
}

/** Codificador mínimo de AMF0. */
object Amf {
    val NULL = byteArrayOf(5)

    fun string(s: String): ByteArray {
        val b = s.toByteArray(Charsets.UTF_8)
        return byteArrayOf(2, (b.size shr 8).toByte(), b.size.toByte()) + b
    }

    fun number(d: Double): ByteArray {
        val l = java.lang.Double.doubleToLongBits(d)
        val out = ByteArray(9)
        out[0] = 0
        for (i in 0 until 8) out[1 + i] = (l shr (56 - 8 * i)).toByte()
        return out
    }

    private fun key(k: String): ByteArray {
        val b = k.toByteArray(Charsets.UTF_8)
        return byteArrayOf((b.size shr 8).toByte(), b.size.toByte()) + b
    }

    fun obj(vararg pairs: Pair<String, Any>): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(3)
        for ((k, v) in pairs) {
            o.write(key(k))
            when (v) {
                is String -> o.write(string(v))
                is Number -> o.write(number(v.toDouble()))
                is Boolean -> o.write(byteArrayOf(1, if (v) 1 else 0))
                else -> o.write(NULL)
            }
        }
        o.write(byteArrayOf(0, 0, 9))
        return o.toByteArray()
    }

    /** Matriz ECMA para o onMetaData. */
    fun ecma(vararg pairs: Pair<String, Any>): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(8)
        o.write(intArrayOf(0, 0, 0, pairs.size).map { it.toByte() }.toByteArray())
        for ((k, v) in pairs) {
            o.write(key(k))
            when (v) {
                is String -> o.write(string(v))
                is Number -> o.write(number(v.toDouble()))
                is Boolean -> o.write(byteArrayOf(1, if (v) 1 else 0))
                else -> o.write(NULL)
            }
        }
        o.write(byteArrayOf(0, 0, 9))
        return o.toByteArray()
    }

    fun firstString(p: ByteArray): String {
        if (p.size < 3 || p[0].toInt() != 2) return ""
        val len = ((p[1].toInt() and 0xFF) shl 8) or (p[2].toInt() and 0xFF)
        if (3 + len > p.size) return ""
        return String(p, 3, len, Charsets.UTF_8)
    }

    /** Último número (marcador 0x00) encontrado na mensagem; no createStream é o id do fluxo. */
    fun lastNumber(p: ByteArray): Double {
        var i = p.size - 9
        while (i >= 0) {
            if (p[i].toInt() == 0) {
                var l = 0L
                for (k in 0 until 8) l = (l shl 8) or (p[i + 1 + k].toLong() and 0xFF)
                val d = java.lang.Double.longBitsToDouble(l)
                if (d >= 1.0 && d < 1_000_000.0) return d
            }
            i--
        }
        return 1.0
    }
}
