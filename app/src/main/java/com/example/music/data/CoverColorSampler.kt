package com.example.music.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.music.Song
import kotlin.math.abs

/**
 * Оттенок обложки для сортировки «по обложке».
 * Берём картинку в миниатюре, усредняем несколько пикселей и возвращаем hue (0..359).
 * Результат кэшируется в настройках, чтобы не читать файлы каждый раз.
 */
object CoverColorSampler {

    private const val SAMPLE_SIZE = 16

    suspend fun sample(context: Context, song: Song): Int? = withContext(Dispatchers.IO) {
        val bitmap = song.customCover?.let { decode(context, it) }
            ?: embeddedArtwork(context, song.uri)
            ?: return@withContext null
        try {
            hueOf(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    private fun embeddedArtwork(context: Context, uri: Uri): Bitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.let { bytes ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()

    private fun sampleSize(width: Int, height: Int): Int {
        var size = 1
        while (width / size > SAMPLE_SIZE * 2 && height / size > SAMPLE_SIZE * 2) size *= 2
        return size
    }

    /** Средний оттенок непрозрачных и не слишком тёмных пикселей. */
    private fun hueOf(bitmap: Bitmap): Int? {
        val stepX = (bitmap.width / 8).coerceAtLeast(1)
        val stepY = (bitmap.height / 8).coerceAtLeast(1)
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = pixel ushr 24
                if (alpha > 128) {
                    sumR += (pixel shr 16) and 0xFF
                    sumG += (pixel shr 8) and 0xFF
                    sumB += pixel and 0xFF
                    count++
                }
                x += stepX
            }
            y += stepY
        }
        if (count == 0) return null
        val r = (sumR / count).toFloat()
        val g = (sumG / count).toFloat()
        val b = (sumB / count).toFloat()
        // Почти серые обложки ставим в конец: сортировать их по оттенку бессмысленно.
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        if (abs(max - min) < 18f || max < 24f) return 360
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(r.toInt(), g.toInt(), b.toInt(), hsv)
        return hsv[0].toInt().coerceIn(0, 359)
    }
}
