package com.paulcoding.audibook.core.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/** Audio focus + headphone-unplug handling for spoken audio. */
class AudioFocus(
    private val context: Context,
    private val onPause: (transient: Boolean) -> Unit,
    private val onResume: () -> Unit,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var resumeOnGain = false
    private var noisyRegistered = false

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener({ change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeOnGain = false
                    onPause(false)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    resumeOnGain = true
                    onPause(true)
                }
                AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnGain) {
                    resumeOnGain = false
                    onResume()
                }
            }
        }, Handler(Looper.getMainLooper()))
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onPause(false)
        }
    }

    fun request(): Boolean {
        resumeOnGain = false
        val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted && !noisyRegistered) {
            ContextCompat.registerReceiver(
                context, noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyRegistered = true
        }
        return granted
    }

    fun abandon() {
        resumeOnGain = false
        audioManager.abandonAudioFocusRequest(request)
        if (noisyRegistered) {
            context.unregisterReceiver(noisyReceiver)
            noisyRegistered = false
        }
    }
}
