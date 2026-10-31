package com.paulcoding.audibook.core.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager

/** Listens for a deliberate shake (sustained back-and-forth motion), keeping the CPU awake for at most [maxListenMs]. */
class ShakeDetector(context: Context) : ShakeSensor {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val wakeLock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AudiBook:shake")
        .apply { setReferenceCounted(false) }
    private var listener: SensorEventListener? = null

    override val isAvailable get() = accelerometer != null

    override fun start(maxListenMs: Long, onShake: () -> Unit) {
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

    override fun stop() {
        listener?.let(sensorManager::unregisterListener)
        listener = null
        if (wakeLock.isHeld) wakeLock.release()
    }

    private companion object {
        /** ~1.33 g: a normal hand shake goes well past this; lying still reads ~1 g. */
        const val ACCELERATING_SQUARED = 13f * 13f
    }
}
