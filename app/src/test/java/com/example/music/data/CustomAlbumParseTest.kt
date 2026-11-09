package com.example.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomAlbumParseTest {

    @Test
    fun coverAndNameAreRestored() {
        val album = parseCustomAlbum("Дорога|content://cover|1,2,3")
        assertEquals(CustomAlbum("Дорога", listOf(1L, 2L, 3L), "content://cover"), album)
    }

    @Test
    fun oldFormatWithoutCoverStillWorks() {
        assertEquals(CustomAlbum("Rock", listOf(7L)), parseCustomAlbum("Rock|7"))
    }

    @Test
    fun pipeInNameDoesNotBreakAlbum() {
        assertEquals(
            CustomAlbum("Rock|Pop", listOf(5L)),
            parseCustomAlbum("Rock|Pop||5")
        )
    }

    @Test
    fun garbageIsDropped() {
        assertNull(parseCustomAlbum(""))
        assertNull(parseCustomAlbum("без разделителей"))
        assertNull(parseCustomAlbum("Имя||"))
        assertNull(parseCustomAlbum("|1,2"))
    }
}
