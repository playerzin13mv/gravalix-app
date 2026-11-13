package com.gravalix.app

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registro simples em arquivo, para descobrir por que algo parou. Fica só no telefone. */
object AppLog {
    private var file: File? = null

    fun init(c: Context) {
        file = File(c.applicationContext.filesDir, "gravalix-log.txt")
        file?.let { if (it.length() > 200_000) it.delete() }
    }

    fun path(): File? = file

    @Synchronized
    fun i(msg: String) {
        Log.i("Gravalix", msg)
        write("I", msg)
    }

    @Synchronized
    fun e(msg: String, t: Throwable? = null) {
        Log.e("Gravalix", msg, t)
        write("E", msg + if (t != null) "\n" + Log.getStackTraceString(t) else "")
    }

    private fun write(level: String, msg: String) {
        try {
            val t = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())
            file?.appendText("$t $level $msg\n")
        } catch (_: Exception) {
        }
    }

    fun read(): String = try {
        file?.takeIf { it.exists() }?.readText()?.takeLast(60_000) ?: ""
    } catch (_: Exception) {
        ""
    }

    fun clear() {
        try { file?.delete() } catch (_: Exception) {}
    }
}
