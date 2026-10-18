package com.paulcoding.audibook.core.update

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class AppRelease(val version: String, val notes: String, val apkUrl: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float?) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Flavor-specific: the GitHub build updates itself, the Play build defers to Google Play. */
interface AppUpdater {
    val state: StateFlow<UpdateState>
    /** One-off hints for the user, e.g. that installs from this app must be allowed first. */
    val messages: SharedFlow<String>
    val canSelfUpdate: Boolean
    fun check()
    fun downloadAndInstall()
    fun dismiss()
}
