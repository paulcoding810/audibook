package com.paulcoding.audibook.core.tts

import android.content.Context
import com.paulcoding.audibook.core.network.downloadTo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

enum class ModelType { VITS, KOKORO, KITTEN }

/** How demanding a voice is to synthesize: light voices keep up on any phone, heavy ones need a fast CPU and more battery. */
enum class VoiceWeight { LIGHT, HEAVY }

@Serializable
data class TtsModel(
    val id: String,
    val name: String,
    val language: String,
    val sizeMb: Int,
    val type: ModelType,
    val speakers: Int = 1,
    val weight: VoiceWeight = if (type == ModelType.KOKORO) VoiceWeight.HEAVY else VoiceWeight.LIGHT,
) {
    val url get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$id.tar.bz2"
}

sealed interface ModelState {
    data object NotInstalled : ModelState
    data class Downloading(val progress: Float?) : ModelState
    data object Extracting : ModelState
    data object Installed : ModelState
    data class Failed(val message: String) : ModelState
}

/** Resolved on-disk paths of an installed model. */
data class ModelFiles(
    val model: TtsModel,
    val onnx: File,
    val tokens: File,
    val voices: File?,
    val espeakData: File?,
    val lexicons: List<File>,
    val dictDir: File?,
)

class ModelManager(context: Context, private val client: HttpClient) {
    private val root = File(context.filesDir, "models").apply { mkdirs() }
    private val catalogCache = File(context.filesDir, "voice-catalog.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val json = Json { ignoreUnknownKeys = true }

    private val _remote = MutableStateFlow(readCachedCatalog()?.models.orEmpty())
    private val _states = MutableStateFlow(installedIds().associateWith { ModelState.Installed as ModelState })
    val states: StateFlow<Map<String, ModelState>> = _states.asStateFlow()

    /** Every voice offered for download (recommended first), plus installed ones no longer listed upstream. */
    val catalog: StateFlow<List<TtsModel>> = combine(_remote, _states) { remote, states ->
        val byId = LinkedHashMap<String, TtsModel>()
        ModelCatalog.recommended.forEach { id -> (remote.firstOrNull { it.id == id } ?: ModelCatalog.parseOne(id, 0))?.let { byId[id] = it } }
        remote.forEach { byId.putIfAbsent(it.id, it) }
        states.filterValues { it == ModelState.Installed }.keys.forEach { id -> if (id !in byId) installedModel(id)?.let { byId[id] = it } }
        byId.values.toList()
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val installed: StateFlow<List<TtsModel>> = combine(_states, catalog) { states, all ->
        all.filter { states[it.id] == ModelState.Installed }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun model(id: String): TtsModel? = catalog.value.firstOrNull { it.id == id } ?: installedModel(id)

    fun isInstalled(id: String) = File(dir(id), MARKER).exists()

    fun installedIds(): List<String> = root.listFiles().orEmpty().filter { File(it, MARKER).exists() }.map { it.name }

    /** Speaker count learned when the model was first loaded (0 if unknown). */
    fun speakerCount(id: String): Int =
        model(id)?.speakers?.takeIf { it > 0 } ?: File(dir(id), SPEAKERS).takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: 0

    fun rememberSpeakerCount(id: String, count: Int) {
        runCatching { File(dir(id), SPEAKERS).writeText(count.toString()) }
    }

    /** Refreshes the downloadable voice list from the sherpa-onnx release, at most once a day unless [force]d. */
    suspend fun refreshCatalog(force: Boolean = false) = withContext(Dispatchers.IO) {
        val cached = readCachedCatalog()
        if (!force && cached != null && System.currentTimeMillis() - cached.fetchedAt < CATALOG_MAX_AGE_MS && cached.models.isNotEmpty()) {
            _remote.value = cached.models
            return@withContext
        }
        val release: GitHubRelease = client.get("$GITHUB_API/releases/tags/$RELEASE_TAG").body()
        val assets = buildList {
            for (page in 1..MAX_PAGES) {
                val batch: List<GitHubAsset> = client.get("$GITHUB_API/releases/${release.id}/assets?per_page=100&page=$page").body()
                addAll(batch.map { CatalogAsset(it.name, it.size) })
                if (batch.size < 100) break
            }
        }
        val models = ModelCatalog.parse(assets)
        if (models.isEmpty()) throw TtsException("No voices found")
        catalogCache.writeText(json.encodeToString(CachedCatalog.serializer(), CachedCatalog(System.currentTimeMillis(), models)))
        _remote.value = models
    }

    fun download(model: TtsModel) {
        if (jobs[model.id]?.isActive == true) return
        jobs[model.id] = scope.launch {
            val archive = File(root, "${model.id}.tar.bz2.part")
            try {
                setState(model.id, ModelState.Downloading(null))
                client.downloadTo(model.url, archive) { setState(model.id, ModelState.Downloading(it)) }
                setState(model.id, ModelState.Extracting)
                extract(archive, dir(model.id))
                File(dir(model.id), METADATA).writeText(json.encodeToString(TtsModel.serializer(), model))
                File(dir(model.id), MARKER).createNewFile()
                setState(model.id, ModelState.Installed)
            } catch (e: Exception) {
                dir(model.id).deleteRecursively()
                setState(model.id, if (coroutineContext[Job]?.isCancelled == true) ModelState.NotInstalled else ModelState.Failed(e.message ?: "Download failed"))
            } finally {
                archive.delete()
            }
        }
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        setState(id, ModelState.NotInstalled)
    }

    fun delete(id: String) {
        cancel(id)
        dir(id).deleteRecursively()
    }

    fun files(id: String): ModelFiles? {
        val model = model(id) ?: return null
        val dir = dir(id)
        if (!isInstalled(id)) return null
        val top = dir.listFiles().orEmpty()
        val onnx = top.filter { it.extension == "onnx" }.let { files ->
            files.firstOrNull { it.name.contains("int8") } ?: files.firstOrNull()
        } ?: return null
        return ModelFiles(
            model = model,
            onnx = onnx,
            tokens = File(dir, "tokens.txt"),
            voices = File(dir, "voices.bin").takeIf { it.exists() },
            espeakData = File(dir, "espeak-ng-data").takeIf { it.isDirectory },
            lexicons = top.filter { it.name.startsWith("lexicon") && it.extension == "txt" }.sortedBy { it.name },
            dictDir = File(dir, "dict").takeIf { it.isDirectory },
        )
    }

    private fun dir(id: String) = File(root, id)

    /** Metadata saved at install time, or parsed from the id for models installed by older versions. */
    private fun installedModel(id: String): TtsModel? {
        if (!isInstalled(id)) return null
        val saved = File(dir(id), METADATA).takeIf { it.exists() }
            ?.let { runCatching { json.decodeFromString(TtsModel.serializer(), it.readText()) }.getOrNull() }
        return saved ?: ModelCatalog.parseOne(id, 0)
    }

    private fun readCachedCatalog(): CachedCatalog? =
        catalogCache.takeIf { it.exists() }?.let { runCatching { json.decodeFromString(CachedCatalog.serializer(), it.readText()) }.getOrNull() }

    private fun setState(id: String, state: ModelState) = _states.update { it + (id to state) }

    @Serializable
    private data class CachedCatalog(val fetchedAt: Long, val models: List<TtsModel>)

    @Serializable
    private data class GitHubRelease(val id: Long)

    @Serializable
    private data class GitHubAsset(val name: String, val size: Long)

    /** Extracts the archive into [target], dropping its single top-level folder. */
    private suspend fun extract(archive: File, target: File) {
        target.deleteRecursively()
        target.mkdirs()
        val canonicalTarget = target.canonicalPath + File.separator
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
            while (true) {
                coroutineContext.ensureActive()
                val entry = tar.nextEntry ?: break
                val relative = entry.name.substringAfter('/', "")
                if (relative.isEmpty()) continue
                val out = File(target, relative)
                if (!out.canonicalPath.startsWith(canonicalTarget)) continue
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { tar.copyTo(it) }
                }
            }
        }
    }

    private companion object {
        const val MARKER = ".installed"
        const val METADATA = "model.json"
        const val SPEAKERS = ".speakers"
        const val GITHUB_API = "https://api.github.com/repos/k2-fsa/sherpa-onnx"
        const val RELEASE_TAG = "tts-models"
        const val MAX_PAGES = 15
        const val CATALOG_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
