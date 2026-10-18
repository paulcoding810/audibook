package com.paulcoding.audibook.core.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.paulcoding.audibook.BuildConfig
import com.paulcoding.audibook.core.network.downloadTo
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
import java.io.File

/** Checks GitHub's latest published release and installs the APK matching this device's ABI. */
class GitHubAppUpdater(
    private val context: Context,
    private val client: HttpClient,
) : AppUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val messages: SharedFlow<String> = _messages.asSharedFlow()
    override val canSelfUpdate = true
    private var job: Job? = null

    override fun check() {
        if (job?.isActive == true) return
        _state.value = UpdateState.Checking
        job = scope.launch {
            _state.value = try {
                latestRelease()?.takeIf { isNewer(it.version, BuildConfig.VERSION_NAME) }
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
        val release = when (val current = _state.value) {
            is UpdateState.Available -> current.release
            else -> return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            openInstallPermissionSettings()
            _messages.tryEmit("Allow installs from AudiBook, then tap Download & install again")
            return
        }
        job = scope.launch {
            _state.value = UpdateState.Downloading(release, null)
            try {
                val apk = File(context.cacheDir, "updates/AudiBook-update.apk")
                client.downloadTo(release.apkUrl, apk) { _state.value = UpdateState.Downloading(release, it) }
                install(apk)
                _state.value = UpdateState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("Download failed: ${e.message ?: "network error"}")
            }
        }
    }

    override fun dismiss() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }

    private suspend fun latestRelease(): AppRelease? {
        val response = client.get("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest") {
            header("Accept", "application/vnd.github+json")
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
        val release: GitHubRelease = response.body()
        val apk = pickApk(release.assets.map { it.name }, Build.SUPPORTED_ABIS.toList()) ?: return null
        return AppRelease(
            version = release.tagName.removePrefix("v"),
            notes = plainReleaseNotes(release.body.orEmpty()),
            apkUrl = release.assets.first { it.name == apk }.downloadUrl,
        )
    }

    private fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
    )
}
