package com.paulcoding.audibook.core.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.os.SystemClock
import kotlin.math.sqrt

/** Listens for a deliberate shake (two strong jolts within a second), keeping the CPU awake for at most [maxListenMs]. */
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
        var lastJolt = 0L
        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                if (gForce < JOLT_G) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastJolt in MIN_GAP_MS..MAX_GAP_MS) {
                    stop()
                    onShake()
                } else if (now - lastJolt > MIN_GAP_MS) {
                    lastJolt = now
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
        const val JOLT_G = 2.3f
        const val MIN_GAP_MS = 120L
        const val MAX_GAP_MS = 1_000L
    }
}
