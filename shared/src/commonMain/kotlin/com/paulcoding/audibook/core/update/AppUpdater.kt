package com.paulcoding.audibook.core.update

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** [url] is what [AppUpdater.downloadAndInstall] fetches or opens: the APK on Android, the release page on desktop. */
data class AppRelease(val version: String, val notes: String, val url: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float?) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Platform-specific: the GitHub Android build updates itself, the Play build defers to Google Play, desktop opens the release page. */
interface AppUpdater {
    val state: StateFlow<UpdateState>
    /** One-off hints for the user, e.g. that installs from this app must be allowed first. */
    val messages: SharedFlow<String>
    val canSelfUpdate: Boolean
    val installLabel: String get() = "Download & install"
    fun check()
    fun downloadAndInstall()
    fun dismiss()
}
