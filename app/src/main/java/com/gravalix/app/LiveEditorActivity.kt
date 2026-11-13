package com.gravalix.app

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.Toast

/** Editor da cena da Live: arraste e use dois dedos para mudar o tamanho. */
class LiveEditorActivity : BaseActivity() {

    private companion object {
        const val REQ_NEW_IMAGE = 41
        const val REQ_NEW_TUBER = 42
        const val REQ_REPLACE = 43
        const val REQ_TALK = 44
        val COLORS = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFFFE066.toInt(), 0xFFFF5470.toInt(),
            0xFF30D158.toInt(), 0xFF0A84FF.toInt(), 0xFF000000.toInt()
        )
    }

    private lateinit var items: MutableList<SceneItem>
    private lateinit var scene: SceneView
    private lateinit var panel: LinearLayout
    private lateinit var seek: SeekBar
    private val meter by lazy { AudioMeter(this) }
    private var testTalk = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        items = LiveScene.load(this)

        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@LiveEditorActivity, 16), Ui.dp(this@LiveEditorActivity, 16),
                Ui.dp(this@LiveEditorActivity, 16), Ui.dp(this@LiveEditorActivity, 24))
        }
        scroll.addView(col)
        setContentView(scroll)

        col.addView(Ui.text(this, getString(R.string.editor_title), 24f, R.color.text, true))
        col.addView(Ui.hint(this, getString(R.string.editor_hint)))

        // prévia no formato da tela do celular
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        val ratio = dm.heightPixels.toFloat() / dm.widthPixels
        val previewW = (dm.widthPixels * 0.62f).toInt()
        val previewH = (previewW * ratio).toInt()
        scene = SceneView(this, items, true)
        val frame = FrameLayout(this).apply { setBackgroundColor(0xFF2A2740.toInt()) }
        frame.addView(scene, FrameLayout.LayoutParams(previewW, previewH))
        col.addView(frame, LinearLayout.LayoutParams(previewW, previewH).apply {
            topMargin = Ui.dp(this@LiveEditorActivity, 10)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        })
        scene.onSelect = { updatePanel() }
        scene.onChange = { }

        // adicionar
        val add = Ui.card(this, getString(R.string.editor_add))
        add.addView(Ui.button(this, getString(R.string.editor_add_tuber)) { pick(REQ_NEW_TUBER) })
        add.addView(Ui.button(this, getString(R.string.editor_add_image)) { pick(REQ_NEW_IMAGE) })
        add.addView(Ui.button(this, getString(R.string.editor_add_text)) {
            val it = SceneItem(LiveScene.newId(), SceneItem.TEXT, 0.5f, 0.12f, 0.5f)
            items.add(it)
            scene.selected = it
            updatePanel()
            askText(it)
        })
        Ui.addCard(col, add)

        // item selecionado
        panel = Ui.card(this, getString(R.string.editor_selected))
        seek = SeekBar(this).apply {
            max = 120
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    scene.selected?.size = (p / 100f).coerceIn(0.05f, 1.2f)
                    scene.invalidate()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        Ui.addCard(col, panel)

        // opções gerais
        val opts = Ui.card(this, getString(R.string.editor_options))
        val testSw = Switch(this).apply {
            text = getString(R.string.editor_test_talk)
            setTextColor(AppTheme.color(this@LiveEditorActivity, R.color.text))
            setOnCheckedChangeListener { _, on -> testTalk = on; scene.forceTalk = on }
        }
        opts.addView(testSw)
        val onRec = Switch(this).apply {
            text = getString(R.string.editor_on_record)
            setTextColor(AppTheme.color(this@LiveEditorActivity, R.color.text))
            isChecked = Prefs.sceneOnRecord(this@LiveEditorActivity)
            setOnCheckedChangeListener { _, on -> Prefs.setSceneOnRecord(this@LiveEditorActivity, on) }
        }
        opts.addView(onRec)
        opts.addView(Ui.hint(this, getString(R.string.editor_mic_hint)))
        Ui.addCard(col, opts)

        updatePanel()
    }

    override fun onResume() {
        super.onResume()
        meter.start()
    }

    override fun onPause() {
        meter.stop()
        LiveScene.save(this, items)
        super.onPause()
    }

    private fun pick(req: Int) {
        try {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), req)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_photo_error, Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_NEW_IMAGE, REQ_NEW_TUBER -> {
                val tuber = requestCode == REQ_NEW_TUBER
                val it = SceneItem(LiveScene.newId(), if (tuber) SceneItem.PNGTUBER else SceneItem.IMAGE,
                    0.5f, 0.6f, if (tuber) 0.45f else 0.35f)
                val bmp = LiveScene.importImage(this, uri, it.id, 0)
                if (bmp == null) {
                    Toast.makeText(this, R.string.toast_photo_error, Toast.LENGTH_LONG).show()
                    return
                }
                it.bmpA = bmp
                items.add(it)
                scene.selected = it
                updatePanel()
                if (tuber) Toast.makeText(this, R.string.editor_tuber_next, Toast.LENGTH_LONG).show()
            }
            REQ_REPLACE, REQ_TALK -> {
                val sel = scene.selected ?: return
                val slot = if (requestCode == REQ_TALK) 1 else 0
                val bmp = LiveScene.importImage(this, uri, sel.id, slot)
                if (bmp == null) {
                    Toast.makeText(this, R.string.toast_photo_error, Toast.LENGTH_LONG).show()
                    return
                }
                if (slot == 1) sel.bmpB = bmp else sel.bmpA = bmp
                scene.invalidate()
            }
        }
    }

    private fun askText(item: SceneItem) {
        val et = EditText(this).apply { setText(item.text); setSingleLine(true) }
        AlertDialog.Builder(this)
            .setTitle(R.string.editor_text_title)
            .setView(et)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val t = et.text.toString().trim()
                item.text = if (t.isEmpty()) " " else t
                scene.invalidate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updatePanel() {
        // mantém só o título (primeiro filho) e refaz o resto
        while (panel.childCount > 1) panel.removeViewAt(1)
        val sel = scene.selected
        if (sel == null) {
            panel.addView(Ui.hint(this, getString(R.string.editor_none)))
            return
        }
        seek.progress = (sel.size * 100).toInt().coerceIn(0, 120)
        panel.addView(Ui.hint(this, getString(R.string.editor_size)))
        (seek.parent as? android.view.ViewGroup)?.removeView(seek)
        panel.addView(seek)
        if (sel.type == SceneItem.TEXT) {
            panel.addView(Ui.button(this, getString(R.string.editor_edit_text)) { askText(sel) })
            panel.addView(Ui.button(this, getString(R.string.editor_text_color)) {
                val i = COLORS.indexOf(sel.color)
                sel.color = COLORS[(i + 1) % COLORS.size]
                scene.invalidate()
            })
        } else {
            panel.addView(Ui.button(this, getString(R.string.editor_replace)) { pick(REQ_REPLACE) })
            if (sel.type == SceneItem.PNGTUBER) {
                panel.addView(Ui.button(this, getString(R.string.editor_talk_image)) { pick(REQ_TALK) })
            }
            panel.addView(Ui.button(this, getString(R.string.editor_flip)) {
                sel.flip = !sel.flip
                scene.invalidate()
            })
        }
        panel.addView(Ui.button(this, getString(R.string.editor_front)) {
            items.remove(sel); items.add(sel); scene.invalidate()
        })
        panel.addView(Ui.button(this, getString(R.string.editor_delete)) {
            items.remove(sel)
            LiveScene.delete(this, sel.id)
            scene.selected = null
            updatePanel()
        })
    }
}
