package com.paulcoding.audibook.core.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager

/** Listens for a deliberate shake (sustained back-and-forth motion), keeping the CPU awake for at most [maxListenMs]. */
class ShakeDetector(context: Context) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AudiBook:shake")
        .apply { setReferenceCounted(false) }
    private var listener: SensorEventListener? = null

    val isAvailable get() = accelerometer != null

    fun start(maxListenMs: Long, onShake: () -> Unit) {
        stop()
        val sensor = accelerometer ?: return
        val window = ShakeWindow()
        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                if (window.add(event.timestamp, x * x + y * y + z * z > ACCELERATING_SQUARED)) {
                    stop()
                    onShake()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        wakeLock.acquire(maxListenMs)
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        listener?.let(sensorManager::unregisterListener)
        listener = null
        if (wakeLock.isHeld) wakeLock.release()
    }

    private companion object {
        /** ~1.33 g: a normal hand shake goes well past this; lying still reads ~1 g. */
        const val ACCELERATING_SQUARED = 13f * 13f
    }
}

/**
 * Sliding window of recent samples (the approach of Square's "seismic"): it's a shake when the samples cover
 * at least [MIN_SPAN_NS] and three quarters of them are accelerating. One bump (e.g. rolling over) isn't enough.
 */
internal class ShakeWindow {
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
