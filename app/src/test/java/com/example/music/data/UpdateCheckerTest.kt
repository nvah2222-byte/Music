package com.example.music.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun newerMajorOrMinorIsDetected() {
        assertTrue(UpdateChecker.isNewer("1.1", "1.0"))
        assertTrue(UpdateChecker.isNewer("v2.0.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("v1.2", "1.2"))
        assertFalse(UpdateChecker.isNewer("1.2.0", "1.2"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("1.0", "1.1"))
    }

    @Test
    fun junkTagIsIgnored() {
        assertFalse(UpdateChecker.isNewer("nightly", "1.0"))
        assertFalse(UpdateChecker.isNewer("", "1.0"))
    }

    @Test
    fun prereleaseSuffixIsIgnored() {
        assertTrue(UpdateChecker.isNewer("1.3-beta", "1.2"))
    }
}
