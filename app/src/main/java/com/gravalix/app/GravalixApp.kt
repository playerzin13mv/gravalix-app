package com.gravalix.app

import android.app.Application

class GravalixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.i("app iniciado (v${BuildConfigInfo.version(this)})")
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            AppLog.e("QUEDA em ${t.name}", e)
            old?.uncaughtException(t, e)
        }
    }
}

object BuildConfigInfo {
    fun version(c: android.content.Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }
}
