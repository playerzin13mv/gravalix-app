package com.gravalix.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Um item da cena: imagem, PNGTuber ou texto. Posição e tamanho são frações da tela. */
class SceneItem(
    val id: String,
    val type: Int,
    var x: Float = 0.5f,
    var y: Float = 0.5f,
    var size: Float = 0.35f,
    var text: String = "Texto",
    var color: Int = 0xFFFFFFFF.toInt(),
    var flip: Boolean = false
) {
    companion object {
        const val IMAGE = 0
        const val PNGTUBER = 1
        const val TEXT = 2
    }

    var bmpA: Bitmap? = null   // imagem (ou PNGTuber de boca fechada)
    var bmpB: Bitmap? = null   // PNGTuber falando
}

/** Guarda e carrega a cena (JSON nas configurações + imagens na pasta do app). */
object LiveScene {

    private fun dir(c: Context): File = File(c.filesDir, "scene").also { it.mkdirs() }
    fun file(c: Context, id: String, slot: Int): File = File(dir(c), "${id}_$slot.png")

    fun load(c: Context): MutableList<SceneItem> {
        val out = ArrayList<SceneItem>()
        val raw = Prefs.scene(c)
        if (raw.isEmpty()) return out
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val it = SceneItem(
                    o.getString("id"), o.getInt("type"),
                    o.optDouble("x", 0.5).toFloat(), o.optDouble("y", 0.5).toFloat(),
                    o.optDouble("size", 0.35).toFloat(), o.optString("text", "Texto"),
                    o.optInt("color", 0xFFFFFFFF.toInt()), o.optBoolean("flip", false)
                )
                it.bmpA = decode(file(c, it.id, 0))
                it.bmpB = decode(file(c, it.id, 1))
                out.add(it)
            }
        } catch (e: Exception) {
            AppLog.e("cena: erro ao ler", e)
        }
        return out
    }

    fun save(c: Context, items: List<SceneItem>) {
        val arr = JSONArray()
        for (it in items) {
            arr.put(JSONObject().apply {
                put("id", it.id); put("type", it.type)
                put("x", it.x.toDouble()); put("y", it.y.toDouble()); put("size", it.size.toDouble())
                put("text", it.text); put("color", it.color); put("flip", it.flip)
            })
        }
        Prefs.setScene(c, arr.toString())
    }

    fun newId(): String = System.currentTimeMillis().toString(36) + (Math.random() * 1000).toInt()

    /** Copia a imagem escolhida para dentro do app (reduzida, no máx. 1024 px). */
    fun importImage(c: Context, uri: Uri, id: String, slot: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1400) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            val scale = 1024f / maxOf(bmp.width, bmp.height)
            val fin = if (scale < 1f) Bitmap.createScaledBitmap(
                bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true
            ) else bmp
            FileOutputStream(file(c, id, slot)).use { fin.compress(Bitmap.CompressFormat.PNG, 100, it) }
            fin
        } catch (e: Exception) {
            AppLog.e("cena: importar imagem", e)
            null
        }
    }

    fun delete(c: Context, id: String) {
        file(c, id, 0).delete()
        file(c, id, 1).delete()
    }

    private fun decode(f: File): Bitmap? = if (f.exists()) try { BitmapFactory.decodeFile(f.path) } catch (_: Exception) { null } else null
}
