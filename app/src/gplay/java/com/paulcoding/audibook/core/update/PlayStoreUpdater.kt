package com.paulcoding.audibook.core.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Play builds may not update themselves; this just opens the Play Store listing. */
class PlayStoreUpdater(private val context: Context) : AppUpdater {
    override val state: StateFlow<UpdateState> = MutableStateFlow<UpdateState>(UpdateState.Idle).asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val messages: SharedFlow<String> = _messages.asSharedFlow()
    override val canSelfUpdate = false

    override fun check() {
        val id = context.packageName
        val opened = open("market://details?id=$id") || open("https://play.google.com/store/apps/details?id=$id")
        if (!opened) _messages.tryEmit("Google Play isn't available on this device")
    }

    override fun downloadAndInstall() = Unit

    override fun dismiss() = Unit

    private fun open(url: String) = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
