package com.gravalix.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Base das telas: aplica o tema escolhido e as animações de transição. */
open class BaseActivity : Activity() {

    private var appliedDark = true

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.apply(this)
        appliedDark = AppTheme.isDark(this)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (AppTheme.isDark(this) != appliedDark) recreate()
    }

    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        super.startActivityForResult(intent, requestCode, options)
        if (Prefs.animations(this)) overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        if (Prefs.animations(this)) overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}
