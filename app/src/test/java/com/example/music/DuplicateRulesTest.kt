package com.example.music

import com.example.music.data.duplicateGroups
import com.example.music.data.songKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateRulesTest {

    private fun keys(vararg songs: Triple<String, String, Long>) =
        songs.map { (title, artist, seconds) -> songKey(title, artist, seconds * 1000) }

    @Test
    fun sameTitleAndArtistIsDuplicate() {
        val groups = duplicateGroups(
            keys(
                Triple("Туман", "Кино", 180L),
                Triple("туман !!!", "КИНО", 184L)
            )
        )
        assertEquals(listOf(listOf(0, 1)), groups)
    }

    @Test
    fun sameTitleDifferentArtistsIsNotDuplicate() {
        val groups = duplicateGroups(
            keys(
                Triple("Intro", "Artist A", 180L),
                Triple("Intro", "Artist B", 181L)
            )
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun durationFarApartIsNotDuplicate() {
        val groups = duplicateGroups(
            keys(
                Triple("Intro", "Artist", 180L),
                Triple("Intro", "Artist", 186L)
            )
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun thirdCopyOutsideWindowStaysSeparate() {
        val groups = duplicateGroups(
            keys(
                Triple("Intro", "Artist", 180L),
                Triple("Intro", "Artist", 184L),
                Triple("Intro", "Artist", 188L)
            )
        )
        // 188 - 180 = 8 с, это уже не дубль первой копии.
        assertEquals(listOf(listOf(0, 1)), groups)
    }
}
