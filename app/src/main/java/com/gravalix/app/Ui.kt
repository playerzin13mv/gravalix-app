package com.gravalix.app

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Pequenas peças de tela usadas nas telas feitas em código. */
object Ui {
    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun text(c: Context, s: String, size: Float, colorRes: Int, bold: Boolean = false): TextView =
        TextView(c).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(AppTheme.color(c, colorRes))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun hint(c: Context, s: String): TextView =
        text(c, s, 12f, R.color.subtext).apply { setPadding(0, dp(c, 4), 0, dp(c, 2)) }

    fun card(c: Context, title: String): LinearLayout {
        val l = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
        }
        if (title.isNotEmpty()) l.addView(text(c, title, 13f, R.color.accent, true))
        return l
    }

    fun addCard(parent: LinearLayout, card: LinearLayout) {
        parent.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(parent.context, 12) })
    }

    fun button(c: Context, label: String, primary: Boolean = false, onClick: () -> Unit): Button =
        Button(c).apply {
            text = label
            isAllCaps = false
            setTextColor(if (primary) 0xFFFFFFFF.toInt() else AppTheme.color(c, R.color.text))
            setBackgroundResource(if (primary) R.drawable.bg_button_primary else R.drawable.bg_button_secondary)
            stateListAnimator = null
            minHeight = 0
            minimumHeight = 0
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, if (primary) 52 else 42)
            ).apply { topMargin = dp(c, 6) }
            setOnClickListener { onClick() }
        }
}
