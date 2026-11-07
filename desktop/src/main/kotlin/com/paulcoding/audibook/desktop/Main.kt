package com.paulcoding.audibook.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.paulcoding.audibook.AudiBookApp
import com.paulcoding.audibook.core.data.ExternalImports
import com.paulcoding.audibook.core.di.desktopAppDirs
import com.paulcoding.audibook.core.di.desktopModule
import com.paulcoding.audibook.core.di.sharedModules
import com.paulcoding.audibook.core.platform.AppInfo
import org.koin.core.context.startKoin
import java.awt.Desktop
import java.io.File

fun main(args: Array<String>) {
    val info = AppInfo(
        versionName = System.getProperty("audibook.version") ?: "dev",
        isDebug = System.getProperty("audibook.debug") == "true",
    )
    val koin = startKoin {
        modules(sharedModules + desktopModule(desktopAppDirs(), info, githubRepo = "paulcoding810/audibook"))
    }.koin
    val imports = koin.get<ExternalImports>()
    // EPUBs passed on the command line (Windows/Linux file associations) or opened from Finder (macOS).
    val files = args.map(::File).filter { it.isFile }
    files.forEach { file -> imports.submit { file.inputStream() } }
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE)) {
        Desktop.getDesktop().setOpenFileHandler { event -> event.files.forEach { file -> imports.submit { file.inputStream() } } }
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "AudiBook",
            state = rememberWindowState(width = 480.dp, height = 860.dp),
        ) {
            AudiBookApp(openLastRead = files.isEmpty())
        }
    }
}
