package com.paulcoding.audibook.core.tts

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodes 8/16/32-bit integer or 32-bit float PCM WAV data to mono floats. */
object WavReader {
    fun decode(bytes: ByteArray): PcmAudio {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 12 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") {
            throw TtsException("Speech engine returned invalid audio")
        }
        var format = 1
        var channels = 1
        var sampleRate = 0
        var bits = 16
        var position = 12
        while (position + 8 <= bytes.size) {
            val id = String(bytes, position, 4)
            val declared = buffer.getInt(position + 4).toLong() and 0xFFFFFFFFL
            val start = position + 8
            val size = declared.coerceAtMost((bytes.size - start).toLong()).toInt()
            when (id) {
                "fmt " -> {
                    format = buffer.getShort(start).toInt()
                    channels = buffer.getShort(start + 2).toInt().coerceAtLeast(1)
                    sampleRate = buffer.getInt(start + 4)
                    bits = buffer.getShort(start + 14).toInt()
                }
                "data" -> return PcmAudio(samples(buffer, start, size, format, channels, bits), sampleRate)
            }
            position = start + size + (size and 1)
        }
        throw TtsException("Speech engine returned no audio")
    }

    private fun samples(buffer: ByteBuffer, start: Int, size: Int, format: Int, channels: Int, bits: Int): FloatArray {
        val bytesPerSample = bits / 8
        val frames = size / (bytesPerSample * channels)
        val out = FloatArray(frames)
        for (frame in 0 until frames) {
            var sum = 0f
            for (channel in 0 until channels) {
                val offset = start + (frame * channels + channel) * bytesPerSample
                sum += when {
                    format == 3 && bits == 32 -> buffer.getFloat(offset)
                    bits == 16 -> buffer.getShort(offset) / 32768f
                    bits == 8 -> ((buffer.get(offset).toInt() and 0xFF) - 128) / 128f
                    bits == 32 -> buffer.getInt(offset) / 2147483648f
                    else -> throw TtsException("Unsupported audio format ($bits-bit)")
                }
            }
            out[frame] = sum / channels
        }
        return out
    }
}
