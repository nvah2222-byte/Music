package com.example.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LoudnessNormalizerTest {

    @Test
    fun quietTrackIsBoosted() {
        // -24 дБ -> -18 дБ: +6 дБ, примерно 1.995x
        assertEquals(1.995f, LoudnessNormalizer.gainFor(-24f, -18f), 0.01f)
    }

    @Test
    fun loudTrackIsReduced() {
        // -12 дБ -> -18 дБ: -6 дБ, примерно 0.5x
        assertEquals(0.5f, LoudnessNormalizer.gainFor(-12f, -18f), 0.01f)
    }

    @Test
    fun boostIsLimited() {
        assertEquals(4f, LoudnessNormalizer.gainFor(-60f, -6f), 0.001f)
    }

    @Test
    fun cutIsLimited() {
        assertEquals(0.1f, LoudnessNormalizer.gainFor(0f, -30f), 0.001f)
    }
}
