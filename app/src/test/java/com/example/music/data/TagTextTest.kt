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
    fun emptyAndAsciiAreSafe() {
        assertEquals("", TagText.fix(""))
        assertEquals("abc", TagText.fix("abc"))
    }

    private fun charset(name: String) = java.nio.charset.Charset.forName(name)
}
