package com.gravalix.app

/** Recebe o vídeo e o áudio já codificados (H.264 em Annex-B e AAC cru) para transmitir ao vivo. */
interface LiveSink {
    fun onVideoConfig(sps: ByteArray, pps: ByteArray)
    fun onVideo(data: ByteArray, ptsUs: Long, key: Boolean)
    fun onAudioConfig(asc: ByteArray)
    fun onAudio(data: ByteArray, ptsUs: Long)
    fun close()
}
