package com.example.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongQueryTest {

    // В haystack трека входят название, исполнитель и альбом.
    private val haystack = "Ночная прогулка Кино Легенды"

    @Test
    fun queryFindsAlbumName() {
        assertTrue(matchesSearch("легенды", haystack))
        assertTrue(matchesSearch("кино легенды", haystack))
    }

    @Test
    fun queryStillFindsTitleAndArtist() {
        assertTrue(matchesSearch("прогулка", haystack))
        assertTrue(matchesSearch("кино", haystack))
    }

    @Test
    fun unrelatedQueryDoesNotMatch() {
        assertFalse(matchesSearch("джаз", haystack))
    }
}
