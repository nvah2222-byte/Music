package com.example.music.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Size
import coil.size.isOriginal
import coil.size.pxOrElse

/**
 * Достаёт обложку: у аудиофайлов — встроенную через MediaMetadataRetriever,
 * у обычных картинок (пользовательские обложки) — декодирует сам.
 * Вся тяжёлая работа идёт на фоновом потоке Coil; create() не обращается
 * к ContentResolver, чтобы не тормозить прокрутку на главном потоке.
 */
class AudioCoverFetcher(
    private val context: Context,
    private val uri: Uri,
    private val targetSize: Size?
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        // Аудио читаем только как контейнер: иначе мы бы декодировали mp3 как картинку
        // и рисковали памятью на больших файлах.
        val bitmap = if (mime?.startsWith("audio/") == true) {
            embeddedArt()
        } else {
            decodeImage()
        } ?: return null
        return DrawableResult(BitmapDrawable(context.resources, bitmap), false, DataSource.DISK)
    }

    /** Встроенная обложка аудиофайла. */
    private fun embeddedArt(): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val art = retriever.embeddedPicture ?: return null
            decodeSampled(art, targetSize)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /** Декодирует файл как обычную картинку с учётом EXIF-поворота. */
    private fun decodeImage(): Bitmap? {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        val bitmap = decodeSampled(bytes, targetSize) ?: return null
        return rotateByExif(bitmap, uri)
    }

    private fun rotateByExif(bitmap: Bitmap, uri: Uri): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(degrees) }, true
        )
    }

    /**
     * Декодирует картинку, уменьшая её до размера, запрошенного Coil.
     * Без этого каждая строка списка грузила бы обложку в полном размере
     * и прокрутка заметно лагала.
     */
    private fun decodeSampled(art: ByteArray, target: Size?): Bitmap? {
        var sample = 1
        // Для полноэкранной обложки просят Size.ORIGINAL — но декодируем не больше
        // MAX_ORIGINAL_SIZE по большей стороне, иначе встроенная картинка 4000 px съест память.
        val targetWidth: Int
        val targetHeight: Int
        if (target == null || target.isOriginal) {
            targetWidth = MAX_ORIGINAL_SIZE
            targetHeight = MAX_ORIGINAL_SIZE
        } else {
            targetWidth = target.width.pxOrElse { 0 }
            targetHeight = target.height.pxOrElse { 0 }
        }
        if (targetWidth > 0 && targetHeight > 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(art, 0, art.size, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                val scale = maxOf(bounds.outWidth / targetWidth, bounds.outHeight / targetHeight)
                sample = Integer.highestOneBit(scale.coerceAtLeast(1))
            }
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(art, 0, art.size, options)
    }

    private companion object {
        const val MAX_ORIGINAL_SIZE = 2048
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        // Никаких обращений к ContentResolver здесь: create() вызывается на главном потоке,
        // а getType() — это медленный binder-вызов, который лагал список при первом скролле.
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher =
            AudioCoverFetcher(context, data, options.size)
    }
}
