package com.gravalix.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast

/**
 * "Mostrar toques" no vídeo. Dois jeitos, o app usa o que estiver disponível:
 *
 * 1) Toques do sistema (exatos, no ponto do dedo): precisa da permissão especial
 *    WRITE_SECURE_SETTINGS, liberada uma única vez pelo script ativar-toques.sh (Termux).
 * 2) Toques do Gravalix: serviço de acessibilidade do próprio app, que desenha um círculo
 *    no centro do que foi tocado. Basta ligar o serviço uma vez em Acessibilidade.
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
        Prefs.setShowTouches(c, want)
        applyChoice(c, want)
        return want
    }

    /**
     * Aplica a escolha. Prioridade: toques do sistema (exatos, com a permissão especial),
     * depois os toques do próprio app (serviço de acessibilidade). Se nenhum dos dois está
     * pronto, leva você para ligar o serviço do Gravalix.
     */
    fun applyChoice(c: Context, want: Boolean) {
        if (canToggle(c)) {
            set(c, want)
            return
        }
        if (want && !TouchAccessibilityService.connected) {
            Toast.makeText(c, R.string.touch_need_a11y, Toast.LENGTH_LONG).show()
            openAccessibilitySettings(c)
        }
    }

    fun openAccessibilitySettings(c: Context) {
        try {
            c.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
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
