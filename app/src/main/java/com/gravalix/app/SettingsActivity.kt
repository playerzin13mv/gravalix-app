package com.gravalix.app

import android.app.Activity
import android.content.Context
import android.content.Intent
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
class SettingsActivity : BaseActivity() {

    private companion object {
        const val REQ_PHOTO = 31
    }

    private lateinit var photoPreview: IconButton
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

        // Aparência
        card(getString(R.string.section_appearance)).also { c ->
            c.addView(hint(getString(R.string.theme_title)))
            radios(
                c,
                listOf(
                    AppTheme.SYSTEM to getString(R.string.theme_system),
                    AppTheme.LIGHT to getString(R.string.theme_light),
                    AppTheme.DARK to getString(R.string.theme_dark)
                ),
                Prefs.theme(this)
            ) {
                Prefs.setTheme(this, it)
                recreate()
            }
            switchRow(c, getString(R.string.opt_animations), "", Prefs.animations(this)) {
                Prefs.setAnimations(this, it)
            }
        }

        // Live
        card(getString(R.string.btn_live)).also { c ->
            c.addView(hint(getString(R.string.settings_live_hint)))
            c.addView(smallButton(getString(R.string.live_title)) {
                startActivity(Intent(this, LiveActivity::class.java))
            })
            c.addView(smallButton(getString(R.string.editor_title)) {
                startActivity(Intent(this, LiveEditorActivity::class.java))
            })
        }

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

        // Bolha durante a gravação
        card(getString(R.string.section_bubble)).also { c ->
            c.addView(hint(getString(R.string.bubble_mode_title)))
            radios(
                c,
                listOf(
                    Prefs.BUBBLE_GONE to getString(R.string.bubble_gone),
                    Prefs.BUBBLE_VISIBLE to getString(R.string.bubble_visible),
                    Prefs.BUBBLE_HIDDEN_FROM_VIDEO to getString(R.string.bubble_hidden)
                ),
                Prefs.bubbleMode(this)
            ) { Prefs.setBubbleMode(this, it) }
            c.addView(hint(getString(R.string.bubble_mode_hint)))
        }

        // Personalizar a bolha
        card(getString(R.string.section_bubble_style)).also { c ->
            // Foto da bolha
            c.addView(hint(getString(R.string.style_photo)))
            photoPreview = IconButton(this, IconButton.CAMERA, 0xE68781FF.toInt())
            photoPreview.photo = BubblePhoto.load(this)
            val photoRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            photoRow.addView(photoPreview, LinearLayout.LayoutParams(dp(64), dp(64)))
            val photoButtons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            photoButtons.addView(smallButton(getString(R.string.btn_choose_photo)) {
                val pick = Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
                try {
                    startActivityForResult(pick, REQ_PHOTO)
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.toast_photo_error, Toast.LENGTH_LONG).show()
                }
            })
            photoButtons.addView(smallButton(getString(R.string.btn_default_icon)) {
                BubblePhoto.clear(this)
                photoPreview.photo = null
                BubbleService.reload(this)
            })
            photoRow.addView(photoButtons, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
            c.addView(photoRow)
            c.addView(hint(getString(R.string.style_photo_hint)))

            c.addView(hint(getString(R.string.style_size)))
            radios(
                c,
                listOf(
                    0 to getString(R.string.size_small),
                    1 to getString(R.string.size_medium),
                    2 to getString(R.string.size_large)
                ),
                Prefs.bubbleSize(this)
            ) { Prefs.setBubbleSize(this, it); BubbleService.reload(this) }

            c.addView(hint(getString(R.string.style_opacity)))
            radios(
                c,
                listOf(
                    0 to "100%",
                    1 to "80%",
                    2 to "60%",
                    3 to "40%"
                ),
                Prefs.bubbleAlpha(this)
            ) { Prefs.setBubbleAlpha(this, it); BubbleService.reload(this) }

            c.addView(hint(getString(R.string.style_color)))
            radios(
                c,
                listOf(
                    0 to getString(R.string.color_purple),
                    1 to getString(R.string.color_red),
                    2 to getString(R.string.color_green),
                    3 to getString(R.string.color_blue),
                    4 to getString(R.string.color_orange),
                    5 to getString(R.string.color_gray)
                ),
                Prefs.bubbleColor(this)
            ) { Prefs.setBubbleColor(this, it); BubbleService.reload(this) }

            c.addView(hint(getString(R.string.style_buttons)))
            switchRow(c, getString(R.string.menu_pen), "", Prefs.menuPen(this)) {
                Prefs.setMenuPen(this, it); BubbleService.reload(this)
            }
            switchRow(c, getString(R.string.menu_touches), "", Prefs.menuTouches(this)) {
                Prefs.setMenuTouches(this, it); BubbleService.reload(this)
            }
            switchRow(c, getString(R.string.menu_home), "", Prefs.menuHome(this)) {
                Prefs.setMenuHome(this, it); BubbleService.reload(this)
            }
            c.addView(hint(getString(R.string.style_side_hint)))
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
                TouchIndicator.applyChoice(this, it)
            }
            touchStatus = hint("")
            c.addView(touchStatus)
            btnDev = Button(this).apply {
                text = getString(R.string.btn_open_a11y)
                isAllCaps = false
                setTextColor(color(R.color.text))
                setBackgroundResource(R.drawable.bg_button_secondary)
                stateListAnimator = null
                setOnClickListener { TouchIndicator.openAccessibilitySettings(this@SettingsActivity) }
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

        // Se algo parar sozinho
        card(getString(R.string.section_problems)).also { c ->
            c.addView(hint(getString(R.string.problems_hint)))
            c.addView(smallButton(getString(R.string.btn_battery)) {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                }
            })
            c.addView(smallButton(getString(R.string.btn_share_log)) {
                val log = AppLog.read().ifEmpty { getString(R.string.log_empty) }
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Gravalix – relatório")
                    .putExtra(Intent.EXTRA_TEXT, log)
                startActivity(Intent.createChooser(send, getString(R.string.btn_share_log)))
            })
            c.addView(smallButton(getString(R.string.btn_clear_log)) {
                AppLog.clear()
                Toast.makeText(this, R.string.toast_log_cleared, Toast.LENGTH_SHORT).show()
            })
        }

        // Entrada suave dos cartões
        if (Prefs.animations(this)) {
            for (i in 0 until content.childCount) {
                val anim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.rise_in)
                anim.startOffset = (i * 55L).coerceAtMost(500L)
                content.getChildAt(i).startAnimation(anim)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PHOTO || resultCode != RESULT_OK) return
        val uri = data?.data
        if (uri != null && BubblePhoto.saveFrom(this, uri)) {
            photoPreview.photo = BubblePhoto.load(this)
            BubbleService.reload(this)
            Toast.makeText(this, R.string.toast_photo_ok, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.toast_photo_error, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val system = TouchIndicator.canToggle(this)
        val app = TouchAccessibilityService.connected
        touchStatus.text = getString(
            when {
                system -> R.string.touch_status_system
                app -> R.string.touch_status_app
                else -> R.string.touch_status_none
            }
        )
        btnDev.visibility = if (system || app) View.GONE else View.VISIBLE
    }

    // ------------------------------------------------------------------ helpers

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun color(id: Int): Int = AppTheme.color(this, id)

    private fun text(s: String, size: Float, colorRes: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(color(colorRes))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun smallButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(color(R.color.text))
            setBackgroundResource(R.drawable.bg_button_secondary)
            stateListAnimator = null
            minHeight = 0
            minimumHeight = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)
            ).apply { bottomMargin = dp(6) }
            setOnClickListener { onClick() }
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
