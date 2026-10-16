package com.paulcoding.audibook.core.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
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
) {
    val progress: Float get() = if (chapterCount == 0) 0f else chapterIndex.toFloat() / chapterCount
}

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY COALESCE(lastReadAt, addedAt) DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE books SET chapterIndex = :chapter, sentenceIndex = :sentence, lastReadAt = :at WHERE id = :id")
    suspend fun updateProgress(id: String, chapter: Int, sentence: Int, at: Long)
}

@Database(entities = [BookEntity::class], version = 1, exportSchema = false)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}
