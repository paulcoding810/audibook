package com.paulcoding.audibook.core.tts

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import kotlin.coroutines.coroutineContext

enum class ModelType { VITS, KOKORO, KITTEN }

data class TtsModel(
    val id: String,
    val name: String,
    val language: String,
    val sizeMb: Int,
    val type: ModelType,
    val speakers: Int = 1,
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()

    val catalog = listOf(
        TtsModel("vits-piper-en_US-amy-low-int8", "Piper Amy (US)", "en-US", 21, ModelType.VITS),
        TtsModel("vits-piper-en_US-lessac-medium-int8", "Piper Lessac (US)", "en-US", 20, ModelType.VITS),
        TtsModel("vits-piper-en_US-ryan-medium-int8", "Piper Ryan (US)", "en-US", 21, ModelType.VITS),
        TtsModel("vits-piper-en_GB-alba-medium-int8", "Piper Alba (GB)", "en-GB", 21, ModelType.VITS),
        TtsModel("vits-piper-en_US-libritts_r-medium-int8", "Piper LibriTTS-R (904 voices)", "en-US", 23, ModelType.VITS, speakers = 904),
        TtsModel("vits-piper-vi_VN-vais1000-medium-int8", "Piper VAIS1000 (Vietnamese)", "vi-VN", 21, ModelType.VITS),
        TtsModel("kitten-nano-en-v0_8-int8", "Kitten Nano (8 voices)", "en-US", 31, ModelType.KITTEN, speakers = 8),
        TtsModel("kokoro-int8-en-v0_19", "Kokoro v0.19 (11 voices)", "en-US", 103, ModelType.KOKORO, speakers = 11),
        TtsModel("kokoro-int8-multi-lang-v1_1", "Kokoro v1.1 multilingual (103 voices)", "multi", 147, ModelType.KOKORO, speakers = 103),
    )

    private val _states = MutableStateFlow(catalog.associate { it.id to initialState(it) })
    val states: StateFlow<Map<String, ModelState>> = _states.asStateFlow()

    fun model(id: String): TtsModel? = catalog.firstOrNull { it.id == id }

    fun isInstalled(id: String) = File(dir(id), MARKER).exists()

    fun download(model: TtsModel) {
        if (jobs[model.id]?.isActive == true) return
        jobs[model.id] = scope.launch {
            val archive = File(root, "${model.id}.tar.bz2.part")
            try {
                setState(model.id, ModelState.Downloading(null))
                fetch(model.url, archive) { setState(model.id, ModelState.Downloading(it)) }
                setState(model.id, ModelState.Extracting)
                extract(archive, dir(model.id))
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

    private fun initialState(model: TtsModel): ModelState =
        if (isInstalled(model.id)) ModelState.Installed else ModelState.NotInstalled

    private fun setState(id: String, state: ModelState) = _states.update { it + (id to state) }

    private suspend fun fetch(url: String, target: File, onProgress: (Float?) -> Unit) {
        client.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) throw TtsException("Download failed: HTTP ${response.status.value}")
            val total = response.contentLength()
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(64 * 1024)
            var read = 0L
            var lastReported = -1
            target.outputStream().use { out ->
                while (true) {
                    coroutineContext.ensureActive()
                    val n = channel.readAvailable(buffer, 0, buffer.size)
                    if (n == -1) break
                    out.write(buffer, 0, n)
                    read += n
                    if (total != null && total > 0) {
                        val percent = (read * 100 / total).toInt()
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress(read.toFloat() / total)
                        }
                    }
                }
            }
        }
    }

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
    }
}
