package com.example.music.data

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveExtractTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun zipExtractsMusicAndCoverButNotJunk() {
        val zip = temp.newFile("album.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("folder/01.mp3"))
            out.write(byteArrayOf(1, 2, 3))
            out.closeEntry()
            out.putNextEntry(ZipEntry("folder/cover.jpg"))
            out.write(byteArrayOf(4, 5, 6))
            out.closeEntry()
            out.putNextEntry(ZipEntry("readme.txt"))
            out.write("текст".toByteArray())
            out.closeEntry()
        }
        val destination = temp.newFolder("zip-dest")

        ArchiveImporter.extractArchive(zip, destination)

        assertTrue(File(destination, "01.mp3").exists())
        assertTrue(File(destination, "cover.jpg").exists())
        assertFalse(File(destination, "readme.txt").exists())
    }

    @Test
    fun sameFileNameInDifferentFoldersDoesNotOverwrite() {
        val zip = temp.newFile("dup.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            for (folder in listOf("a", "b")) {
                out.putNextEntry(ZipEntry("$folder/song.mp3"))
                out.write(byteArrayOf(1))
                out.closeEntry()
            }
        }
        val destination = temp.newFolder("dup-dest")

        ArchiveImporter.extractArchive(zip, destination)

        assertTrue(File(destination, "song.mp3").exists())
        assertTrue(File(destination, "song (2).mp3").exists())
    }

    @Test
    fun sevenZipIsExtracted() {
        val archive = temp.newFile("album.7z")
        SevenZOutputFile(archive).use { out ->
            val entry = SevenZArchiveEntry().apply { name = "track.mp3" }
            out.putArchiveEntry(entry)
            out.write(byteArrayOf(7, 8, 9))
            out.closeArchiveEntry()
        }
        val destination = temp.newFolder("7z-dest")

        ArchiveImporter.extractArchive(archive, destination)

        assertTrue(File(destination, "track.mp3").exists())
    }
}
