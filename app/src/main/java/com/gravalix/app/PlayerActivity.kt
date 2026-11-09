package com.gravalix.app

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView

/** Player simples para assistir as gravações dentro do app. */
class PlayerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }

        val video = findViewById<VideoView>(R.id.videoView)
        val controller = MediaController(this)
        controller.setAnchorView(video)
        video.setMediaController(controller)
        video.setOnPreparedListener { video.start() }
        video.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, R.string.toast_error_play, Toast.LENGTH_LONG).show()
            finish()
            true
        }
        video.setVideoURI(uri)
    }
}
