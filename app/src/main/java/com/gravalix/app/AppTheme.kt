package com.gravalix.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue

/** Tema do app: Sistema, Claro ou Escuro. */
object AppTheme {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    fun isDark(c: Context): Boolean = when (Prefs.theme(c)) {
        LIGHT -> false
        DARK -> true
        else -> (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    fun apply(a: Activity) {
        a.setTheme(if (isDark(a)) R.style.Theme_Gravalix else R.style.Theme_Gravalix_Light)
    }

    /** Cor do tema atual a partir de um dos antigos R.color.* (text, subtext, accent, card, bg, danger). */
    fun color(c: Context, res: Int): Int {
        val attr = when (res) {
            R.color.text -> R.attr.gxText
            R.color.subtext -> R.attr.gxSub
            R.color.accent -> R.attr.gxAccent
            R.color.card -> R.attr.gxCard
            R.color.bg -> R.attr.gxBg
            R.color.danger -> R.attr.gxDanger
            else -> return c.getColor(res)
        }
        val tv = TypedValue()
        c.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
