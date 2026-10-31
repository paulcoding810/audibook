package com.paulcoding.audibook.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Streams [url] into [target], reporting progress (0..1) at most once per percent. */
suspend fun HttpClient.downloadTo(url: String, target: File, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
    prepareGet(url).execute { response ->
        if (!response.status.isSuccess()) throw IOException("Download failed: HTTP ${response.status.value}")
        val total = response.contentLength()
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(64 * 1024)
        var read = 0L
        var lastReported = -1
        target.parentFile?.mkdirs()
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
