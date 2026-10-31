package com.paulcoding.audibook.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeWindowTest {
    private val stepNs = 20_000_000L // 50 Hz, like SENSOR_DELAY_GAME

    /** Feeds [pattern] (true = accelerating) and returns whether any sample triggered a shake. */
    private fun feed(window: ShakeWindow, pattern: List<Boolean>, startNs: Long = 0): Boolean =
        pattern.withIndex().any { (i, accelerating) -> window.add(startNs + i * stepNs, accelerating) }

    @Test
    fun stillPhoneNeverShakes() {
        assertFalse(feed(ShakeWindow(), List(500) { false }))
    }

    @Test
    fun sustainedShakingTriggers() {
        assertTrue(feed(ShakeWindow(), List(10) { false } + List(20) { true }))
    }

    @Test
    fun singleBumpDoesNotTrigger() {
        val bump = List(50) { false } + List(4) { true } + List(50) { false }
        assertFalse(feed(ShakeWindow(), bump))
    }

    @Test
    fun mostlyStillWithOccasionalSpikesDoesNotTrigger() {
        val pattern = List(300) { it % 3 == 0 }
        assertFalse(feed(ShakeWindow(), pattern))
    }

    @Test
    fun realisticShakeWithGapsTriggers() {
        // Back-and-forth: accelerating except near each turning point.
        val pattern = List(40) { it % 8 != 0 }
        assertTrue(feed(ShakeWindow(), pattern))
    }
}
