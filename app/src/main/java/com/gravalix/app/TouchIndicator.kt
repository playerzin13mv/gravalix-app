package com.gravalix.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast

/**
 * "Mostrar toques" no vídeo.
 *
 * O Android só deixa um app ligar essa opção se ele tiver a permissão especial
 * WRITE_SECURE_SETTINGS, que é liberada uma única vez pelo comando do README
 * (script ativar-toques.sh no Termux). Sem ela, o app abre as Opções do
 * desenvolvedor para você ligar "Mostrar toques" na mão.
 */
object TouchIndicator {

    private const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"
    private const val KEY = "show_touches"

    fun canToggle(c: Context): Boolean =
        c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun isOn(c: Context): Boolean =
        try {
            Settings.System.getInt(c.contentResolver, KEY, 0) == 1
        } catch (_: Exception) {
            false
        }

    /** Liga/desliga o sistema. Retorna false se não tem permissão. */
    fun set(c: Context, on: Boolean): Boolean {
        if (!canToggle(c)) return false
        return try {
            Settings.System.putInt(c.contentResolver, KEY, if (on) 1 else 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Muda a opção "mostrar toques" do Gravalix. Se não tem a permissão especial,
     * abre as Opções do desenvolvedor e explica o que fazer.
     */
    fun toggleFromUser(c: Context): Boolean {
        val want = !Prefs.showTouches(c)
        if (canToggle(c)) {
            Prefs.setShowTouches(c, want)
            set(c, want)
            return want
        }
        Prefs.setShowTouches(c, want)
        Toast.makeText(c, R.string.touch_manual, Toast.LENGTH_LONG).show()
        if (want) openDeveloperSettings(c)
        return want
    }

    fun openDeveloperSettings(c: Context) {
        try {
            c.startActivity(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                c.startActivity(
                    Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }
}
