package com.example.music

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.util.Locale

/**
 * Песня из MediaStore или добавленный пользователем файл.
 * [Immutable] позволяет Compose пропускать перекомпозицию строк списка
 * и не тормозить при быстрой прокрутке.
 */
@Immutable
data class Song(
    val id: Long,
    val albumId: Long,
    val title: String,
    val artist: String,
    /** Название альбома из тегов; пусто, если неизвестно. */
    val album: String = "",
    val uri: Uri,
    val durationText: String,
    val durationMs: Long = 0L,
    val isCustom: Boolean = false,
    val customCover: Uri? = null,
    /** Путь в MediaStore (например, «Download/...»); пусто, если неизвестно. */
    val relativePath: String = "",
    /** Дата добавления в библиотеку (мс). */
    val dateAdded: Long = 0L
)

/** MediaItem с метаданными, чтобы плеер и системное уведомление показывали название и обложку. */
fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setUri(uri)
    // id нужен, чтобы отличать автопереходы от ручного выбора при записи статистики.
    .setMediaId(id.toString())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(customCover ?: uri)
            .setDurationMs(durationMs)
            .build()
    )
    .build()

fun List<Song>.toMediaItems(): List<MediaItem> = map { it.toMediaItem() }

/**
 * Совпадение с поисковым запросом: все слова запроса должны найтись
 * в названии, исполнителе или альбоме (без учёта регистра).
 */
fun Song.matchesQuery(query: String): Boolean =
    matchesSearch(query, "$title $artist $album")

/** Поиск по произвольному тексту: каждое слово запроса должно найтись. */
internal fun matchesSearch(query: String, text: String): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return false
    val haystack = text.lowercase()
    return normalized.split(' ')
        .all { word -> word.isNotBlank() && word in haystack }
}

fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}
