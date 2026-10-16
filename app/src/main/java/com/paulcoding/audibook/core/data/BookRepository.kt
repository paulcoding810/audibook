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
import java.util.UUID

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

    suspend fun import(uri: Uri): BookEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val file = File(booksDir, "$id.epub")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: error("Cannot open file")
            val book = EpubParser(file).use { parser ->
                val epub = parser.book
                require(epub.chapters.isNotEmpty()) { "This EPUB has no readable chapters" }
                val cover = epub.coverHref?.let { href ->
                    parser.readBytes(href)?.let { bytes ->
                        File(coversDir, "$id.${href.substringAfterLast('.', "img")}").apply { writeBytes(bytes) }
                    }
                }
                BookEntity(
                    id = id,
                    title = epub.title,
                    author = epub.author,
                    language = epub.language,
                    coverPath = cover?.absolutePath,
                    filePath = file.absolutePath,
                    chapterCount = epub.chapters.size,
                    addedAt = System.currentTimeMillis(),
                )
            }
            dao.insert(book)
            book
        } catch (e: Exception) {
            file.delete()
            throw e
        }
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
