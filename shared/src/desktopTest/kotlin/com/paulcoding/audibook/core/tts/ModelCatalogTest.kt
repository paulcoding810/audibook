package com.paulcoding.audibook.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelCatalogTest {
    private fun model(id: String, mb: Long = 20) = ModelCatalog.parseOne(id, mb * 1024 * 1024)!!

    @Test
    fun parsesPiper() {
        val m = model("vits-piper-en_US-amy-low-int8")
        assertEquals(ModelType.VITS, m.type)
        assertEquals("en_US", m.language)
        assertEquals("Piper Amy (low)", m.name)
        assertEquals(VoiceWeight.LIGHT, m.weight)
        assertEquals("Piper Libritts R (medium) · 904 voices", model("vits-piper-en_US-libritts_r-medium-int8").name)
        assertEquals("Piper SA Dii (high)", model("vits-piper-ar_JO-SA_dii-high-fp16").name)
        assertEquals("vi_VN", model("vits-piper-vi_VN-vais1000-medium").language)
    }

    @Test
    fun parsesOtherFamilies() {
        assertEquals("Mimic3 Vais1000 (low)", model("vits-mimic3-vi_VN-vais1000_low").name)
        assertEquals("deu", model("vits-mms-deu").language)
        assertEquals("zh", model("vits-zh-hf-eula").language)
        assertEquals("yue", model("vits-cantonese-hf-xiaomaiiwn").language)
        assertEquals("zh_en", model("vits-melo-tts-zh_en").language)
        val kokoro = model("kokoro-int8-multi-lang-v1_1", 147)
        assertEquals(ModelType.KOKORO, kokoro.type)
        assertEquals(ModelCatalog.MULTILINGUAL, kokoro.language)
        assertEquals(103, kokoro.speakers)
        assertEquals("Kokoro v1.1 · 103 voices", kokoro.name)
        assertEquals("Kokoro v0.19 · 11 voices", model("kokoro-int8-en-v0_19", 103).name)
        assertEquals(VoiceWeight.HEAVY, kokoro.weight)
        assertEquals(8, model("kitten-nano-en-v0_8-int8").speakers)
    }

    @Test
    fun skipsUnsupportedEngines() {
        assertNull(ModelCatalog.parseOne("matcha-icefall-en_US-ljspeech", 0))
        assertNull(ModelCatalog.parseOne("sherpa-onnx-pocket-tts-int8-2026-01-26", 0))
        assertNull(ModelCatalog.parseOne("sherpa-onnx-supertonic-tts-int8-2026-03-06", 0))
    }

    @Test
    fun keepsSmallestPrecisionPerVoiceAndIgnoresOtherFiles() {
        val assets = listOf(
            CatalogAsset("vits-piper-en_US-amy-low.tar.bz2", 67L shl 20),
            CatalogAsset("vits-piper-en_US-amy-low-fp16.tar.bz2", 35L shl 20),
            CatalogAsset("vits-piper-en_US-amy-low-int8.tar.bz2", 21L shl 20),
            CatalogAsset("kokoro-en-v0_19.tar.bz2", 319L shl 20),
            CatalogAsset("kokoro-int8-en-v0_19.tar.bz2", 103L shl 20),
            CatalogAsset("vits-mms-deu.tar.bz2", 100L shl 20),
            CatalogAsset("espeak-ng-data.zip", 1),
            CatalogAsset("matcha-icefall-zh-baker.tar.bz2", 1),
        )
        assertEquals(
            setOf("vits-piper-en_US-amy-low-int8", "kokoro-int8-en-v0_19", "vits-mms-deu"),
            ModelCatalog.parse(assets).map { it.id }.toSet(),
        )
    }

    @Test
    fun labelsLanguages() {
        assertEquals("English (United States)", ModelCatalog.languageLabel("en_US"))
        assertEquals("Chinese + English", ModelCatalog.languageLabel("zh_en"))
        assertEquals("Multilingual", ModelCatalog.languageLabel("multi"))
        assertEquals("Vietnamese (Vietnam)", ModelCatalog.languageLabel("vi_VN"))
        assertEquals("vi", ModelCatalog.baseLanguage("vi_VN"))
    }

    @Test
    fun parsesEveryRecommendedId() {
        ModelCatalog.recommended.forEach { assertEquals(it, model(it).id) }
    }
}
