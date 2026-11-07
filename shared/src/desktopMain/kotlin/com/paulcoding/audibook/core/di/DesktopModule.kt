package com.paulcoding.audibook.core.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.paulcoding.audibook.core.data.db.BookDatabase
import com.paulcoding.audibook.core.data.db.BookDatabaseConstructor
import com.paulcoding.audibook.core.platform.AppDirs
import com.paulcoding.audibook.core.platform.AppInfo
import com.paulcoding.audibook.core.playback.AudioFocusFactory
import com.paulcoding.audibook.core.playback.NoAudioFocus
import com.paulcoding.audibook.core.playback.NoShakeSensor
import com.paulcoding.audibook.core.playback.ShakeSensor
import com.paulcoding.audibook.core.tts.DesktopSherpaLoader
import com.paulcoding.audibook.core.tts.DesktopSystemTtsEngine
import com.paulcoding.audibook.core.tts.JavaSoundOutput
import com.paulcoding.audibook.core.tts.PcmOutputFactory
import com.paulcoding.audibook.core.tts.SherpaLoader
import com.paulcoding.audibook.core.tts.SystemSpeechEngine
import com.paulcoding.audibook.core.update.AppUpdater
import com.paulcoding.audibook.core.update.DesktopUpdater
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toOkioPath
import org.koin.dsl.module
import java.io.File

/** Per-user data folder: ~/Library/Application Support/AudiBook, %APPDATA%\AudiBook, or ~/.local/share/audibook (or -Daudibook.dataDir). */
fun desktopAppDirs(): AppDirs {
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()
    val override = System.getProperty("audibook.dataDir")
    val files = when {
        override != null -> File(override)
        os.contains("mac") -> File(home, "Library/Application Support/AudiBook")
        os.contains("win") -> File(System.getenv("APPDATA") ?: home, "AudiBook")
        else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "audibook")
    }
    return AppDirs(files.apply { mkdirs() }, File(files, "cache").apply { mkdirs() })
}

fun desktopModule(dirs: AppDirs, info: AppInfo, githubRepo: String) = module {
    single { dirs }
    single { info }
    single<BookDatabase> {
        Room.databaseBuilder<BookDatabase>(File(dirs.files, "audibook.db").absolutePath) { BookDatabaseConstructor.initialize() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
    single { PreferenceDataStoreFactory.createWithPath { File(dirs.files, "settings.preferences_pb").toOkioPath() } }
    single<PcmOutputFactory> { PcmOutputFactory(::JavaSoundOutput) }
    single<SystemSpeechEngine> { DesktopSystemTtsEngine(dirs, get()) }
    single<SherpaLoader> { DesktopSherpaLoader }
    single<AudioFocusFactory> { AudioFocusFactory { _, _ -> NoAudioFocus } }
    single<ShakeSensor> { NoShakeSensor }
    single<AppUpdater> { DesktopUpdater(get(), info, githubRepo) }
}
