package com.paulcoding.audibook.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.paulcoding.audibook.core.data.TtsSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

data class SystemVoice(
    val name: String,
    val locale: Locale,
    val requiresNetwork: Boolean,
    val quality: Int,
)

class SystemTtsEngine(private val context: Context) : SpeechEngine {
    private var tts: TextToSpeech? = null
    private var counter = 0L
    private val silence = SilentAudio()
    private val initMutex = Mutex()

    private sealed interface Event {
        val id: String
        data class Started(override val id: String) : Event
        data class Finished(override val id: String) : Event
        data class Failed(override val id: String, val code: Int) : Event
    }

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

        val events = Channel<Event>(Channel.UNLIMITED)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                events.trySend(Event.Started(utteranceId))
            }

            override fun onDone(utteranceId: String) {
                events.trySend(Event.Finished(utteranceId))
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                events.trySend(Event.Failed(utteranceId, TextToSpeech.ERROR))
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                events.trySend(Event.Failed(utteranceId, errorCode))
            }
        })

        val queue = ArrayDeque<Pair<String, Utterance>>()
        var tail: Utterance? = first

        fun enqueue(utterance: Utterance) {
            val id = "u${counter++}"
            val result = tts.speak(utterance.text, TextToSpeech.QUEUE_ADD, null, id)
            if (result != TextToSpeech.SUCCESS) throw TtsException("Text-to-speech failed to queue text")
            queue.addLast(id to utterance)
        }

        suspend fun fill() {
            while (queue.size < LOOKAHEAD) {
                val utterance = tail ?: return
                enqueue(utterance)
                tail = next(utterance)
            }
        }

        try {
            silence.start()
            fill()
            while (queue.isNotEmpty()) {
                val event = events.receive()
                val index = queue.indexOfFirst { it.first == event.id }
                if (index < 0) continue
                when (event) {
                    is Event.Started -> onStart(queue[index].second)
                    is Event.Finished -> {
                        repeat(index + 1) { queue.removeFirst() }
                        fill()
                    }
                    is Event.Failed -> throw TtsException(errorMessage(event.code))
                }
            }
        } finally {
            tts.stop()
            silence.stop()
        }
    }

    private fun applySettings(tts: TextToSpeech, settings: TtsSettings, language: String?) {
        tts.setSpeechRate(settings.speed)
        tts.setPitch(settings.pitch)
        val voice = settings.systemVoice?.let { name -> tts.voices?.firstOrNull { it.name == name } }
        when {
            voice != null -> tts.voice = voice
            language != null -> {
                val locale = Locale.forLanguageTag(language)
                if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
            }
            else -> tts.voice = tts.defaultVoice
        }
    }

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
        created.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        return created
    }

    private fun errorMessage(code: Int) = when (code) {
        TextToSpeech.ERROR_NETWORK, TextToSpeech.ERROR_NETWORK_TIMEOUT -> "Voice needs a network connection"
        TextToSpeech.ERROR_NOT_INSTALLED_YET -> "Voice data is not installed yet"
        TextToSpeech.ERROR_SYNTHESIS -> "Speech synthesis failed"
        else -> "Text-to-speech error ($code)"
    }

    private companion object {
        const val LOOKAHEAD = 3
        const val GOOGLE_TTS = "com.google.android.tts"
    }
}
