package com.paulcoding.audibook.core.data

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.paulcoding.audibook.core.data.db.BookDao
import com.paulcoding.audibook.core.data.db.BookEntity
import com.paulcoding.audibook.core.epub.ChapterContent
import com.paulcoding.audibook.core.epub.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

sealed interface ImportOutcome {
    val book: BookEntity

    data class Added(override val book: BookEntity) : ImportOutcome
    data class AlreadyInLibrary(override val book: BookEntity) : ImportOutcome
    /** A restored book without its file got the matching EPUB back, keeping its progress. */
    data class Reattached(override val book: BookEntity) : ImportOutcome
}

val BookEntity.hasFile: Boolean get() = File(filePath).exists()

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

class BookRepository(
    private val context: Context,
    private val dao: BookDao,
) {
    private val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val mutex = Mutex()
    private var openParser: Pair<String, EpubParser>? = null
    private val chapterCache = LruCache<String, ChapterContent>(8)

    fun observeBooks(): Flow<List<BookEntity>> = dao.observeAll()

    suspend fun getBook(id: String): BookEntity? = dao.get(id)

    /** Most recently read book whose EPUB is still on this device. */
    suspend fun lastReadBookId(): String? = withContext(Dispatchers.IO) {
        dao.recentlyRead().firstOrNull { it.hasFile }?.id
    }

    suspend fun allBooks(): List<BookEntity> = dao.all()

    suspend fun import(uri: Uri): ImportOutcome = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("import", ".epub", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            } ?: error("Cannot open file")
            importFile(temp)
        } finally {
            temp.delete()
        }
    }

    /** Copies the EPUB at [source] into the library, reattaching it to a restored entry with the same fingerprint. */
    suspend fun importFile(source: File): ImportOutcome = withContext(Dispatchers.IO) {
        val fingerprint = sha256(source)
        val existing = dao.findByFingerprint(fingerprint)
        if (existing != null && existing.hasFile) return@withContext ImportOutcome.AlreadyInLibrary(existing)
        val id = existing?.id ?: UUID.randomUUID().toString()
        val target = File(booksDir, "$id.epub")
        source.copyTo(target, overwrite = true)
        try {
            EpubParser(target).use { parser ->
                val epub = parser.book
                require(epub.chapters.isNotEmpty()) { "This EPUB has no readable chapters" }
                val cover = epub.coverHref?.let { href ->
                    parser.readBytes(href)?.let { bytes ->
                        File(coversDir, "$id.${href.substringAfterLast('.', "img")}").apply { writeBytes(bytes) }
                    }
                }
                if (existing != null) {
                    dao.reattach(id, target.absolutePath, cover?.absolutePath, epub.chapters.size)
                    chapterCache.evictAll()
                    ImportOutcome.Reattached(dao.get(id)!!)
                } else {
                    val book = BookEntity(
                        id = id,
                        title = epub.title,
                        author = epub.author,
                        language = epub.language,
                        coverPath = cover?.absolutePath,
                        filePath = target.absolutePath,
                        chapterCount = epub.chapters.size,
                        addedAt = System.currentTimeMillis(),
                        fingerprint = fingerprint,
                    )
                    dao.insert(book)
                    ImportOutcome.Added(book)
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    /** Applies progress from a backup unless this device has read the book more recently. */
    suspend fun restoreProgress(id: String, chapter: Int, sentence: Int, lastReadAt: Long?) {
        if (lastReadAt == null) return
        val current = dao.get(id) ?: return
        if ((current.lastReadAt ?: 0) < lastReadAt) dao.updateProgress(id, chapter, sentence, lastReadAt)
    }

    /** Adds a library entry for a backed-up book whose EPUB wasn't included; the user can re-add the file later. */
    suspend fun addPlaceholder(fingerprint: String, title: String, author: String?, language: String?, chapterCount: Int, addedAt: Long): BookEntity {
        val id = UUID.randomUUID().toString()
        val book = BookEntity(
            id = id,
            title = title,
            author = author,
            language = language,
            coverPath = null,
            filePath = File(booksDir, "$id.epub").absolutePath,
            chapterCount = chapterCount,
            addedAt = addedAt,
            fingerprint = fingerprint,
        )
        dao.insert(book)
        return book
    }

    suspend fun findByFingerprint(fingerprint: String): BookEntity? = dao.findByFingerprint(fingerprint)

    /** Fills in fingerprints for books added before they existed. */
    suspend fun backfillFingerprints() = withContext(Dispatchers.IO) {
        dao.all().filter { it.fingerprint.isEmpty() && it.hasFile }.forEach { dao.setFingerprint(it.id, sha256(File(it.filePath))) }
    }

    suspend fun delete(book: BookEntity) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (openParser?.first == book.id) {
                openParser?.second?.close()
                openParser = null
            }
        }
        dao.delete(book.id)
        File(book.filePath).delete()
        book.coverPath?.let { File(it).delete() }
    }

    suspend fun saveProgress(id: String, chapter: Int, sentence: Int) =
        dao.updateProgress(id, chapter, sentence, System.currentTimeMillis())

    suspend fun chapters(book: BookEntity) = parser(book).book.chapters

    suspend fun chapter(book: BookEntity, index: Int): ChapterContent {
        val key = "${book.id}:$index"
        chapterCache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val parser = parser(book)
            parser.readChapter(parser.book.chapters[index]).also { chapterCache.put(key, it) }
        }
    }

    private suspend fun parser(book: BookEntity): EpubParser = withContext(Dispatchers.IO) {
        mutex.withLock {
            openParser?.takeIf { it.first == book.id }?.second
                ?: EpubParser(File(book.filePath)).also { parser ->
                    openParser?.second?.close()
                    parser.book
                    openParser = book.id to parser
                }
        }
    }
}
