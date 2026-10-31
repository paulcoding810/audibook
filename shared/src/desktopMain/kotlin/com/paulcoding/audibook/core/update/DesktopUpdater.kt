package com.paulcoding.audibook.core.update

import com.paulcoding.audibook.core.platform.AppInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.awt.Desktop
import java.net.URI

/** Checks GitHub's latest published release and opens its page to download the desktop installer. */
class DesktopUpdater(
    private val client: HttpClient,
    private val info: AppInfo,
    private val repo: String,
) : AppUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val messages: SharedFlow<String> = _messages.asSharedFlow()
    override val canSelfUpdate = true
    override val installLabel = "Open download page"
    private var job: Job? = null

    override fun check() {
        if (job?.isActive == true) return
        _state.value = UpdateState.Checking
        job = scope.launch {
            _state.value = try {
                latestRelease()?.takeIf { isNewer(it.version, info.versionName) }
                    ?.let { UpdateState.Available(it) }
                    ?: UpdateState.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed("Couldn't check for updates: ${e.message ?: "network error"}")
            }
        }
    }

    override fun downloadAndInstall() {
        val release = (_state.value as? UpdateState.Available)?.release ?: return
        _state.value = UpdateState.Idle
        runCatching { Desktop.getDesktop().browse(URI(release.url)) }
            .onFailure { _messages.tryEmit("Open ${release.url} to download the update") }
    }

    override fun dismiss() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }

    private suspend fun latestRelease(): AppRelease? {
        val response = client.get("https://api.github.com/repos/$repo/releases/latest") {
            header("Accept", "application/vnd.github+json")
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
        val release: GitHubRelease = response.body()
        return AppRelease(
            version = release.tagName.removePrefix("v"),
            notes = plainReleaseNotes(release.body.orEmpty()),
            url = release.htmlUrl,
        )
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        val body: String? = null,
    )
}
