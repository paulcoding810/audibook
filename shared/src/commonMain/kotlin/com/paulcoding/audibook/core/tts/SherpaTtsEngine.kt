package com.paulcoding.audibook.core.tts

import com.paulcoding.audibook.core.data.TtsSettings
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** A loaded sherpa-onnx voice; each platform wraps its own binding (Android AAR, desktop JVM jar). */
interface SherpaVoice {
    val numSpeakers: Int
    fun generate(text: String, speaker: Int, speed: Float): PcmAudio
    fun release()
}

fun interface SherpaLoader {
    fun load(files: ModelFiles, numThreads: Int): SherpaVoice
}

class SherpaTtsEngine(
    private val models: ModelManager,
    private val loader: SherpaLoader,
    private val output: PcmOutputFactory,
) : SpeechEngine {
    private val synthDispatcher = Executors.newSingleThreadExecutor { Thread(it, "sherpa-tts") }.asCoroutineDispatcher()
    private var loaded: Pair<String, SherpaVoice>? = null

    override suspend fun speak(
        first: Utterance,
        settings: TtsSettings,
        language: String?,
        next: suspend (Utterance) -> Utterance?,
        onStart: (Utterance) -> Unit,
    ) {
        val modelId = settings.sherpaModelId ?: throw TtsException("Choose an on-device voice in Settings")
        val tts = withContext(synthDispatcher) { load(modelId) }
        val speaker = settings.sherpaSpeaker.coerceIn(0, (tts.numSpeakers - 1).coerceAtLeast(0))
        playPipelined(first, next, onStart, BUFFERED_SENTENCES, output, synthDispatcher) { utterance ->
            tts.generate(utterance.text, speaker, settings.speed)
        }
    }

    private fun load(modelId: String): SherpaVoice {
        loaded?.takeIf { it.first == modelId }?.let { return it.second }
        loaded?.second?.release()
        loaded = null
        val files = models.files(modelId) ?: throw TtsException("Voice model is not installed")
        val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
        return loader.load(files, threads).also {
            loaded = modelId to it
            models.rememberSpeakerCount(modelId, it.numSpeakers)
        }
    }

    private companion object {
        const val BUFFERED_SENTENCES = 2
    }
}
