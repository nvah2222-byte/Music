package com.example.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.music.MainActivity
import com.example.music.PlaybackService
import com.example.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Виджет на главном экране: обложка, название, исполнитель и управление.
 * Состояние хранится в SharedPreferences, чтобы после перезапуска лаунчера
 * виджет показывал последний трек, а не пустую заглушку.
 */
object MusicWidget {

    private const val PREFS = "music_widget"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_PLAYING = "playing"
    private const val KEY_COVER = "cover"
    private const val COVER_FILE = "widget_cover.png"

    fun setState(context: Context, title: String, artist: String, playing: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TITLE, title)
            .putString(KEY_ARTIST, artist)
            .putBoolean(KEY_PLAYING, playing)
            .apply()
        render(context)
    }

    fun setCover(context: Context, bitmap: Bitmap?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val file = File(context.cacheDir, COVER_FILE)
        if (bitmap == null) {
            file.delete()
            prefs.edit().remove(KEY_COVER).apply()
        } else {
            val scaled = scaleDown(bitmap)
            runCatching {
                file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 90, it) }
            }
            prefs.edit().putString(KEY_COVER, file.absolutePath).apply()
        }
        render(context)
    }

    fun render(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, MusicWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, null)?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.app_name)
        val artist = prefs.getString(KEY_ARTIST, null)?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.widget_no_track)
        val playing = prefs.getBoolean(KEY_PLAYING, false)
        val cover = prefs.getString(KEY_COVER, null)
            ?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }

        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.music_widget)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setImageViewResource(
                R.id.widget_play,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            if (cover != null) {
                views.setImageViewBitmap(R.id.widget_cover, cover)
            } else {
                views.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
            }
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
            views.setOnClickPendingIntent(R.id.widget_prev, action(context, ACTION_PREV, 1))
            views.setOnClickPendingIntent(R.id.widget_play, action(context, ACTION_PLAY_PAUSE, 2))
            views.setOnClickPendingIntent(R.id.widget_next, action(context, ACTION_NEXT, 3))
            manager.updateAppWidget(id, views)
        }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= 256) return bitmap
        val scale = 256f / maxSide
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun action(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, MusicWidgetControlReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    const val ACTION_PLAY_PAUSE = "com.example.music.widget.PLAY_PAUSE"
    const val ACTION_PREV = "com.example.music.widget.PREV"
    const val ACTION_NEXT = "com.example.music.widget.NEXT"
}

class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        MusicWidget.render(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        MusicWidget.render(context)
    }
}

/**
 * Кнопки виджета. Отдельный неэкспортированный receiver: сам AppWidgetProvider
 * обязан быть exported, и иначе любое приложение могло бы управлять плеером.
 */
class MusicWidgetControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MusicWidget.ACTION_PLAY_PAUSE,
            MusicWidget.ACTION_PREV,
            MusicWidget.ACTION_NEXT -> control(context, intent.action!!)
        }
    }

    /** Короткое подключение к уже существующей сессии, без запуска второго плеера. */
    private fun control(context: Context, action: String) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            // applicationContext обязателен: ReceiverRestrictedContext запрещает bindService.
            val appContext = context.applicationContext
            val future = runCatching {
                MediaController.Builder(
                    appContext,
                    SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
                ).buildAsync()
            }.getOrNull()
            try {
                val controller = withContext(Dispatchers.IO) {
                    future?.get(3, TimeUnit.SECONDS)
                } ?: return@launch
                if (controller.mediaItemCount == 0) return@launch
                when (action) {
                    MusicWidget.ACTION_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.play()
                    MusicWidget.ACTION_PREV -> controller.seekToPrevious()
                    MusicWidget.ACTION_NEXT -> controller.seekToNext()
                }
            } catch (_: Exception) {
                // Плеер ещё не создан или сервис недоступен — кнопка просто ничего не делает.
            } finally {
                future?.let { MediaController.releaseFuture(it) }
                pending.finish()
            }
        }
    }
}
