package com.gravalix.app

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Toast

/** Tela da Live: escolhe a plataforma, cola a chave, edita a cena e entra no ar. */
class LiveActivity : BaseActivity() {

    private val platforms = listOf(
        "Twitch" to "rtmp://live.twitch.tv/app",
        "YouTube" to "rtmp://a.rtmp.youtube.com/live2",
        "Facebook" to "rtmps://live-api-s.facebook.com:443/rtmp/",
        "Outro" to ""
    )

    private lateinit var urlEdit: EditText
    private lateinit var keyEdit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@LiveActivity, 16), Ui.dp(this@LiveActivity, 16),
                Ui.dp(this@LiveActivity, 16), Ui.dp(this@LiveActivity, 24))
        }
        scroll.addView(col)
        setContentView(scroll)

        col.addView(Ui.text(this, getString(R.string.live_title), 24f, R.color.text, true))
        col.addView(Ui.hint(this, getString(R.string.live_intro)))

        // plataforma
        val plat = Ui.card(this, getString(R.string.live_platform))
        val group = RadioGroup(this)
        platforms.forEachIndexed { i, p ->
            val rb = RadioButton(this).apply {
                id = 1000 + i
                text = p.first
                setTextColor(AppTheme.color(this@LiveActivity, R.color.text))
            }
            group.addView(rb)
        }
        plat.addView(group)

        urlEdit = EditText(this).apply {
            setText(Prefs.liveUrl(this@LiveActivity))
            hint = getString(R.string.live_url_hint)
            setSingleLine(true)
            setTextColor(AppTheme.color(this@LiveActivity, R.color.text))
            setHintTextColor(AppTheme.color(this@LiveActivity, R.color.subtext))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        keyEdit = EditText(this).apply {
            setText(Prefs.liveKey(this@LiveActivity))
            hint = getString(R.string.live_key_hint)
            setSingleLine(true)
            setTextColor(AppTheme.color(this@LiveActivity, R.color.text))
            setHintTextColor(AppTheme.color(this@LiveActivity, R.color.subtext))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val idx = checkedId - 1000
            if (idx in platforms.indices) {
                Prefs.setLivePlatform(this, idx)
                if (platforms[idx].second.isNotEmpty()) urlEdit.setText(platforms[idx].second)
            }
        }
        group.check(1000 + Prefs.livePlatform(this).coerceIn(0, platforms.size - 1))
        plat.addView(Ui.hint(this, getString(R.string.live_url)))
        plat.addView(urlEdit)
        plat.addView(Ui.hint(this, getString(R.string.live_key)))
        plat.addView(keyEdit)
        plat.addView(Ui.hint(this, getString(R.string.live_key_help)))
        Ui.addCard(col, plat)

        // cena
        val sc = Ui.card(this, getString(R.string.live_scene))
        sc.addView(Ui.hint(this, getString(R.string.live_scene_hint)))
        sc.addView(Ui.button(this, getString(R.string.live_edit_scene)) {
            saveFields()
            startActivity(Intent(this, LiveEditorActivity::class.java))
        })
        Ui.addCard(col, sc)

        // ir ao ar
        val go = Ui.card(this, getString(R.string.live_go))
        go.addView(Ui.hint(this, getString(R.string.live_warning)))
        go.addView(Ui.button(this, getString(R.string.live_start), true) { startLive() })
        go.addView(Ui.hint(this, getString(R.string.live_stop_hint)))
        Ui.addCard(col, go)
    }

    override fun onPause() {
        saveFields()
        super.onPause()
    }

    private fun saveFields() {
        Prefs.setLiveUrl(this, urlEdit.text.toString().trim())
        Prefs.setLiveKey(this, keyEdit.text.toString().trim())
    }

    private fun startLive() {
        saveFields()
        val url = Prefs.liveUrl(this)
        if (!url.startsWith("rtmp://", true) && !url.startsWith("rtmps://", true)) {
            Toast.makeText(this, R.string.live_bad_url, Toast.LENGTH_LONG).show()
            return
        }
        if (Prefs.liveKey(this).isEmpty()) {
            Toast.makeText(this, R.string.live_no_key, Toast.LENGTH_LONG).show()
            return
        }
        if (RecorderService.isActive) {
            Toast.makeText(this, R.string.live_already, Toast.LENGTH_LONG).show()
            return
        }
        startActivity(
            Intent(this, StartActivity::class.java)
                .putExtra(RecorderService.EXTRA_LIVE, true)
                .putExtra(StartActivity.EXTRA_GO_HOME, true)
        )
    }
}
