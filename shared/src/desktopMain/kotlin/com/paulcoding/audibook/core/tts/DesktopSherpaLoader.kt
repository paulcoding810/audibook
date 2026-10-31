package com.paulcoding.audibook.core.tts

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

/** Loads voices through the sherpa-onnx JVM jar; its native library comes from the per-OS jar on the classpath. */
object DesktopSherpaLoader : SherpaLoader {
    override fun load(files: ModelFiles, numThreads: Int): SherpaVoice {
        val config = OfflineTtsConfig.builder()
            .setModel(modelConfig(files, numThreads))
            .setMaxNumSentences(1)
            .build()
        val tts = OfflineTts(config)
        return object : SherpaVoice {
            override val numSpeakers = tts.numSpeakers

            override fun generate(text: String, speaker: Int, speed: Float): PcmAudio =
                tts.generate(text, speaker, speed).let { PcmAudio(it.samples, it.sampleRate) }

            override fun release() = tts.release()
        }
    }

    private fun modelConfig(files: ModelFiles, threads: Int): OfflineTtsModelConfig {
        val builder = OfflineTtsModelConfig.builder().setNumThreads(threads)
        when (files.model.type) {
            ModelType.VITS -> builder.setVits(
                OfflineTtsVitsModelConfig.builder()
                    .setModel(files.onnx.absolutePath)
                    .setTokens(files.tokens.absolutePath)
                    .setDataDir(files.dataDir)
                    .setLexicon(files.lexicons.joinToString(",") { it.absolutePath })
                    .setDictDir(files.dictDir?.absolutePath.orEmpty())
                    .build()
            )
            ModelType.KOKORO -> builder.setKokoro(
                OfflineTtsKokoroModelConfig.builder()
                    .setModel(files.onnx.absolutePath)
                    .setVoices(files.voices?.absolutePath.orEmpty())
                    .setTokens(files.tokens.absolutePath)
                    .setDataDir(files.dataDir)
                    .setLexicon(files.kokoroLexicons)
                    .setDictDir(files.dictDir?.absolutePath.orEmpty())
                    .build()
            )
            ModelType.KITTEN -> builder.setKitten(
                OfflineTtsKittenModelConfig.builder()
                    .setModel(files.onnx.absolutePath)
                    .setVoices(files.voices?.absolutePath.orEmpty())
                    .setTokens(files.tokens.absolutePath)
                    .setDataDir(files.dataDir)
                    .build()
            )
        }
        return builder.build()
    }
}
