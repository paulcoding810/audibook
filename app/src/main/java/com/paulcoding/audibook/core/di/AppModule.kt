package com.paulcoding.audibook.core.di

import androidx.room.Room
import com.paulcoding.audibook.core.backup.BackupManager
import com.paulcoding.audibook.core.data.BookRepository
import com.paulcoding.audibook.core.data.SettingsRepository
import com.paulcoding.audibook.core.data.db.BookDatabase
import com.paulcoding.audibook.core.playback.ReadAloudController
import com.paulcoding.audibook.core.tts.ModelManager
import com.paulcoding.audibook.core.tts.SherpaTtsEngine
import com.paulcoding.audibook.core.tts.SystemTtsEngine
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val dataModule = module {
    single {
        Room.databaseBuilder(androidContext(), BookDatabase::class.java, "audibook.db")
            .addMigrations(BookDatabase.MIGRATION_1_2)
            .build()
    }
    single { get<BookDatabase>().bookDao() }
    singleOf(::BookRepository)
    singleOf(::SettingsRepository)
    singleOf(::BackupManager)
}

val ttsModule = module {
    singleOf(::ModelManager)
    singleOf(::SystemTtsEngine)
    singleOf(::SherpaTtsEngine)
    singleOf(::ReadAloudController)
}
