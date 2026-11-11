package com.gravalix.app

import android.app.Activity
import android.app.AlertDialog
import android.app.RecoverableSecurityException
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.LruCache
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class Recording(
    val id: Long,
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateMs: Long
)

/**
 * Tela inicial: a lista dos vídeos gravados (assistir, compartilhar, YouTube, excluir),
 * o botão de gravar e a chave da bolha flutuante. As configurações ficam em [SettingsActivity].
 */
class MainActivity : Activity() {

    private companion object {
        const val REQ_DELETE = 20
        const val REQ_OVERLAY = 21
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }

    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var btnStart: Button
    private lateinit var switchBubble: Switch
    private val items = ArrayList<Recording>()
    private val adapter = RecordingsAdapter()
    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val thumbCache = LruCache<Long, Bitmap>(60)
    private var ignoreSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        listView = findViewById(R.id.list)
        emptyView = findViewById(R.id.empty)
        btnStart = findViewById(R.id.btnStart)
        switchBubble = findViewById(R.id.switchBubble)
        listView.adapter = adapter
        listView.emptyView = emptyView

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnStart.setOnClickListener {
            if (RecorderService.isActive) {
                startService(
                    Intent(this, RecorderService::class.java).setAction(RecorderService.ACTION_STOP)
                )
                btnStart.postDelayed({ updateStartButton() }, 700)
            } else {
                startActivity(
                    Intent(this, StartActivity::class.java).putExtra(StartActivity.EXTRA_GO_HOME, true)
                )
            }
        }

        switchBubble.setOnCheckedChangeListener { _, checked ->
            if (ignoreSwitch) return@setOnCheckedChangeListener
            Prefs.setBubble(this, checked)
            if (checked) enableBubble() else BubbleService.stop(this)
        }
    }

    override fun onResume() {
        super.onResume()
        load()
        updateStartButton()

        ignoreSwitch = true
        switchBubble.isChecked = Prefs.bubble(this)
        ignoreSwitch = false

        // Bolha ligada: garante que ela está na tela
        if (Prefs.bubble(this) && Settings.canDrawOverlays(this) && !BubbleService.isRunning) {
            BubbleService.start(this)
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun updateStartButton() {
        btnStart.setText(if (RecorderService.isActive) R.string.btn_stop else R.string.btn_start)
    }

    private fun enableBubble() {
        if (Settings.canDrawOverlays(this)) {
            BubbleService.start(this)
            return
        }
        Toast.makeText(this, R.string.need_overlay_bubble, Toast.LENGTH_LONG).show()
        try {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                REQ_OVERLAY
            )
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------- lista

    private fun load() {
        items.clear()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        )
        try {
            contentResolver.query(
                collection,
                projection,
                MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                arrayOf("Movies/Gravalix%"),
                MediaStore.Video.Media.DATE_ADDED + " DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    items.add(
                        Recording(
                            id,
                            ContentUris.withAppendedId(collection, id),
                            c.getString(nameCol) ?: "Gravalix",
                            c.getLong(durCol),
                            c.getLong(sizeCol),
                            c.getLong(dateCol) * 1000L
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // lista fica vazia
        }
        adapter.notifyDataSetChanged()
    }

    // ------------------------------------------------------------------ ações

    private fun play(r: Recording) {
        startActivity(Intent(this, PlayerActivity::class.java).setData(r.uri))
    }

    private fun shareIntent(r: Recording): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, r.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun share(r: Recording) {
        startActivity(Intent.createChooser(shareIntent(r), getString(R.string.share_title)))
    }

    private fun shareToYoutube(r: Recording) {
        val intent = shareIntent(r).setPackage(YOUTUBE_PACKAGE)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.youtube_missing, Toast.LENGTH_LONG).show()
            share(r)
        }
    }

    private fun confirmDelete(r: Recording) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.delete_confirm) { _, _ -> delete(r) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun delete(r: Recording) {
        try {
            val n = contentResolver.delete(r.uri, null, null)
            if (n > 0) {
                load()
                return
            }
        } catch (e: SecurityException) {
            val sender: IntentSender? = if (Build.VERSION.SDK_INT >= 30) {
                MediaStore.createDeleteRequest(contentResolver, listOf(r.uri)).intentSender
            } else if (e is RecoverableSecurityException) {
                e.userAction.actionIntent.intentSender
            } else {
                null
            }
            if (sender != null) {
                try {
                    startIntentSenderForResult(sender, REQ_DELETE, null, 0, 0, 0)
                } catch (_: IntentSender.SendIntentException) {
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_DELETE -> load()
            REQ_OVERLAY -> {
                if (Settings.canDrawOverlays(this)) {
                    BubbleService.start(this)
                } else {
                    Prefs.setBubble(this, false)
                    ignoreSwitch = true
                    switchBubble.isChecked = false
                    ignoreSwitch = false
                }
            }
        }
    }

    // ----------------------------------------------------------------- adapter

    private fun formatDuration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    private fun formatSize(bytes: Long): String =
        String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0)

    private inner class RecordingsAdapter : BaseAdapter() {

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): Any = items[position]

        override fun getItemId(position: Int): Long = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.item_recording, parent, false)
            val r = items[position]

            view.findViewById<TextView>(R.id.tvName).text = r.name
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(r.dateMs))
            view.findViewById<TextView>(R.id.tvInfo).text =
                formatDuration(r.durationMs) + " · " + formatSize(r.sizeBytes) + "\n" + date

            val thumb = view.findViewById<ImageView>(R.id.thumb)
            thumb.tag = r.id
            val cached = thumbCache.get(r.id)
            if (cached != null) {
                thumb.setImageBitmap(cached)
            } else {
                thumb.setImageBitmap(null)
                try {
                    executor.execute {
                        try {
                            val bmp = contentResolver.loadThumbnail(r.uri, Size(320, 180), null)
                            thumbCache.put(r.id, bmp)
                            mainHandler.post {
                                if (thumb.tag == r.id) thumb.setImageBitmap(bmp)
                            }
                        } catch (_: Exception) {
                        }
                    }
                } catch (_: Exception) {
                }
            }

            thumb.setOnClickListener { play(r) }
            view.findViewById<Button>(R.id.btnPlay).setOnClickListener { play(r) }
            view.findViewById<Button>(R.id.btnShare).setOnClickListener { share(r) }
            view.findViewById<Button>(R.id.btnYoutube).setOnClickListener { shareToYoutube(r) }
            view.findViewById<Button>(R.id.btnDelete).setOnClickListener { confirmDelete(r) }
            return view
        }
    }
}
