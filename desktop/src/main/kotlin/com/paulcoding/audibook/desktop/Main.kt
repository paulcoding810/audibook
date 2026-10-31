package com.paulcoding.audibook.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.paulcoding.audibook.AudiBookApp
import com.paulcoding.audibook.core.di.desktopAppDirs
import com.paulcoding.audibook.core.di.desktopModule
import com.paulcoding.audibook.core.di.sharedModules
import com.paulcoding.audibook.core.platform.AppInfo
import org.koin.core.context.startKoin

fun main() {
    val info = AppInfo(
        versionName = System.getProperty("audibook.version") ?: "dev",
        isDebug = System.getProperty("audibook.debug") == "true",
    )
    startKoin {
        modules(sharedModules + desktopModule(desktopAppDirs(), info, githubRepo = "paulcoding810/audibook"))
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "AudiBook",
            state = rememberWindowState(width = 480.dp, height = 860.dp),
        ) {
            AudiBookApp(openLastRead = true)
        }
    }
}
