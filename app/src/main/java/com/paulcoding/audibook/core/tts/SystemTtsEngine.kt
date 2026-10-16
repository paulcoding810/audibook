package com.paulcoding.audibook.core.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.paulcoding.audibook.core.data.TtsSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class SystemVoice(
    val name: String,
    val locale: Locale,
    val requiresNetwork: Boolean,
    val quality: Int,
)

/**
 * Renders sentences ahead with [TextToSpeech.synthesizeToFile] and plays them itself, because the
 * framework's speak() queue only synthesizes ~500 ms ahead and leaves gaps on slower devices.
 */
class SystemTtsEngine(private val context: Context) : SpeechEngine {
    private var tts: TextToSpeech? = null
    private val initMutex = Mutex()
    private val counter = AtomicLong()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Int>>()
    private val cacheDir get() = File(context.cacheDir, "tts").apply { mkdirs() }

    private class VoiceFailure(val code: Int) : Exception()

    suspend fun voices(): List<SystemVoice> = engine().voices.orEmpty()
        .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
        .map { SystemVoice(it.name, it.locale, it.isNetworkConnectionRequired, it.quality) }
        .sortedWith(compareBy({ it.locale.displayName }, { it.name }))

    override suspend fun speak(
        first: Utterance,
        settings: TtsSettings,
        language: String?,
        next: suspend (Utterance) -> Utterance?,
        onStart: (Utterance) -> Unit,
    ) {
        val tts = engine()
        applySettings(tts, settings, language)
        var usedFallback = false

        suspend fun render(text: String): PcmAudio {
            while (true) {
                try {
                    return synthesize(tts, text)
                } catch (failure: VoiceFailure) {
                    if (failure.code in skippableErrors) return PcmAudio(FloatArray(0), 0)
                    val current = tts.voice
                    val offline = current?.let { bestVoice(tts, it.locale, offlineOnly = true) }
                    val fallback = offline?.takeIf { !usedFallback && it.name != current.name }
                        ?: throw TtsException(errorMessage(failure.code, current, hasOfflineVoice = offline != null))
                    Log.w(TAG, "Voice ${current.name} failed (${failure.code}), falling back to ${fallback.name}")
                    tts.voice = fallback
                    usedFallback = true
                }
            }
        }

        try {
            playPipelined(first, next, onStart, BUFFERED_SENTENCES, Dispatchers.Main.immediate) { render(it.text) }
        } finally {
            tts.stop()
            pending.values.forEach { it.cancel() }
            pending.clear()
        }
    }

    private suspend fun synthesize(tts: TextToSpeech, text: String): PcmAudio {
        val id = "s${counter.getAndIncrement()}"
        val file = File(cacheDir, "$id.wav")
        val result = CompletableDeferred<Int>()
        pending[id] = result
        try {
            if (tts.synthesizeToFile(text, Bundle(), file, id) != TextToSpeech.SUCCESS) {
                throw TtsException("Text-to-speech failed to queue text")
            }
            val code = result.await()
            if (code != TextToSpeech.SUCCESS) throw VoiceFailure(code)
            return withContext(Dispatchers.IO) { WavReader.decode(file.readBytes()) }
        } finally {
            pending.remove(id)
            file.delete()
        }
    }

    private fun applySettings(tts: TextToSpeech, settings: TtsSettings, language: String?) {
        tts.setSpeechRate(settings.speed)
        tts.setPitch(settings.pitch)
        val chosen = settings.systemVoice?.let { name -> tts.voices?.firstOrNull { it.name == name } }
        val automatic = language?.let { bestVoice(tts, Locale.forLanguageTag(it), offlineOnly = false) }
        when {
            chosen != null -> tts.voice = chosen
            automatic != null -> tts.voice = automatic
            language != null -> {
                val locale = Locale.forLanguageTag(language)
                if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
            }
            else -> tts.voice = tts.defaultVoice
        }
    }

    /** Installed voice for [locale]: offline first, then concrete (non network-first "language") voices, same country, quality. */
    private fun bestVoice(tts: TextToSpeech, locale: Locale, offlineOnly: Boolean): Voice? =
        tts.voices.orEmpty()
            .filter { it.locale.language == locale.language && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
            .filter { !offlineOnly || !it.isNetworkConnectionRequired }
            .sortedWith(
                compareBy<Voice>(
                    { it.isNetworkConnectionRequired },
                    { LEGACY_LANGUAGE_VOICE in it.features },
                    { locale.country.isNotEmpty() && it.locale.country != locale.country },
                    { -it.quality },
                )
            )
            .firstOrNull()

    private suspend fun engine(): TextToSpeech = initMutex.withLock {
        tts ?: create().also { tts = it }
    }

    private suspend fun create(): TextToSpeech {
        val status = CompletableDeferred<Int>()
        val listener = TextToSpeech.OnInitListener { status.complete(it) }
        val hasGoogle = runCatching { context.packageManager.getPackageInfo(GOOGLE_TTS, 0) }.isSuccess
        val created = if (hasGoogle) TextToSpeech(context, listener, GOOGLE_TTS) else TextToSpeech(context, listener)
        if (status.await() != TextToSpeech.SUCCESS) {
            created.shutdown()
            throw TtsException("No text-to-speech engine available")
        }
        created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit

            override fun onDone(utteranceId: String) {
                pending[utteranceId]?.complete(TextToSpeech.SUCCESS)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                pending[utteranceId]?.complete(TextToSpeech.ERROR)
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                pending[utteranceId]?.complete(errorCode)
            }
        })
        return created
    }

    private fun errorMessage(code: Int, voice: Voice?, hasOfflineVoice: Boolean): String {
        val language = voice?.locale?.displayLanguage ?: "this language"
        return when {
            code in networkErrors && !hasOfflineVoice ->
                "Couldn't reach Google's online $language voice, and no offline $language voice is downloaded. " +
                    "Download it in Voice settings → Install more voices, or use an on-device AI voice."
            code in networkErrors -> "The online $language voice couldn't connect"
            code == TextToSpeech.ERROR_NOT_INSTALLED_YET -> "Voice data for $language is not installed yet"
            else -> "Text-to-speech error ($code)"
        }
    }

    private companion object {
        const val BUFFERED_SENTENCES = 4
        const val GOOGLE_TTS = "com.google.android.tts"
        const val TAG = "SystemTtsEngine"
        const val LEGACY_LANGUAGE_VOICE = "legacySetLanguageVoice"
        val networkErrors = setOf(TextToSpeech.ERROR_NETWORK, TextToSpeech.ERROR_NETWORK_TIMEOUT, TextToSpeech.ERROR_SERVICE)
        val skippableErrors = setOf(TextToSpeech.ERROR_SYNTHESIS, TextToSpeech.ERROR_INVALID_REQUEST)
    }
}
