package com.paulcoding.audibook.core.tts

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

/** Loads voices through the sherpa-onnx Android AAR. */
object AndroidSherpaLoader : SherpaLoader {
    override fun load(files: ModelFiles, numThreads: Int): SherpaVoice {
        val tts = OfflineTts(null, OfflineTtsConfig(model = modelConfig(files, numThreads), maxNumSentences = 1))
        return object : SherpaVoice {
            override val numSpeakers = tts.numSpeakers()

            override fun generate(text: String, speaker: Int, speed: Float): PcmAudio =
                tts.generate(text, speaker, speed).let { PcmAudio(it.samples, it.sampleRate) }

            override fun release() = tts.release()
        }
    }

    private fun modelConfig(files: ModelFiles, threads: Int): OfflineTtsModelConfig = when (files.model.type) {
        ModelType.VITS -> OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(
                model = files.onnx.absolutePath,
                tokens = files.tokens.absolutePath,
                dataDir = files.dataDir,
                lexicon = files.lexicons.joinToString(",") { it.absolutePath },
                dictDir = files.dictDir?.absolutePath.orEmpty(),
            ),
            numThreads = threads,
        )
        ModelType.KOKORO -> OfflineTtsModelConfig(
            kokoro = OfflineTtsKokoroModelConfig(
                model = files.onnx.absolutePath,
                voices = files.voices?.absolutePath.orEmpty(),
                tokens = files.tokens.absolutePath,
                dataDir = files.dataDir,
                lexicon = files.kokoroLexicons,
                dictDir = files.dictDir?.absolutePath.orEmpty(),
            ),
            numThreads = threads,
        )
        ModelType.KITTEN -> OfflineTtsModelConfig(
            kitten = OfflineTtsKittenModelConfig(
                model = files.onnx.absolutePath,
                voices = files.voices?.absolutePath.orEmpty(),
                tokens = files.tokens.absolutePath,
                dataDir = files.dataDir,
            ),
            numThreads = threads,
        )
    }
}
