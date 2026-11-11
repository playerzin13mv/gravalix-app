package com.gravalix.app

import android.content.Context
import android.content.SharedPreferences

/** Todas as configurações do Gravalix num lugar só. */
object Prefs {

    const val BUBBLE_HIDDEN_FROM_VIDEO = 0 // continua na tela para você, mas fora do vídeo
    const val BUBBLE_GONE = 1              // some totalmente durante a gravação

    private fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("gravalix", Context.MODE_PRIVATE)

    fun audio(c: Context) = sp(c).getInt("audio", RecorderService.AUDIO_INTERNAL)
    fun setAudio(c: Context, v: Int) = sp(c).edit().putInt("audio", v).apply()

    fun quality(c: Context) = sp(c).getInt("quality", RecorderService.QUALITY_FHD)
    fun setQuality(c: Context, v: Int) = sp(c).edit().putInt("quality", v).apply()

    /** Segundos da contagem antes de gravar. 0 = sem contagem. */
    fun countdown(c: Context) = sp(c).getInt("countdown", 3)
    fun setCountdown(c: Context, v: Int) = sp(c).edit().putInt("countdown", v).apply()

    fun landscape(c: Context) = sp(c).getBoolean("landscape", false)
    fun setLandscape(c: Context, v: Boolean) = sp(c).edit().putBoolean("landscape", v).apply()

    /** Bolha flutuante ligada (aparece antes e durante a gravação). */
    fun bubble(c: Context) = sp(c).getBoolean("bubble", true)
    fun setBubble(c: Context, v: Boolean) = sp(c).edit().putBoolean("bubble", v).apply()

    fun bubbleMode(c: Context) = sp(c).getInt("bubble_mode", BUBBLE_HIDDEN_FROM_VIDEO)
    fun setBubbleMode(c: Context, v: Int) = sp(c).edit().putInt("bubble_mode", v).apply()

    /** Mostrar os toques no vídeo. */
    fun showTouches(c: Context) = sp(c).getBoolean("show_touches", false)
    fun setShowTouches(c: Context, v: Boolean) = sp(c).edit().putBoolean("show_touches", v).apply()

    /** Traços da caneta somem sozinhos depois de alguns segundos. */
    fun penFade(c: Context) = sp(c).getBoolean("pen_fade", false)
    fun setPenFade(c: Context, v: Boolean) = sp(c).edit().putBoolean("pen_fade", v).apply()

    fun penColor(c: Context) = sp(c).getInt("pen_color", 0)
    fun setPenColor(c: Context, v: Int) = sp(c).edit().putInt("pen_color", v).apply()

    /** Agitar o celular para parar a gravação. */
    fun shakeStop(c: Context) = sp(c).getBoolean("shake_stop", false)
    fun setShakeStop(c: Context, v: Boolean) = sp(c).edit().putBoolean("shake_stop", v).apply()

    /** Posição da bolha (para lembrar onde você deixou). */
    fun bubbleX(c: Context) = sp(c).getInt("bubble_x", -1)
    fun bubbleY(c: Context) = sp(c).getInt("bubble_y", -1)
    fun setBubblePos(c: Context, x: Int, y: Int) =
        sp(c).edit().putInt("bubble_x", x).putInt("bubble_y", y).apply()
}
