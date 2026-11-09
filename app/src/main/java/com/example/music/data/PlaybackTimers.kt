package com.example.music.data

import android.content.Context

/**
 * Таймеры сна и «стоп через N треков». Хранятся в отдельном файле, потому что
 * их должен выполнять PlaybackService даже после того, как активити уничтожена.
 */
object PlaybackTimers {
    private const val PREFS_NAME = "playback_timers"
    private const val KEY_SLEEP_END_AT = "sleep_end_at"
    private const val KEY_SONGS_UNTIL_STOP = "songs_until_stop"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Время окончания таймера сна по elapsedRealtime; 0 — выключен. */
    fun sleepEndAt(context: Context): Long = prefs(context).getLong(KEY_SLEEP_END_AT, 0L)

    fun setSleepEndAt(context: Context, value: Long) {
        prefs(context).edit().putLong(KEY_SLEEP_END_AT, value).apply()
    }

    /** Сколько треков ещё проиграть до паузы; 0 — выключено. */
    fun songsUntilStop(context: Context): Int = prefs(context).getInt(KEY_SONGS_UNTIL_STOP, 0)

    fun setSongsUntilStop(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_SONGS_UNTIL_STOP, value).apply()
    }
}
