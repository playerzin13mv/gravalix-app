package com.gravalix.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Todas as configurações do Gravalix, separadas da lista de vídeos. */
class SettingsActivity : Activity() {

    private lateinit var content: LinearLayout
    private lateinit var touchStatus: TextView
    private lateinit var btnDev: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scroll.addView(content)
        setContentView(scroll)

        content.addView(text(getString(R.string.settings_title), 24f, R.color.text, true))

        // Áudio
        card(getString(R.string.section_audio)).also { c ->
            radios(
                c,
                listOf(
                    RecorderService.AUDIO_INTERNAL to getString(R.string.audio_internal),
                    RecorderService.AUDIO_MIC to getString(R.string.audio_mic),
                    RecorderService.AUDIO_BOTH to getString(R.string.audio_both),
                    RecorderService.AUDIO_NONE to getString(R.string.audio_mute)
                ),
                Prefs.audio(this)
            ) { Prefs.setAudio(this, it) }
        }

        // Qualidade
        card(getString(R.string.section_quality)).also { c ->
            radios(
                c,
                listOf(
                    RecorderService.QUALITY_HD to getString(R.string.quality_hd),
                    RecorderService.QUALITY_FHD to getString(R.string.quality_fhd),
                    RecorderService.QUALITY_FHD60 to getString(R.string.quality_fhd60)
                ),
                Prefs.quality(this)
            ) { Prefs.setQuality(this, it) }
        }

        // Contagem regressiva
        card(getString(R.string.section_countdown)).also { c ->
            radios(
                c,
                listOf(
                    0 to getString(R.string.countdown_off),
                    3 to getString(R.string.countdown_3),
                    5 to getString(R.string.countdown_5),
                    10 to getString(R.string.countdown_10)
                ),
                Prefs.countdown(this)
            ) { Prefs.setCountdown(this, it) }
        }

        // Bolha
        card(getString(R.string.section_bubble)).also { c ->
            c.addView(hint(getString(R.string.bubble_mode_title)))
            radios(
                c,
                listOf(
                    Prefs.BUBBLE_HIDDEN_FROM_VIDEO to getString(R.string.bubble_hidden),
                    Prefs.BUBBLE_GONE to getString(R.string.bubble_gone)
                ),
                Prefs.bubbleMode(this)
            ) { Prefs.setBubbleMode(this, it) }
            c.addView(hint(getString(R.string.bubble_mode_hint)))
        }

        // Caneta
        card(getString(R.string.section_pen)).also { c ->
            switchRow(c, getString(R.string.opt_pen_fade), getString(R.string.opt_pen_fade_hint),
                Prefs.penFade(this)) { Prefs.setPenFade(this, it) }
        }

        // Mostrar toques
        card(getString(R.string.section_touches)).also { c ->
            switchRow(c, getString(R.string.opt_touches), getString(R.string.opt_touches_hint),
                Prefs.showTouches(this)) {
                Prefs.setShowTouches(this, it)
                if (it && !TouchIndicator.canToggle(this)) {
                    Toast.makeText(this, R.string.touch_manual, Toast.LENGTH_LONG).show()
                }
            }
            touchStatus = hint("")
            c.addView(touchStatus)
            btnDev = Button(this).apply {
                text = getString(R.string.btn_open_dev)
                isAllCaps = false
                setTextColor(color(R.color.text))
                setBackgroundResource(R.drawable.bg_button_secondary)
                stateListAnimator = null
                setOnClickListener { TouchIndicator.openDeveloperSettings(this@SettingsActivity) }
            }
            c.addView(btnDev, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(6) })
        }

        // Outras opções
        card(getString(R.string.section_options)).also { c ->
            switchRow(c, getString(R.string.opt_landscape), getString(R.string.opt_landscape_hint),
                Prefs.landscape(this)) { Prefs.setLandscape(this, it) }
            switchRow(c, getString(R.string.opt_shake), getString(R.string.opt_shake_hint),
                Prefs.shakeStop(this)) { Prefs.setShakeStop(this, it) }
            c.addView(hint(getString(R.string.tile_tip)))
        }
    }

    override fun onResume() {
        super.onResume()
        val ok = TouchIndicator.canToggle(this)
        touchStatus.text = getString(if (ok) R.string.touch_status_ok else R.string.touch_status_no)
        btnDev.visibility = if (ok) View.GONE else View.VISIBLE
    }

    // ------------------------------------------------------------------ helpers

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun color(id: Int): Int = getColor(id)

    private fun text(s: String, size: Float, colorRes: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color(colorRes))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun hint(s: String): TextView =
        text(s, 12f, R.color.subtext).apply { setPadding(0, dp(4), 0, dp(2)) }

    private fun card(title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        c.addView(text(title, 13f, R.color.accent, true))
        content.addView(c, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })
        return c
    }

    private fun radios(
        parent: LinearLayout,
        options: List<Pair<Int, String>>,
        selected: Int,
        onChange: (Int) -> Unit
    ) {
        val group = RadioGroup(this)
        val byId = HashMap<Int, Int>()
        for ((value, label) in options) {
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                setTextColor(color(R.color.text))
                minHeight = dp(44)
            }
            byId[rb.id] = value
            group.addView(rb)
            if (value == selected) rb.isChecked = true
        }
        group.setOnCheckedChangeListener { _, id -> byId[id]?.let(onChange) }
        parent.addView(group)
    }

    private fun switchRow(
        parent: LinearLayout,
        label: String,
        hintText: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val sw = Switch(this).apply {
            text = label
            setTextColor(color(R.color.text))
            isChecked = checked
            minHeight = dp(44)
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        parent.addView(sw, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })
        parent.addView(hint(hintText))
    }
}
