package com.paulcoding.audibook.core.tts

import java.util.Locale

/** A downloadable file on the sherpa-onnx `tts-models` release. */
data class CatalogAsset(val name: String, val sizeBytes: Long)

/**
 * Turns sherpa-onnx release file names (e.g. `vits-piper-en_US-amy-low-int8.tar.bz2`) into voices AudiBook can run.
 * Each voice is published in up to three precisions; only the smallest (int8 > fp16 > fp32) is listed.
 */
object ModelCatalog {
    /** Curated defaults shown first; known speaker counts for multi-speaker models. */
    val recommended = listOf(
        "vits-piper-en_US-amy-low-int8",
        "vits-piper-en_US-lessac-medium-int8",
        "vits-piper-en_US-ryan-medium-int8",
        "vits-piper-en_GB-alba-medium-int8",
        "vits-piper-en_US-libritts_r-medium-int8",
        "vits-piper-vi_VN-vais1000-medium-int8",
        "kitten-nano-en-v0_8-int8",
        "kokoro-int8-en-v0_19",
        "kokoro-int8-multi-lang-v1_1",
    )

    private val knownSpeakers = mapOf(
        "vits-piper-en_US-libritts_r-medium" to 904,
        "kitten-nano-en-v0_8" to 8,
        "kokoro-en-v0_19" to 11,
        "kokoro-multi-lang-v1_0" to 53,
        "kokoro-multi-lang-v1_1" to 103,
    )

    private val precisionTokens = listOf("int8", "fp16", "fp32")
    private val qualities = listOf("x_low", "low", "medium", "high")
    private val version = Regex("^v\\d+(_\\d+)*$")
    private val languageCode = Regex("^[a-z]{2,3}(_([A-Z]{2}|[a-z]{2}))?$")
    private val families = listOf(
        "vits-piper" to "Piper", "vits-mimic3" to "Mimic3", "vits-coqui" to "Coqui", "vits-mms" to "MMS",
        "vits-icefall" to "Icefall", "vits-melo-tts" to "Melo", "vits-inflect" to "Inflect",
        "vits-cantonese" to "VITS", "vits-zh" to "VITS", "sherpa-onnx-vits" to "VITS", "vits" to "VITS",
        "kokoro" to "Kokoro", "kitten" to "Kitten",
    )

    fun parse(assets: List<CatalogAsset>): List<TtsModel> =
        assets.mapNotNull { asset ->
            asset.name.removeSuffix(".tar.bz2").takeIf { it != asset.name }?.let { parseOne(it, asset.sizeBytes) }
        }
            .groupBy { variantKey(it.id) }
            .map { (_, variants) -> variants.minBy { precisionRank(it.id) } }

    /** Parses one release file id (without `.tar.bz2`), or null if its engine isn't supported. */
    fun parseOne(id: String, sizeBytes: Long): TtsModel? {
        val type = when {
            id.startsWith("kokoro") -> ModelType.KOKORO
            id.startsWith("kitten") -> ModelType.KITTEN
            id.startsWith("vits-") || id.startsWith("sherpa-onnx-vits-") -> ModelType.VITS
            else -> return null
        }
        val (prefix, family) = families.first { id.startsWith(it.first) }
        val tokens = id.removePrefix(prefix).split('-').filter { it.isNotEmpty() && it !in precisionTokens }
        val language = when {
            id.startsWith("vits-cantonese") -> "yue"
            id.startsWith("vits-zh") || id.startsWith("sherpa-onnx-vits-zh") -> "zh"
            id.startsWith("vits-ljs") || id.startsWith("vits-vctk") -> "en"
            type == ModelType.KOKORO && id.contains("multi-lang") -> MULTILINGUAL
            type == ModelType.KITTEN -> "en"
            else -> tokens.firstOrNull { languageCode.matches(it) } ?: ""
        }
        val rest = tokens.filterNot { it == language || it == "multi" || it == "lang" || it == "tts" }
        val sizeMb = (sizeBytes / (1024 * 1024)).toInt()
        val speakers = knownSpeakers[variantKey(id)] ?: 0
        return TtsModel(
            id = id,
            name = displayName(family, rest, speakers),
            language = language,
            sizeMb = sizeMb,
            type = type,
            speakers = speakers,
            weight = if (type == ModelType.KOKORO || sizeMb >= 100) VoiceWeight.HEAVY else VoiceWeight.LIGHT,
        )
    }

    /** Human-readable language, e.g. "en_US" → "English (United States)", "zh_en" → "Chinese + English". */
    fun languageLabel(code: String): String {
        if (code.isEmpty()) return "Other"
        if (code == MULTILINGUAL) return "Multilingual"
        val parts = code.split('_')
        if (parts.size == 2 && parts[1].all(Char::isLowerCase)) return "${languageName(parts[0])} + ${languageName(parts[1])}"
        val locale = if (parts.size == 2) Locale(parts[0], parts[1]) else Locale(parts[0])
        return locale.getDisplayName(Locale.ENGLISH).takeIf { it.isNotBlank() && it != code } ?: code
    }

    /** Base language for filtering, e.g. "en_US" → "en". */
    fun baseLanguage(code: String) = code.substringBefore('_')

    private fun languageName(code: String) = Locale(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }

    private fun displayName(family: String, rest: List<String>, speakers: Int): String {
        val quality = rest.lastOrNull()?.let { last -> qualities.firstOrNull { last == it || last.endsWith("_$it") } }
        val words = rest.mapIndexed { index, token ->
            if (index == rest.lastIndex && quality != null) token.removeSuffix(quality).removeSuffix("_") else token
        }.filter { it.isNotEmpty() }
        val speaker = words.flatMap { word ->
            if (version.matches(word)) listOf(word.replace('_', '.')) else word.split('_').filter { it.isNotEmpty() }
        }.joinToString(" ") { if (version.matches(it.replace('.', '_'))) it else it.replaceFirstChar(Char::titlecase) }
        return buildString {
            append(family)
            if (speaker.isNotEmpty()) append(' ').append(speaker)
            if (quality != null) append(" (").append(quality.replace('_', '-')).append(')')
            if (speakers > 1) append(" · ").append(speakers).append(" voices")
        }
    }

    private fun variantKey(id: String) = id.split('-').filterNot { it in precisionTokens }.joinToString("-")

    private fun precisionRank(id: String) = id.split('-').let { tokens ->
        when {
            "int8" in tokens -> 0
            "fp16" in tokens -> 1
            else -> 2
        }
    }

    const val MULTILINGUAL = "multi"
}
