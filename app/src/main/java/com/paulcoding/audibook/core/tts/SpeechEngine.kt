package com.paulcoding.audibook.core.tts

import com.paulcoding.audibook.core.data.TtsSettings

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
