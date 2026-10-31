package com.paulcoding.audibook.core.tts

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/** Plays mono PCM through Java Sound as 16-bit samples, which every mixer supports. */
class JavaSoundOutput(sampleRate: Int) : PcmOutput {
    private val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
    private val line = AudioSystem.getSourceDataLine(format).apply {
        open(format, sampleRate / 2 * format.frameSize)
        start()
    }
    private var buffer = ByteArray(0)

    override fun write(samples: FloatArray, offset: Int, size: Int): Int {
        if (buffer.size < size * 2) buffer = ByteArray(size * 2)
        for (i in 0 until size) {
            val value = (samples[offset + i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
            buffer[2 * i] = value.toByte()
            buffer[2 * i + 1] = (value shr 8).toByte()
        }
        return line.write(buffer, 0, size * 2) / 2
    }

    override val framesPlayed get() = line.longFramePosition

    override fun release() {
        line.stop()
        line.flush()
        line.close()
    }
}
