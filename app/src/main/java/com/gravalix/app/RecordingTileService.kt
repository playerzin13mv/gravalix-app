package com.gravalix.app

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Botão no painel rápido (puxando a barra de cima). Inicia e para a gravação sem
 * mostrar nada na tela, então não aparece no vídeo.
 */
class RecordingTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (RecorderService.isActive) {
            startService(
                Intent(this, RecorderService::class.java).setAction(RecorderService.ACTION_STOP)
            )
        } else {
            val intent = Intent(this, StartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                val pi = PendingIntent.getActivity(this, 20, intent, PendingIntent.FLAG_IMMUTABLE)
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val active = RecorderService.isActive
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(if (active) R.string.tile_stop else R.string.tile_start)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_rec)
        tile.updateTile()
    }
}
