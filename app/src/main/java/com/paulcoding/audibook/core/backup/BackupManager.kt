package com.paulcoding.audibook.core.backup

import android.content.Context
import android.net.Uri
import com.paulcoding.audibook.core.data.BookRepository
import com.paulcoding.audibook.core.data.ImportOutcome
import com.paulcoding.audibook.core.data.SettingsBackup
import com.paulcoding.audibook.core.data.SettingsRepository
import com.paulcoding.audibook.core.data.hasFile
import com.paulcoding.audibook.core.tts.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupManifest(
    val version: Int = VERSION,
    val exportedAt: Long,
    val books: List<BackupBook>,
    val settings: SettingsBackup,
    val models: List<String> = emptyList(),
) {
    companion object {
        const val VERSION = 1
    }
}

@Serializable
data class BackupBook(
    val fingerprint: String,
    val title: String,
    val author: String? = null,
    val language: String? = null,
    val chapterCount: Int = 0,
    val chapterIndex: Int = 0,
    val sentenceIndex: Int = 0,
    val lastReadAt: Long? = null,
    val addedAt: Long = 0,
    /** Path of the EPUB inside the archive, or null if the file wasn't on the exporting device. */
    val file: String? = null,
)

data class ImportSummary(
    val added: Int,
    val restored: Int,
    val unchanged: Int,
    val missingModels: List<String>,
)

/**
 * A `.audibook` backup is a zip with `manifest.json` (books, progress, settings, installed model ids)
 * and `books/<fingerprint>.epub`. Voice models are left out; they can be downloaded again.
 */
class BackupManager(
    private val context: Context,
    private val books: BookRepository,
    private val settings: SettingsRepository,
    private val models: ModelManager,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        books.backfillFingerprints()
        val library = books.allBooks().filter { it.fingerprint.isNotEmpty() }.distinctBy { it.fingerprint }
        val manifest = BackupManifest(
            exportedAt = System.currentTimeMillis(),
            books = library.map { book ->
                BackupBook(
                    fingerprint = book.fingerprint,
                    title = book.title,
                    author = book.author,
                    language = book.language,
                    chapterCount = book.chapterCount,
                    chapterIndex = book.chapterIndex,
                    sentenceIndex = book.sentenceIndex,
                    lastReadAt = book.lastReadAt,
                    addedAt = book.addedAt,
                    file = if (book.hasFile) "books/${book.fingerprint}.epub" else null,
                )
            },
            settings = settings.exportSettings(),
            models = models.installedIds(),
        )
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Can't write to the chosen file")
        ZipOutputStream(output.buffered()).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
            manifest.books.forEach { entry ->
                val path = entry.file ?: return@forEach
                val book = library.first { it.fingerprint == entry.fingerprint }
                zip.putNextEntry(ZipEntry(path))
                File(book.filePath).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        library.size
    }

    suspend fun import(uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "backup-import").apply { deleteRecursively(); mkdirs() }
        try {
            var manifest: BackupManifest? = null
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Can't open the chosen file")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        name == MANIFEST -> manifest = json.decodeFromString(BackupManifest.serializer(), zip.readBytes().decodeToString())
                        EPUB_ENTRY.matches(name) -> File(workDir, name.substringAfter('/')).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            val backup = manifest ?: throw IOException("This isn't an AudiBook backup")
            if (backup.version > BackupManifest.VERSION) throw IOException("This backup was made by a newer version of AudiBook")
            restore(backup, workDir)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private suspend fun restore(backup: BackupManifest, workDir: File): ImportSummary {
        var added = 0
        var restored = 0
        var unchanged = 0
        backup.books.forEach { entry ->
            val epub = entry.file?.let { File(workDir, it.substringAfter('/')) }?.takeIf { it.exists() }
            if (entry.fingerprint.isEmpty()) return@forEach
            val book = if (epub != null) {
                val outcome = books.importFile(epub)
                when (outcome) {
                    is ImportOutcome.Added -> added++
                    is ImportOutcome.Reattached -> restored++
                    is ImportOutcome.AlreadyInLibrary -> unchanged++
                }
                outcome.book
            } else {
                books.findByFingerprint(entry.fingerprint)?.also { unchanged++ }
                    ?: books.addPlaceholder(entry.fingerprint, entry.title, entry.author, entry.language, entry.chapterCount, entry.addedAt)
                        .also { added++ }
            }
            books.restoreProgress(book.id, entry.chapterIndex, entry.sentenceIndex, entry.lastReadAt)
        }
        settings.importSettings(backup.settings)
        val missingModels = backup.models.filterNot(models::isInstalled).map { models.model(it)?.name ?: it }
        return ImportSummary(added, restored, unchanged, missingModels)
    }

    private companion object {
        const val MANIFEST = "manifest.json"
        val EPUB_ENTRY = Regex("books/[0-9a-f]{64}\\.epub")
    }
}
