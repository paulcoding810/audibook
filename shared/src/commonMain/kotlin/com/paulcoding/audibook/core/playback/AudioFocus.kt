package com.paulcoding.audibook.core.playback

/** Exclusive use of audio output: [request] before playing, [abandon] after. */
interface AudioFocus {
    fun request(): Boolean
    fun abandon()
}

fun interface AudioFocusFactory {
    /** [onPause] fires when another app or unplugged headphones interrupt; transient losses later call [onResume]. */
    fun create(onPause: (transient: Boolean) -> Unit, onResume: () -> Unit): AudioFocus
}

object NoAudioFocus : AudioFocus {
    override fun request() = true
    override fun abandon() = Unit
}
