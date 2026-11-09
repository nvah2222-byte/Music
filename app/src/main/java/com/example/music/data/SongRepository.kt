package com.example.music.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.music.R
import com.example.music.Song
import com.example.music.formatDuration
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/** Альбом, созданный пользователем вручную. */
data class CustomAlbum(
    val name: String,
    val songIds: List<Long>,
    val coverUri: String? = null
)

/**
 * Разбирает сохранённый альбом «имя|обложка|id1,id2» (старый формат — «имя|id1,id2»).
 * Имя читаем справа, чтобы символ «|» внутри названия не ломал альбом.
 */
internal fun parseCustomAlbum(entry: String): CustomAlbum? {
    val parts = entry.split('|')
    if (parts.size < 2) return null
    val idsText = parts.last()
    val cover = if (parts.size >= 3) parts[parts.size - 2].ifEmpty { null } else null
    val name = parts.dropLast(if (parts.size >= 3) 2 else 1).joinToString("|")
    val ids = idsText.split(',').mapNotNull { it.toLongOrNull() }
    return if (name.isBlank() || ids.isEmpty()) null else CustomAlbum(name, ids, cover)
}

/**
 * Загружает треки из MediaStore и хранит пользовательские данные
 * (добавленные файлы, обложки, порядок списка) в SharedPreferences.
 */
class SongRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val addedIdGenerator = AtomicLong(System.currentTimeMillis())

    /** Все треки: устройство + добавленные пользователем, с сохранённым порядком и обложками. */
    fun loadAll(): List<Song> {
        val covers = loadCovers()
        val songs = (queryDeviceSongs() + loadAddedSongs()).map { song ->
            covers[song.id]?.let { song.copy(customCover = it) } ?: song
        }
        val order = prefs.getString(KEY_ORDER, null)
            ?.split(',')
            ?.mapNotNull(String::toLongOrNull)
        if (order.isNullOrEmpty()) return songs
        val rank = order.withIndex().associate { it.value to it.index }
        return songs.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
    }

    /** Сохраняет порядок, добавленные файлы и пользовательские обложки. */
    fun save(songs: List<Song>) {
        prefs.edit()
            .putString(KEY_ORDER, songs.joinToString(",") { it.id.toString() })
            .putStringSet(KEY_ADDED, songs.filter { it.isCustom }.map { "${it.id}|${it.uri}" }.toSet())
            .putStringSet(KEY_COVERS, songs.mapNotNull { song -> song.customCover?.let { "${song.id}|$it" } }.toSet())
            .apply()
    }

    /** Уникальный id для добавленного пользователем файла. */
    fun newAddedId(): Long = addedIdGenerator.getAndIncrement()

    /** Позиция, с которой продолжить длинный трек. */
    fun loadPosition(id: Long): Long = prefs.getLong("$KEY_POSITION_PREFIX$id", 0L)

    fun savePosition(id: Long, positionMs: Long) {
        prefs.edit().putLong("$KEY_POSITION_PREFIX$id", positionMs).apply()
    }

    fun clearPosition(id: Long) {
        prefs.edit().remove("$KEY_POSITION_PREFIX$id").apply()
    }

    /** Снимок пользовательских данных для резервной копии. */
    fun exportPrefs(): JSONObject = dumpPrefs(prefs)

    fun importPrefs(json: JSONObject) {
        restorePrefs(prefs, json)
    }

    /** Читает метаданные аудиофайла. Возвращает null, если файл недоступен. */
    fun readMetadata(uri: Uri, id: Long): Song? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: appContext.getString(R.string.unknown_title)
            val rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: appContext.getString(R.string.unknown_artist)
            val (title, artist) = resolveTitleAndArtist(rawTitle, rawArtist)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            Song(
                id = id,
                albumId = 0L,
                title = title,
                artist = artist,
                album = album,
                uri = uri,
                durationText = formatDuration(duration),
                durationMs = duration,
                isCustom = true,
                dateAdded = id
            )
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Определяем настоящего исполнителя:
     * - вырезаем ссылки на сайты из имени («Артист [zaycev.net]» → «Артист»);
     * - если тег артиста пустой или в нём сайт/перезаливщик — берём из названия
     *   («Исполнитель - Название»);
     * - если название тоже начинается с «Исполнитель - », убираем дубль из названия.
     */
    private fun resolveTitleAndArtist(rawTitle: String, rawArtist: String): Pair<String, String> {
        val unknown = appContext.getString(R.string.unknown_artist)
        val artist = cleanArtistTag(rawArtist)
        val artistMissing = artist.isBlank() ||
            artist.equals("<unknown>", ignoreCase = true) ||
            artist.equals("unknown", ignoreCase = true) ||
            artist.equals(unknown, ignoreCase = true)
        val junkArtist = !artistMissing && looksLikeWebsite(artist)

        if (!artistMissing && !junkArtist) {
            // Артист в теге нормальный; если название дублирует его, чистим название.
            val parsed = splitArtistFromTitle(rawTitle)
            if (parsed != null && normalizeTitle(parsed.first) == normalizeTitle(artist)) {
                return parsed
            }
            return rawTitle to artist
        }

        val parsed = splitArtistFromTitle(rawTitle)
        if (parsed != null) return parsed

        // Артист не нашёлся, а в теге сайт или <unknown>: помечаем как неизвестного,
        // чтобы он не попадал в альбомы и итоги под чужим именем.
        return rawTitle to unknown
    }

    /** Убирает из имени артиста ссылки на сайты: «Артист [zaycev.net]» → «Артист». */
    private fun cleanArtistTag(artist: String): String {
        val withoutLinks = artist
            .replace(BRACKET_LINK_REGEX, " ")
            .replace(PAREN_LINK_REGEX, " ")
        val tokens = withoutLinks.split(' ')
            .map { it.trim() }
            .filter { token ->
                token.isNotEmpty() &&
                    !token.startsWith("http", ignoreCase = true) &&
                    !token.startsWith("@") &&
                    !token.contains("www.") &&
                    !DOMAIN_REGEX.containsMatchIn(token) &&
                    token.any { it.isLetterOrDigit() }
            }
        return tokens.joinToString(" ").trim()
    }

    /** «zaycev.net», «vk.com/music», «@channel» — похоже на сайт/перезаливщика, а не на артиста. */
    private fun looksLikeWebsite(text: String): Boolean {
        val value = text.trim()
        if (value.isEmpty()) return false
        if (value.startsWith("http", ignoreCase = true) ||
            value.contains("www.") ||
            value.contains('@')
        ) {
            return true
        }
        return DOMAIN_REGEX.containsMatchIn(value)
    }

    private fun splitArtistFromTitle(title: String): Pair<String, String>? {
        for (separator in TITLE_ARTIST_SEPARATORS) {
            val index = title.indexOf(separator)
            if (index <= 0) continue
            val artist = title.substring(0, index).trim()
            val name = title.substring(index + separator.length).trim()
            if (artist.isNotEmpty() && name.isNotEmpty()) return artist to name
        }
        return null
    }

    /** Избранные треки (id). */
    fun loadFavorites(): Set<Long> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()

    fun saveFavorites(favorites: Set<Long>) {
        prefs.edit()
            .putStringSet(KEY_FAVORITES, favorites.map { it.toString() }.toSet())
            .apply()
    }

    /** Сколько раз запускался каждый трек: id -> число. */
    fun loadPlayCounts(): Map<Long, Int> =
        prefs.getStringSet(KEY_PLAY_COUNTS, emptySet())
            ?.mapNotNull { entry ->
                val separator = entry.indexOf('|')
                if (separator <= 0) return@mapNotNull null
                val id = entry.substring(0, separator).toLongOrNull() ?: return@mapNotNull null
                val count = entry.substring(separator + 1).toIntOrNull() ?: return@mapNotNull null
                id to count
            }
            ?.toMap()
            ?: emptyMap()

    fun incrementPlayCount(id: Long) {
        val counts = loadPlayCounts().toMutableMap()
        counts[id] = (counts[id] ?: 0) + 1
        prefs.edit()
            .putStringSet(KEY_PLAY_COUNTS, counts.map { (songId, count) -> "$songId|$count" }.toSet())
            .apply()
    }

    /** События прослушиваний с временем: пары (id песни, время в мс). Новые в конце. */
    fun loadPlayEvents(): List<Pair<Long, Long>> =
        prefs.getString(KEY_PLAY_EVENTS, null)
            ?.split(',')
            ?.mapNotNull { entry ->
                val separator = entry.indexOf('|')
                if (separator <= 0) return@mapNotNull null
                val id = entry.substring(0, separator).toLongOrNull() ?: return@mapNotNull null
                val time = entry.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
                id to time
            }
            ?: emptyList()

    fun recordPlayEvent(id: Long) {
        val events = loadPlayEvents().toMutableList()
        events.add(id to System.currentTimeMillis())
        // Храним только последние события, чтобы строка не разрасталась.
        val trimmed = if (events.size > MAX_PLAY_EVENTS) {
            events.subList(events.size - MAX_PLAY_EVENTS, events.size)
        } else {
            events
        }
        prefs.edit()
            .putString(KEY_PLAY_EVENTS, trimmed.joinToString(",") { "${it.first}|${it.second}" })
            .apply()
    }

    /** История прослушиваний: id от новых к старым. */
    fun loadHistory(): List<Long> =
        prefs.getString(KEY_HISTORY, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()

    fun addToHistory(id: Long) {
        val history = loadHistory().toMutableList()
        history.remove(id)
        history.add(0, id)
        prefs.edit().putString(KEY_HISTORY, history.take(50).joinToString(",")).apply()
    }

    /** Альбомы, созданные пользователем вручную. */
    fun loadCustomAlbums(): List<CustomAlbum> =
        prefs.getStringSet(KEY_CUSTOM_ALBUMS, emptySet())
            .orEmpty()
            .mapNotNull(::parseCustomAlbum)
            .sortedBy { it.name.lowercase() }

    fun saveCustomAlbums(albums: List<CustomAlbum>) {
        prefs.edit()
            .putStringSet(
                KEY_CUSTOM_ALBUMS,
                albums.map { "${it.name}|${it.coverUri.orEmpty()}|${it.songIds.joinToString(",")}" }.toSet()
            )
            .apply()
    }

    /**
     * Ищет дубли — одинаковое название, исполнитель и длительность (±5 с).
     * Возвращает лишние копии: одну копию с обложкой оставляем, остальные удаляем.
     * Если обложки нет ни у одной копии, песни не трогаем.
     */
    fun findCoveredDuplicates(songs: List<Song>): List<Song> {
        val keys = songs.map { songKey(it.title, it.artist, it.durationMs) }
        return duplicateGroups(keys).flatMap { indices ->
            val group = indices.map { songs[it] }
            val covered = group.filter { hasRealCover(it) }
            if (covered.isEmpty()) emptyList() else group - covered.first()
        }
    }

    /** true, если у песни есть настоящая картинка (не авто-заглушка). */
    fun hasRealCover(song: Song): Boolean {        if (song.customCover != null) return true
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, song.uri)
            retriever.embeddedPicture != null
        } catch (e: Exception) {
            false
        } finally {
            retriever.release()
        }
    }

    /**
     * Кэш «есть ли встроенная обложка»: чтение каждого файла дорогое,
     * поэтому результат сортировки «По обложке» переживает перезапуск.
     */
    fun loadCoverPresence(): Map<Long, Boolean> =
        prefs.getStringSet(KEY_COVER_PRESENCE, emptySet()).orEmpty().mapNotNull { entry ->
            val separator = entry.indexOf('|')
            if (separator <= 0) return@mapNotNull null
            val id = entry.substring(0, separator).toLongOrNull() ?: return@mapNotNull null
            id to (entry.substring(separator + 1) == "1")
        }.toMap()

    fun saveCoverPresence(presence: Map<Long, Boolean>) {
        prefs.edit()
            .putStringSet(
                KEY_COVER_PRESENCE,
                presence.map { (id, hasCover) -> "$id|${if (hasCover) 1 else 0}" }.toSet()
            )
            .apply()
    }

    /**
     * Кандидаты на удаление: лишние копии песен, лежащие в системных «Загрузках»,
     * если у песни есть дубль (то же название, исполнитель и длительность).
     * По одной копии каждой песни всегда оставляем.
     */
    fun findDownloadsDuplicates(songs: List<Song>): List<Song> {
        val keys = songs.map { songKey(it.title, it.artist, it.durationMs) }
        return duplicateGroups(keys).flatMap { indices ->
            val group = indices.map { songs[it] }
            val downloads = group.filter { isInDownloads(it) }
            if (downloads.isEmpty()) return@flatMap emptyList()
            // Есть копия вне «Загрузок» — удаляем все из «Загрузок»;
            // иначе все копии в «Загрузках» — оставляем одну.
            if (group.any { !isInDownloads(it) }) downloads else downloads.drop(1)
        }
    }

    /** Лежит ли файл в системной папке «Загрузки». */
    private fun isInDownloads(song: Song): Boolean {
        if (song.relativePath.isNotEmpty()) {
            return song.relativePath.startsWith("Download/", ignoreCase = true)
        }
        // Для добавленных файлов пробуем узнать путь через MediaStore.
        val column = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            MediaStore.MediaColumns.DATA
        }
        return runCatching {
            appContext.contentResolver.query(song.uri, arrayOf(column), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use false
                    val path = cursor.getString(0) ?: return@use false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        path.startsWith("Download/", ignoreCase = true)
                    } else {
                        path.contains("/Download/", ignoreCase = true)
                    }
                }
                ?: false
        }.getOrDefault(false)
    }

    private fun queryDeviceSongs(): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        // RELATIVE_PATH появился только в Android 10: на API 24–28 запрос с ним падает.
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DATE_ADDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
        }.toTypedArray()
        val songs = mutableListOf<Song>()
        appContext.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val albumIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumNameIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val dateIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val durationMs = cursor.getLong(durationIdx)
                val (title, artist) = resolveTitleAndArtist(
                    cursor.getString(titleIdx) ?: appContext.getString(R.string.unknown_title),
                    cursor.getString(artistIdx) ?: appContext.getString(R.string.unknown_artist)
                )
                songs += Song(
                    id = id,
                    albumId = cursor.getLong(albumIdx),
                    title = title,
                    artist = artist,
                    album = cursor.getString(albumNameIdx) ?: "",
                    uri = ContentUris.withAppendedId(collection, id),
                    durationText = formatDuration(durationMs),
                    durationMs = durationMs,
                    relativePath = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else "",
                    dateAdded = if (dateIdx >= 0) cursor.getLong(dateIdx) * 1000L else 0L
                )
            }
        }
        return songs
    }

    private fun loadAddedSongs(): List<Song> {
        val added = prefs.getStringSet(KEY_ADDED, emptySet()) ?: return emptyList()
        return added.mapNotNull { entry ->
            val separator = entry.indexOf('|')
            if (separator <= 0) return@mapNotNull null
            val id = entry.substring(0, separator).toLongOrNull() ?: return@mapNotNull null
            readMetadata(Uri.parse(entry.substring(separator + 1)), id)
        }
    }

    private fun loadCovers(): Map<Long, Uri> {
        val covers = prefs.getStringSet(KEY_COVERS, emptySet()) ?: return emptyMap()
        return covers.mapNotNull { entry ->
            val separator = entry.indexOf('|')
            if (separator <= 0) return@mapNotNull null
            val id = entry.substring(0, separator).toLongOrNull() ?: return@mapNotNull null
            id to Uri.parse(entry.substring(separator + 1))
        }.toMap()
    }

    private companion object {
        const val PREFS_NAME = "music_library"
        const val KEY_ORDER = "order"
        const val KEY_ADDED = "added_songs"
        const val KEY_COVERS = "custom_covers"
        const val KEY_FAVORITES = "favorites"
        const val KEY_PLAY_COUNTS = "play_counts"
        const val KEY_HISTORY = "history"
        const val KEY_PLAY_EVENTS = "play_events"
        const val KEY_CUSTOM_ALBUMS = "custom_albums"
        const val KEY_COVER_PRESENCE = "cover_presence"
        const val KEY_POSITION_PREFIX = "position_"
        const val MAX_PLAY_EVENTS = 5000

        /** Варианты разделителя «исполнитель — название» в заголовке. */
        val TITLE_ARTIST_SEPARATORS = listOf(" - ", " — ", " – ", " − ")

        /** «site.ru», «vk.com/…» — похоже на домен сайта. */
        val DOMAIN_REGEX = Regex("""\.[a-zA-Z]{2,6}($|[/\s])""")

        /** Ссылка на сайт в квадратных скобках: [zaycev.net]. */
        val BRACKET_LINK_REGEX =
            Regex("""\[[^\]]*(\.[a-zA-Z]{2,6}|www\.|@|http)[^\]]*]""", RegexOption.IGNORE_CASE)

        /** Ссылка на сайт в круглых скобках: (vk.com/music). */
        val PAREN_LINK_REGEX =
            Regex("""\([^)]*(\.[a-zA-Z]{2,6}|www\.|@|http)[^)]*\)""", RegexOption.IGNORE_CASE)
    }
}
