package com.example.music.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.github.junrar.Junrar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile

/** Результат импорта: имя будущего альбома, треки и картинка обложки, если она была в архиве. */
data class ArchiveImport(
    val albumName: String,
    val songs: List<File>,
    val cover: File?
)

/**
 * Распаковывает zip/rar/7z в приватную папку приложения и возвращает аудиофайлы и обложку.
 * Файлы лежат во внешнем приватном каталоге приложения — плееру не нужны разрешения.
 */
object ArchiveImporter {

    private val AUDIO_EXTENSIONS =
        setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "amr", "mka")
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    suspend fun import(context: Context, uri: Uri): ArchiveImport = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(context, uri)
        val albumName = displayName.substringBeforeLast('.').trim().ifBlank { "Импорт" }
        val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "albums")
        val destination = uniqueDirectory(root, sanitize(albumName))
        val temp = File(context.cacheDir, "archive_import_${System.currentTimeMillis()}")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Не удалось открыть архив")

            if (hasMagic(temp, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O
            ) {
                error("7z поддерживается на Android 8 и новее")
            }
            extractArchive(temp, destination)
        } finally {
            temp.delete()
        }

        val files = destination.walkTopDown().filter { it.isFile }.toList()
        val songs = files.filter { it.extension.lowercase() in AUDIO_EXTENSIONS }
            .sortedBy { it.name.lowercase() }
        if (songs.isEmpty()) {
            destination.deleteRecursively()
            error("В архиве нет музыки")
        }
        val images = files.filter { it.extension.lowercase() in IMAGE_EXTENSIONS }
        val cover = images.firstOrNull { file ->
            val name = file.name.lowercase()
            "cover" in name || "front" in name || "folder" in name
        } ?: images.firstOrNull()
        // Текст, .nfo и прочий мусор из архива не нужен.
        files.filter { it !in songs && it != cover }.forEach { it.delete() }

        ArchiveImport(albumName = albumName, songs = songs, cover = cover)
    }

    /** Определяет формат по сигнатуре и распаковывает аудио/обложки в [destination]. */
    internal fun extractArchive(archive: File, destination: File) {
        when {
            hasMagic(archive, 0x50, 0x4B) -> extractZip(archive, destination)
            hasMagic(archive, 0x52, 0x61, 0x72, 0x21) -> Junrar.extract(archive, destination)
            hasMagic(archive, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) ->
                extractSevenZip(archive, destination)
            else -> error("Неизвестный формат архива")
        }
    }

    private fun extractZip(archive: File, destination: File) {
        ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/')
                if (!isWanted(name)) continue
                zip.getInputStream(entry).use { input ->
                    uniqueFile(destination, name).outputStream().use { input.copyTo(it) }
                }
            }
        }
    }

    private fun extractSevenZip(archive: File, destination: File) {
        // FileChannel вместо java.nio.file.Path: Path есть только с Android 8,
        // а канал доступен и на старых системах (проверка версии — выше, в import).
        RandomAccessFile(archive, "r").use { randomAccess ->
            SevenZFile.builder().setSeekableByteChannel(randomAccess.channel).get().use { seven ->
                while (true) {
                    val entry = seven.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.substringAfterLast('/')
                    if (!isWanted(name)) continue
                    uniqueFile(destination, name).outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = seven.read(buffer, 0, buffer.size)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
        }
    }

    private fun isWanted(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in AUDIO_EXTENSIONS || extension in IMAGE_EXTENSIONS
    }

    private fun uniqueFile(destination: File, name: String): File {
        var file = File(destination, name)
        if (!file.exists()) return file
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        var index = 2
        while (file.exists()) {
            file = File(destination, "$base ($index)$suffix")
            index++
        }
        return file
    }

    private fun uniqueDirectory(root: File, name: String): File {
        var directory = File(root, name)
        var index = 2
        while (directory.exists()) {
            directory = File(root, "$name ($index)")
            index++
        }
        if (!directory.mkdirs()) error("Не удалось создать папку для альбома")
        return directory
    }

    private fun sanitize(name: String): String =
        name.map { character ->
            if (character.isLetterOrDigit() || character == ' ' || character == '-' || character == '_') {
                character
            } else {
                '_'
            }
        }
            .joinToString("")
            .trim()
            .take(60)
            .ifBlank { "Импорт" }

    private fun hasMagic(file: File, vararg magic: Int): Boolean {
        val head = ByteArray(magic.size)
        val read = file.inputStream().use { it.read(head) }
        if (read < magic.size) return false
        return magic.indices.all { (head[it].toInt() and 0xFF) == magic[it] }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull().orEmpty()
}
