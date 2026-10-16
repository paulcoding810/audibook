package com.paulcoding.audibook.core.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.paulcoding.audibook.core.data.TtsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

class SherpaTtsEngine(private val models: ModelManager) : SpeechEngine {
    private val synthDispatcher = Executors.newSingleThreadExecutor { Thread(it, "sherpa-tts") }.asCoroutineDispatcher()
    private var loaded: Pair<String, OfflineTts>? = null

    private class Chunk(val utterance: Utterance, val samples: FloatArray)

    override suspend fun speak(
        first: Utterance,
        settings: TtsSettings,
        language: String?,
        next: suspend (Utterance) -> Utterance?,
        onStart: (Utterance) -> Unit,
    ) = coroutineScope {
        val modelId = settings.sherpaModelId ?: throw TtsException("Choose an on-device voice in Settings")
        val tts = withContext(synthDispatcher) { load(modelId) }
        val speaker = settings.sherpaSpeaker.coerceIn(0, (tts.numSpeakers() - 1).coerceAtLeast(0))
        val sampleRate = tts.sampleRate()

        val chunks = Channel<Chunk>(capacity = BUFFERED_SENTENCES)
        launch(synthDispatcher) {
            var utterance: Utterance? = first
            while (utterance != null) {
                ensureActive()
                val audio = tts.generate(utterance.text, speaker, settings.speed)
                chunks.send(Chunk(utterance, audio.samples))
                utterance = next(utterance)
            }
            chunks.close()
        }

        val track = createTrack(sampleRate)
        var framesWritten = 0L
        try {
            track.play()
            for (chunk in chunks) {
                onStart(chunk.utterance)
                withContext(Dispatchers.IO) {
                    var offset = 0
                    while (offset < chunk.samples.size && isActive) {
                        val written = track.write(chunk.samples, offset, chunk.samples.size - offset, AudioTrack.WRITE_BLOCKING)
                        if (written < 0) throw TtsException("Audio output error ($written)")
                        offset += written
                    }
                    framesWritten += offset
                }
            }
            while (track.playbackHeadPosition.toLong() and 0xFFFFFFFFL < framesWritten) delay(50)
        } finally {
            track.pause()
            track.flush()
            track.release()
        }
    }

    private fun createTrack(sampleRate: Int): AudioTrack {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun load(modelId: String): OfflineTts {
        loaded?.takeIf { it.first == modelId }?.let { return it.second }
        loaded?.second?.release()
        loaded = null
        val files = models.files(modelId) ?: throw TtsException("Voice model is not installed")
        return OfflineTts(null, OfflineTtsConfig(model = modelConfig(files), maxNumSentences = 1)).also {
            loaded = modelId to it
        }
    }

    private fun modelConfig(files: ModelFiles): OfflineTtsModelConfig {
        val dataDir = files.espeakData?.absolutePath.orEmpty()
        val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
        return when (files.model.type) {
            ModelType.VITS -> OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = files.onnx.absolutePath,
                    tokens = files.tokens.absolutePath,
                    dataDir = dataDir,
                    lexicon = files.lexicons.joinToString(",") { it.absolutePath },
                ),
                numThreads = threads,
            )
            ModelType.KOKORO -> OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = files.onnx.absolutePath,
                    voices = files.voices?.absolutePath.orEmpty(),
                    tokens = files.tokens.absolutePath,
                    dataDir = dataDir,
                    lexicon = kokoroLexicons(files),
                    dictDir = files.dictDir?.absolutePath.orEmpty(),
                ),
                numThreads = threads,
            )
            ModelType.KITTEN -> OfflineTtsModelConfig(
                kitten = OfflineTtsKittenModelConfig(
                    model = files.onnx.absolutePath,
                    voices = files.voices?.absolutePath.orEmpty(),
                    tokens = files.tokens.absolutePath,
                    dataDir = dataDir,
                ),
                numThreads = threads,
            )
        }
    }

    private fun kokoroLexicons(files: ModelFiles): String {
        val hasUs = files.lexicons.any { it.name == "lexicon-us-en.txt" }
        return files.lexicons
            .filterNot { hasUs && it.name == "lexicon-gb-en.txt" }
            .joinToString(",") { it.absolutePath }
    }

    private companion object {
        const val BUFFERED_SENTENCES = 2
    }
}
