package com.paulcoding.audibook.core.tts

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

/** A playing mono float PCM stream (AudioTrack on Android, a SourceDataLine on desktop). */
interface PcmOutput {
    /** Blocks until [size] samples from [offset] are buffered; returns the count written or a negative error code. */
    fun write(samples: FloatArray, offset: Int, size: Int): Int

    /** Frames actually played so far. */
    val framesPlayed: Long

    /** Stops immediately, discarding buffered audio. */
    fun release()
}

fun interface PcmOutputFactory {
    /** Opens and starts an output for [sampleRate] Hz. */
    fun open(sampleRate: Int): PcmOutput
}

/** Synthesizes up to [bufferAhead] sentences ahead while playing them in order; gapless as long as synthesis outpaces playback. */
suspend fun playPipelined(
    first: Utterance,
    next: suspend (Utterance) -> Utterance?,
    onStart: (Utterance) -> Unit,
    bufferAhead: Int,
    output: PcmOutputFactory,
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

    var track: PcmOutput? = null
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
                track = output.open(audio.sampleRate)
                sampleRate = audio.sampleRate
                framesWritten = 0
            }
            val out = track!!
            onStart(utterance)
            withContext(Dispatchers.IO) {
                var offset = 0
                while (offset < audio.samples.size && isActive) {
                    val size = minOf(audio.samples.size - offset, audio.sampleRate / WRITES_PER_SECOND)
                    val written = out.write(audio.samples, offset, size)
                    if (written < 0) throw TtsException("Audio output error ($written)")
                    offset += written
                }
                framesWritten += offset
            }
        }
        track?.let { drain(it, framesWritten) }
    } finally {
        track?.release()
    }
}

/** Small writes keep cancellation responsive: a blocking write only returns once its data is buffered. */
private const val WRITES_PER_SECOND = 20

private suspend fun drain(track: PcmOutput, framesWritten: Long) {
    while (track.framesPlayed < framesWritten) delay(20)
}
