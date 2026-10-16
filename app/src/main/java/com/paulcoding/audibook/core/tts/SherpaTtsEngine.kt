package com.paulcoding.audibook.core.tts

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.paulcoding.audibook.core.data.TtsSettings
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class SherpaTtsEngine(private val models: ModelManager) : SpeechEngine {
    private val synthDispatcher = Executors.newSingleThreadExecutor { Thread(it, "sherpa-tts") }.asCoroutineDispatcher()
    private var loaded: Pair<String, OfflineTts>? = null

    override suspend fun speak(
        first: Utterance,
        settings: TtsSettings,
        language: String?,
        next: suspend (Utterance) -> Utterance?,
        onStart: (Utterance) -> Unit,
    ) {
        val modelId = settings.sherpaModelId ?: throw TtsException("Choose an on-device voice in Settings")
        val tts = withContext(synthDispatcher) { load(modelId) }
        val speaker = settings.sherpaSpeaker.coerceIn(0, (tts.numSpeakers() - 1).coerceAtLeast(0))
        playPipelined(first, next, onStart, BUFFERED_SENTENCES, synthDispatcher) { utterance ->
            val audio = tts.generate(utterance.text, speaker, settings.speed)
            PcmAudio(audio.samples, audio.sampleRate)
        }
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
