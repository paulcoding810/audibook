package com.paulcoding.audibook.core.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.paulcoding.audibook.BuildConfig
import com.paulcoding.audibook.core.data.db.BookDatabase
import com.paulcoding.audibook.core.data.db.BookDatabaseConstructor
import com.paulcoding.audibook.core.data.db.MIGRATION_1_2
import com.paulcoding.audibook.core.platform.AppDirs
import com.paulcoding.audibook.core.platform.AppInfo
import com.paulcoding.audibook.core.playback.AndroidAudioFocus
import com.paulcoding.audibook.core.playback.AudioFocusFactory
import com.paulcoding.audibook.core.playback.ShakeDetector
import com.paulcoding.audibook.core.playback.ShakeSensor
import com.paulcoding.audibook.core.tts.AndroidSherpaLoader
import com.paulcoding.audibook.core.tts.AudioTrackOutput
import com.paulcoding.audibook.core.tts.PcmOutputFactory
import com.paulcoding.audibook.core.tts.SherpaLoader
import com.paulcoding.audibook.core.tts.SystemSpeechEngine
import com.paulcoding.audibook.core.tts.SystemTtsEngine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidModule = module {
    single { AppDirs(androidContext().filesDir, androidContext().cacheDir) }
    single {
        AppInfo(
            versionName = BuildConfig.VERSION_NAME,
            isDebug = BuildConfig.DEBUG,
            autoBackupNote = "Reading progress and settings are also backed up automatically with your Google account. Voice models aren't included.",
        )
    }
    single {
        Room.databaseBuilder(androidContext(), "audibook.db") { BookDatabaseConstructor.initialize() }
            .addMigrations(MIGRATION_1_2)
            .build()
    }
    // Same file the old preferencesDataStore("settings") delegate used.
    single { PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile("settings") } }
    single<SystemSpeechEngine> { SystemTtsEngine(androidContext()) }
    single<SherpaLoader> { AndroidSherpaLoader }
    single<PcmOutputFactory> { PcmOutputFactory(::AudioTrackOutput) }
    single<AudioFocusFactory> { AudioFocusFactory { onPause, onResume -> AndroidAudioFocus(androidContext(), onPause, onResume) } }
    single<ShakeSensor> { ShakeDetector(androidContext()) }
}
