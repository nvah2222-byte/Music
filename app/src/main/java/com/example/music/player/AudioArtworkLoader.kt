package com.example.music.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Обложка для системного уведомления и Bluetooth: у аудиофайла достаёт встроенную
 * картинку, у обычной картинки (своя обложка) — декодирует файл.
 * Штатный загрузчик Media3 читает artworkUri как картинку и встроенную обложку из
 * аудио не видит, поэтому подменяем его.
 */
@OptIn(UnstableApi::class)
class AudioArtworkLoader(private val context: Context) : BitmapLoader {

    private val executor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean =
        mimeType.startsWith("image/") || mimeType.startsWith("audio/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        executor.submit(Callable { decode(data) ?: throw IOException("Не удалось декодировать обложку") })

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        executor.submit(Callable { load(uri) ?: throw IOException("Не удалось загрузить обложку") })

    fun shutdown() {
        executor.shutdown()
    }

    private fun load(uri: Uri): Bitmap? {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (mime?.startsWith("audio/") == true) return embeddedArt(uri)
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        return decode(bytes)
    }

    private fun embeddedArt(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.let(::decode)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /** Уменьшает картинку до 1024 px по большей стороне: уведомлению больше не нужно. */
    private fun decode(data: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_SIZE || bounds.outHeight / sample > MAX_SIZE) {
            sample *= 2
        }
        return BitmapFactory.decodeByteArray(
            data, 0, data.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    private companion object {
        const val MAX_SIZE = 1024
    }
}
