package com.paulcoding.audibook.core.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class PcmAudio(val samples: FloatArray, val sampleRate: Int)

/** Synthesizes up to [bufferAhead] sentences ahead while playing them in order; gapless as long as synthesis outpaces playback. */
suspend fun playPipelined(
    first: Utterance,
    next: suspend (Utterance) -> Utterance?,
    onStart: (Utterance) -> Unit,
    bufferAhead: Int,
    synthContext: CoroutineContext,
    synthesize: suspend (Utterance) -> PcmAudio,
) = coroutineScope {
    val chunks = Channel<Pair<Utterance, PcmAudio>>(capacity = bufferAhead)
    launch(synthContext) {
        var utterance: Utterance? = first
        while (utterance != null) {
            ensureActive()
            chunks.send(utterance to synthesize(utterance))
            utterance = next(utterance)
        }
        chunks.close()
    }

    var track: AudioTrack? = null
    var sampleRate = 0
    var framesWritten = 0L
    try {
        for ((utterance, audio) in chunks) {
            if (audio.samples.isEmpty()) continue
            if (audio.sampleRate != sampleRate) {
                track?.let {
                    drain(it, framesWritten)
                    it.release()
                }
                track = createTrack(audio.sampleRate).apply { play() }
                sampleRate = audio.sampleRate
                framesWritten = 0
            }
            val output = track!!
            onStart(utterance)
            withContext(Dispatchers.IO) {
                var offset = 0
                while (offset < audio.samples.size && isActive) {
                    val written = output.write(audio.samples, offset, audio.samples.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) throw TtsException("Audio output error ($written)")
                    offset += written
                }
                framesWritten += offset
            }
        }
        track?.let { drain(it, framesWritten) }
    } finally {
        track?.run {
            pause()
            flush()
            release()
        }
    }
}

private suspend fun drain(track: AudioTrack, framesWritten: Long) {
    while (track.playbackHeadPosition.toLong() and 0xFFFFFFFFL < framesWritten) delay(20)
}

private fun createTrack(sampleRate: Int): AudioTrack {
    val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
    return AudioTrack.Builder()
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
        .setBufferSizeInBytes(minBuffer * 2)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
}
