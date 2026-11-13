package com.gravalix.app

import kotlin.math.sqrt

/** Volume atual do microfone (0 a 1). O PNGTuber usa isso para "falar". */
object AudioLevel {
    @Volatile var level = 0f

    fun feed(buf: ShortArray, n: Int) {
        var sum = 0.0
        for (i in 0 until n) {
            val v = buf[i].toDouble() / 32768.0
            sum += v * v
        }
        val rms = sqrt(sum / maxOf(1, n)).toFloat()
        // suaviza: sobe rápido, desce devagar
        val target = (rms * 6f).coerceIn(0f, 1f)
        level = if (target > level) target else level * 0.8f + target * 0.2f
    }
}
