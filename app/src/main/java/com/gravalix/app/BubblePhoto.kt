package com.gravalix.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/** Foto escolhida pela pessoa para ser o ícone da bolha (recortada em quadrado, 256×256). */
object BubblePhoto {

    private const val SIZE = 256

    private fun file(c: Context) = File(c.filesDir, "bubble_photo.png")

    fun load(c: Context): Bitmap? {
        val f = file(c)
        if (!f.exists()) return null
        return try {
            BitmapFactory.decodeFile(f.path)
        } catch (_: Exception) {
            null
        }
    }

    fun clear(c: Context) {
        file(c).delete()
    }

    /** Lê a imagem escolhida, corrige a rotação, recorta no centro e guarda no app. */
    fun saveFrom(c: Context, uri: Uri): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= SIZE * 2 && bounds.outHeight / (sample * 2) >= SIZE * 2) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = c.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false

            val rotation = c.contentResolver.openInputStream(uri)?.use {
                try {
                    when (ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } catch (_: Exception) {
                    0
                }
            } ?: 0
            if (rotation != 0) {
                val m = Matrix()
                m.postRotate(rotation.toFloat())
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }

            val side = min(bmp.width, bmp.height)
            val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
            FileOutputStream(file(c)).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        } catch (_: Exception) {
            false
        }
    }
}
