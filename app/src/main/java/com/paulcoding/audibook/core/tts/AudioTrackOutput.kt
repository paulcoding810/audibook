package com.paulcoding.audibook.core.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class AudioTrackOutput(sampleRate: Int) : PcmOutput {
    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 2)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
        .apply { play() }

    override fun write(samples: FloatArray, offset: Int, size: Int) =
        track.write(samples, offset, size, AudioTrack.WRITE_BLOCKING)

    override val framesPlayed get() = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL

    override fun release() {
        track.pause()
        track.flush()
        track.release()
    }
}
