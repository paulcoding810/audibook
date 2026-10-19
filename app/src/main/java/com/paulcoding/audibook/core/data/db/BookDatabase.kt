package com.paulcoding.audibook.core.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val language: String?,
    val coverPath: String?,
    val filePath: String,
    val chapterCount: Int,
    val addedAt: Long,
    val chapterIndex: Int = 0,
    val sentenceIndex: Int = 0,
    val lastReadAt: Long? = null,
    /** SHA-256 of the EPUB file; identifies the same book across reinstalls, backups and devices. */
    val fingerprint: String = "",
) {
    val progress: Float get() = if (chapterCount == 0) 0f else chapterIndex.toFloat() / chapterCount
}

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY COALESCE(lastReadAt, addedAt) DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE lastReadAt IS NOT NULL ORDER BY lastReadAt DESC")
    suspend fun recentlyRead(): List<BookEntity>

    @Query("SELECT * FROM books")
    suspend fun all(): List<BookEntity>

    @Query("SELECT * FROM books WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): BookEntity?

    @Query("UPDATE books SET fingerprint = :fingerprint WHERE id = :id")
    suspend fun setFingerprint(id: String, fingerprint: String)

    @Query("UPDATE books SET filePath = :filePath, coverPath = :coverPath, chapterCount = :chapterCount WHERE id = :id")
    suspend fun reattach(id: String, filePath: String, coverPath: String?, chapterCount: Int)

    @Insert
    suspend fun insert(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE books SET chapterIndex = :chapter, sentenceIndex = :sentence, lastReadAt = :at WHERE id = :id")
    suspend fun updateProgress(id: String, chapter: Int, sentence: Int, at: Long)
}

@Database(entities = [BookEntity::class], version = 2, exportSchema = false)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN fingerprint TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
