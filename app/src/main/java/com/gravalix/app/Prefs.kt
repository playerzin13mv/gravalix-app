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

    /** Padrão: a bolha some durante a gravação (é o único jeito que nunca aparece no vídeo). */
    fun bubbleMode(c: Context) = sp(c).getInt("bubble_mode", BUBBLE_GONE)
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

    /** Lado da tela onde a bolha fica encaixada (true = direita) e a altura dela. */
    fun bubbleRight(c: Context) = sp(c).getBoolean("bubble_right", true)
    fun setBubbleSide(c: Context, right: Boolean) = sp(c).edit().putBoolean("bubble_right", right).apply()
    fun bubbleY(c: Context) = sp(c).getInt("bubble_y", -1)
    fun setBubbleY(c: Context, y: Int) = sp(c).edit().putInt("bubble_y", y).apply()

    /** Personalização da bolha: tamanho (0 pequena, 1 média, 2 grande), opacidade e cor. */
    fun bubbleSize(c: Context) = sp(c).getInt("bubble_size", 1)
    fun setBubbleSize(c: Context, v: Int) = sp(c).edit().putInt("bubble_size", v).apply()

    /** 0 = 100%, 1 = 80%, 2 = 60%, 3 = 40% */
    fun bubbleAlpha(c: Context) = sp(c).getInt("bubble_alpha", 0)
    fun setBubbleAlpha(c: Context, v: Int) = sp(c).edit().putInt("bubble_alpha", v).apply()

    fun bubbleColor(c: Context) = sp(c).getInt("bubble_color", 0)
    fun setBubbleColor(c: Context, v: Int) = sp(c).edit().putInt("bubble_color", v).apply()

    /** Quais botões aparecem no menu da bolha. */
    fun menuPen(c: Context) = sp(c).getBoolean("menu_pen", true)
    fun setMenuPen(c: Context, v: Boolean) = sp(c).edit().putBoolean("menu_pen", v).apply()
    fun menuTouches(c: Context) = sp(c).getBoolean("menu_touches", true)
    fun setMenuTouches(c: Context, v: Boolean) = sp(c).edit().putBoolean("menu_touches", v).apply()
    fun menuHome(c: Context) = sp(c).getBoolean("menu_home", true)
    fun setMenuHome(c: Context, v: Boolean) = sp(c).edit().putBoolean("menu_home", v).apply()
}
