package com.example.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TagTextTest {

    @Test
    fun normalRussianTextIsUntouched() {
        assertEquals("Пасха", TagText.fix("Пасха"))
        assertEquals("Егор Летов", TagText.fix("Егор Летов"))
        assertEquals("Song 2024", TagText.fix("Song 2024"))
    }

    @Test
    fun cyrillicBytesReadAsGbkAreRestored() {
        val original = "Музыка"
        val broken = String(original.toByteArray(charset("windows-1251")), charset("GBK"))
        assertEquals(original, TagText.fix(broken))
    }

    @Test
    fun fileNameSplitsIntoArtistAndTitle() {
        assertEquals(
            "Неважно (hitmo.net)" to "Буерак",
            TagText.splitFileName("Буерак - Неважно (hitmo.net).mp3")
        )
        assertEquals("track" to null, TagText.splitFileName("track.mp3"))
    }

    @Test
    fun siteLinksAreRemovedFromTitles() {
        assertEquals("Неважно", TagText.stripLinks("Неважно (hitmo.net)"))
        assertEquals("Песня", TagText.stripLinks("Песня [zaycev.net]"))
        assertEquals("Песня", TagText.stripLinks("Песня - hitmo.net"))
        assertEquals("Song (feat. X)", TagText.stripLinks("Song (feat. X)"))
        assertEquals("Song (Remix)", TagText.stripLinks("Song (Remix)"))
        assertEquals("Mr. Brightside", TagText.stripLinks("Mr. Brightside"))
        assertEquals("Сектор Газа - 30 лет", TagText.stripLinks("Сектор Газа - 30 лет"))
    }

    @Test
    fun emptyAndAsciiAreSafe() {
        assertEquals("", TagText.fix(""))
        assertEquals("abc", TagText.fix("abc"))
    }

    private fun charset(name: String) = java.nio.charset.Charset.forName(name)
}
