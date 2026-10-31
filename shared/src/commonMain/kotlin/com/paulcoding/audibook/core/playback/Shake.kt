package com.paulcoding.audibook.core.playback

/** Detects a deliberate shake of the device, used to resume after the sleep timer. */
interface ShakeSensor {
    val isAvailable: Boolean

    /** Listens for up to [maxListenMs], calling [onShake] once. */
    fun start(maxListenMs: Long, onShake: () -> Unit)
    fun stop()
}

object NoShakeSensor : ShakeSensor {
    override val isAvailable = false
    override fun start(maxListenMs: Long, onShake: () -> Unit) = Unit
    override fun stop() = Unit
}

/**
 * Sliding window of recent samples (the approach of Square's "seismic"): it's a shake when the samples cover
 * at least [MIN_SPAN_NS] and three quarters of them are accelerating. One bump (e.g. rolling over) isn't enough.
 */
class ShakeWindow {
    private val samples = ArrayDeque<Pair<Long, Boolean>>()

    /** Adds a sample at [timestampNs]; returns true when the window now counts as a shake. */
    fun add(timestampNs: Long, accelerating: Boolean): Boolean {
        samples.addLast(timestampNs to accelerating)
        while (samples.size > MIN_SAMPLES && timestampNs - samples.first().first > MAX_SPAN_NS) samples.removeFirst()
        val span = timestampNs - samples.first().first
        val shaking = samples.size >= MIN_SAMPLES && span >= MIN_SPAN_NS &&
            samples.count { it.second } >= samples.size * 3 / 4
        if (shaking) samples.clear()
        return shaking
    }

    private companion object {
        const val MIN_SAMPLES = 4
        const val MIN_SPAN_NS = 250_000_000L
        const val MAX_SPAN_NS = 500_000_000L
    }
}
