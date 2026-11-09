package com.example.music.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.music.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Форматы, в которые умеем упаковывать альбом. RAR создать нельзя — он только читается. */
enum class AlbumArchiveFormat(val extension: String) {
    Zip("zip"),
    SevenZip("7z")
}

/**
 * Собирает альбом в архив в кэше приложения (для отправки через FileProvider).
 * Треки читаются через ContentResolver, поэтому подходят и MediaStore, и SAF, и file://.
 */
object AlbumArchiver {

    suspend fun create(
        context: Context,
        albumName: String,
        songs: List<Song>,
        coverUri: Uri?,
        format: AlbumArchiveFormat
    ): File = withContext(Dispatchers.IO) {
        val entries = buildEntries(context, songs, coverUri)
        if (entries.isEmpty()) error("В альбоме нет треков")
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(directory, "${sanitize(albumName).ifBlank { "album" }}.${format.extension}")
        if (file.exists()) file.delete()

        val written = when (format) {
            AlbumArchiveFormat.Zip -> writeZip(context, file, entries)
            AlbumArchiveFormat.SevenZip -> writeSevenZip(context, file, entries)
        }
        if (written == 0) {
            file.delete()
            error("Не удалось прочитать треки альбома")
        }
        file
    }

    private data class Entry(val uri: Uri, val name: String)

    private fun buildEntries(context: Context, songs: List<Song>, coverUri: Uri?): List<Entry> {
        val used = mutableSetOf<String>()
        val entries = mutableListOf<Entry>()
        songs.forEachIndexed { index, song ->
            val displayName = displayName(context, song.uri)
            val base = displayName?.substringBeforeLast('.')?.ifBlank { null } ?: song.title
            val extension = extensionOf(context, song.uri, displayName)
            val name = uniqueName(sanitize("${index + 1} - $base").ifBlank { "track" } + ".$extension", used)
            entries += Entry(song.uri, name)
        }
        if (coverUri != null) {
            val extension = displayName(context, coverUri)?.substringAfterLast('.', "")?.lowercase()
                ?.takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
            entries += Entry(coverUri, uniqueName("cover.$extension", used))
        }
        return entries
    }

    private fun writeZip(context: Context, file: File, entries: List<Entry>): Int {
        var written = 0
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            for (entry in entries) {
                val input = open(context, entry.uri) ?: continue
                input.use {
                    zip.putNextEntry(ZipEntry(entry.name))
                    it.copyTo(zip)
                    zip.closeEntry()
                    written++
                }
            }
        }
        return written
    }

    private fun writeSevenZip(context: Context, file: File, entries: List<Entry>): Int {
        var written = 0
        SevenZOutputFile(file).use { seven ->
            for (entry in entries) {
                val input = open(context, entry.uri) ?: continue
                input.use {
                    seven.putArchiveEntry(SevenZArchiveEntry().apply { name = entry.name })
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = it.read(buffer)
                        if (read < 0) break
                        seven.write(buffer, 0, read)
                    }
                    seven.closeArchiveEntry()
                    written++
                }
            }
        }
        return written
    }

    private fun open(context: Context, uri: Uri): InputStream? =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    private fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path?.let { File(it).name }
        return runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }

    private fun extensionOf(context: Context, uri: Uri, displayName: String?): String {
        val fromName = displayName?.substringAfterLast('.', "")?.lowercase()
        if (!fromName.isNullOrBlank() && fromName.length <= 5 && fromName.all { it.isLetterOrDigit() }) {
            return fromName
        }
        return when (runCatching { context.contentResolver.getType(uri) }.getOrNull()) {
            "audio/mpeg" -> "mp3"
            "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
            "audio/flac" -> "flac"
            "audio/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/wav", "audio/x-wav" -> "wav"
            else -> "mp3"
        }
    }

    private fun uniqueName(name: String, used: MutableSet<String>): String {
        if (used.add(name)) return name
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        var index = 2
        while (!used.add("$base ($index)$suffix")) index++
        return "$base ($index)$suffix"
    }

    private fun sanitize(name: String): String =
        name.map { character ->
            if (character.isLetterOrDigit() || character in " -_.()[]&,+!") character else '_'
        }
            .joinToString("")
            .trim()
            .take(80)
}
