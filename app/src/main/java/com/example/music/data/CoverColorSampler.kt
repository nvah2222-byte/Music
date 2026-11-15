package com.example.music.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.music.Song

/**
 * Оттенок обложки для сортировки «по обложке».
 * Берём картинку в миниатюре и определяем доминирующий цветовой сектор (шаг 30°).
 * Результат кэшируется в настройках, чтобы не читать файлы каждый раз.
 */
data class CoverSample(val hue: Int, val hash: Long)

object CoverColorSampler {

    private const val SAMPLE_SIZE = 16
    private const val BUCKETS = 12

    suspend fun sample(context: Context, song: Song): CoverSample? = withContext(Dispatchers.IO) {
        val bitmap = song.customCover?.let { decode(context, it) }
            ?: embeddedArtwork(context, song.uri)
            ?: return@withContext null
        try {
            CoverSample(hueOf(bitmap), hashOf(bitmap))
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

    /**
     * Доминирующий оттенок, 0..359. Средний цвет фотографии всегда мутно-серый,
     * поэтому берём самый частый цветовой сектор среди насыщенных пикселей.
     * -1 — серая/чёрная обложка: такие ставим отдельной группой в конце.
     */
    private fun hueOf(bitmap: Bitmap): Int {
        val buckets = IntArray(BUCKETS)
        var sampled = 0
        var neutral = 0
        val stepX = (bitmap.width / 16).coerceAtLeast(1)
        val stepY = (bitmap.height / 16).coerceAtLeast(1)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if ((pixel ushr 24) > 128) {
                    val hsv = FloatArray(3)
                    android.graphics.Color.RGBToHSV(
                        (pixel shr 16) and 0xFF,
                        (pixel shr 8) and 0xFF,
                        pixel and 0xFF,
                        hsv
                    )
                    sampled++
                    if (hsv[1] < 0.14f || hsv[2] < 0.10f) {
                        neutral++
                    } else {
                        buckets[(hsv[0].toInt() / (360 / BUCKETS)).coerceIn(0, BUCKETS - 1)]++
                    }
                }
                x += stepX
            }
            y += stepY
        }
        if (sampled == 0) return -1
        val colored = buckets.sum()
        // Даже тёмные обложки обычно имеют цветной акцент: хватает 15% цветных пикселей.
        if (colored < sampled * 0.15f) return -1
        val best = buckets.indices.maxByOrNull { buckets[it] } ?: return -1
        if (buckets[best] == 0) return -1
        return best * (360 / BUCKETS) + (360 / BUCKETS) / 2
    }
    /**
     * Перцептивный хэш 8x8: одинаковые обложки дают одинаковый хэш,
     * даже если у треков разные альбомы. Так группируем «та же картинка».
     */
    private fun hashOf(bitmap: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        val gray = IntArray(64)
        var sum = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val pixel = small.getPixel(x, y)
                val value = (((pixel shr 16) and 0xFF) * 299 +
                    ((pixel shr 8) and 0xFF) * 587 +
                    (pixel and 0xFF) * 114) / 1000
                gray[y * 8 + x] = value
                sum += value
            }
        }
        val average = sum / 64
        var hash = 0L
        for (index in 0 until 64) {
            if (gray[index] >= average) hash = hash or (1L shl index)
        }
        if (small !== bitmap) small.recycle()
        return hash
    }
}