package com.paulcoding.audibook.core.tts

import com.paulcoding.audibook.core.data.TtsSettings
import java.util.Locale

data class Utterance(val chapter: Int, val sentence: Int, val text: String)

interface SpeechEngine {
    /** Speaks [first] and everything [next] yields, suspending until done; cancel to stop. */
    suspend fun speak(
        first: Utterance,
        settings: TtsSettings,
        language: String?,
        next: suspend (Utterance) -> Utterance?,
        onStart: (Utterance) -> Unit,
    )
}

class TtsException(message: String) : Exception(message)

data class SystemVoice(
    val name: String,
    val locale: Locale,
    val requiresNetwork: Boolean,
    val quality: Int,
)

/** The platform's own voices: Google TTS on Android, the OS speech synthesizer on desktop. */
interface SystemSpeechEngine : SpeechEngine {
    val name: String
    val description: String
    val supportsPitch: Boolean

    suspend fun voices(): List<SystemVoice>

    /** Opens the platform's voice settings; null when there are none to open. */
    val openSettings: (() -> Unit)?
}
