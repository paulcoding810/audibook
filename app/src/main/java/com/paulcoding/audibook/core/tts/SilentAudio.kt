package com.paulcoding.audibook.core.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Loops silence so the system attributes playback to this app while another process (the TTS engine) speaks. */
class SilentAudio {
    private var track: AudioTrack? = null

    fun start() {
        if (track != null) return
        val frames = SAMPLE_RATE / 2
        track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(frames * 2)
                .build()
                .apply {
                    write(ShortArray(frames), 0, frames)
                    setLoopPoints(0, frames, -1)
                    play()
                }
        }.getOrNull()
    }

    fun stop() {
        track?.run {
            runCatching { stop() }
            release()
        }
        track = null
    }

    private companion object {
        const val SAMPLE_RATE = 8000
    }
}
