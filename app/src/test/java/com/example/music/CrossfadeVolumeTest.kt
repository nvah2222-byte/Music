package com.example.music

import org.junit.Assert.assertEquals
import org.junit.Test

class CrossfadeVolumeTest {

    private val fade = 1000

    @Test
    fun disabledMeansFullVolume() {
        assertEquals(1f, crossfadeVolume(now = 5_000, fadeInStartAt = 0, fadeMs = 0, durationMs = 200_000, positionMs = 100_000), 0.001f)
    }

    @Test
    fun fadesInAfterTransition() {
        // Трек только начался: громкость 0, через полсекунды — 0.5, после окна — 1.
        assertEquals(0f, crossfadeVolume(10_000, 10_000, fade, 200_000, 0), 0.001f)
        assertEquals(0.5f, crossfadeVolume(10_500, 10_000, fade, 200_000, 500), 0.001f)
        assertEquals(1f, crossfadeVolume(11_000, 10_000, fade, 200_000, 1_000), 0.001f)
    }

    @Test
    fun fadesOutAtTrackEnd() {
        assertEquals(1f, crossfadeVolume(50_000, 10_000, fade, 200_000, 198_000), 0.001f)
        assertEquals(0.5f, crossfadeVolume(50_000, 10_000, fade, 200_000, 199_500), 0.001f)
        assertEquals(0f, crossfadeVolume(50_000, 10_000, fade, 200_000, 200_000), 0.001f)
    }

    @Test
    fun unknownDurationDoesNotBreakFadeIn() {
        assertEquals(1f, crossfadeVolume(50_000, 10_000, fade, -1, 0), 0.001f)
    }
}
