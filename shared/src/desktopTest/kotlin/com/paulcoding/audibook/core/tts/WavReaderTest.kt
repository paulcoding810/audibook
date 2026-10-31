package com.paulcoding.audibook.core.tts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavReaderTest {
    private fun wav(sampleRate: Int, channels: Int, bits: Int, format: Int, data: ByteArray, extraChunk: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        fun le(size: Int, block: ByteBuffer.() -> Unit) =
            out.write(ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(block).array())
        out.write("RIFF".toByteArray()); le(4) { putInt(0) }; out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le(4) { putInt(16) }
        le(16) {
            putShort(format.toShort()); putShort(channels.toShort()); putInt(sampleRate)
            putInt(sampleRate * channels * bits / 8); putShort((channels * bits / 8).toShort()); putShort(bits.toShort())
        }
        if (extraChunk) {
            out.write("LIST".toByteArray()); le(4) { putInt(3) }; out.write(byteArrayOf(1, 2, 3, 0))
        }
        out.write("data".toByteArray()); le(4) { putInt(data.size) }
        out.write(data)
        return out.toByteArray()
    }

    private fun shorts(vararg values: Short) =
        ByteBuffer.allocate(values.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { values.forEach { putShort(it) } }.array()

    @Test
    fun decodes16BitMono() {
        val audio = WavReader.decode(wav(24000, 1, 16, 1, shorts(0, 16384, -32768)))
        assertEquals(24000, audio.sampleRate)
        assertArrayEquals(floatArrayOf(0f, 0.5f, -1f), audio.samples, 1e-4f)
    }

    @Test
    fun downmixesStereoAndSkipsUnknownChunks() {
        val audio = WavReader.decode(wav(16000, 2, 16, 1, shorts(16384, 0, -16384, -16384), extraChunk = true))
        assertArrayEquals(floatArrayOf(0.25f, -0.5f), audio.samples, 1e-4f)
    }

    @Test
    fun decodesFloat() {
        val data = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(0.25f).putFloat(-0.75f).array()
        assertArrayEquals(floatArrayOf(0.25f, -0.75f), WavReader.decode(wav(22050, 1, 32, 3, data)).samples, 1e-6f)
    }

    @Test
    fun toleratesStreamingHeaderWithOversizedDataLength() {
        val bytes = wav(8000, 1, 16, 1, shorts(100, 200))
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes.size - 8, -1)
        assertEquals(2, WavReader.decode(bytes).samples.size)
    }

    @Test(expected = TtsException::class)
    fun rejectsNonWav() {
        WavReader.decode(ByteArray(64))
    }
}
