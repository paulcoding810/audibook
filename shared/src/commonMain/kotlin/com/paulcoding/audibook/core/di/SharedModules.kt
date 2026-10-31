package com.paulcoding.audibook.core.di

import com.paulcoding.audibook.core.backup.BackupManager
import com.paulcoding.audibook.core.data.BookRepository
import com.paulcoding.audibook.core.data.SettingsRepository
import com.paulcoding.audibook.core.data.db.BookDatabase
import com.paulcoding.audibook.core.network.di.networkModule
import com.paulcoding.audibook.core.playback.ReadAloudController
import com.paulcoding.audibook.core.tts.ModelManager
import com.paulcoding.audibook.core.tts.SherpaTtsEngine
import com.paulcoding.audibook.viewModelModule
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Platform-independent bindings. Each platform module provides: AppDirs, AppInfo, BookDatabase,
 * DataStore<Preferences>, SystemSpeechEngine, SherpaLoader, PcmOutputFactory, AudioFocusFactory, ShakeSensor, AppUpdater.
 */
val sharedModules = listOf(
    networkModule,
    module {
        single { get<BookDatabase>().bookDao() }
        singleOf(::BookRepository)
        singleOf(::SettingsRepository)
        singleOf(::BackupManager)
        singleOf(::ModelManager)
        singleOf(::SherpaTtsEngine)
        singleOf(::ReadAloudController)
    },
    viewModelModule,
)
