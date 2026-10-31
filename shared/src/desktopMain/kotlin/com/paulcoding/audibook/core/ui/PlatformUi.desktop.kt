package com.paulcoding.audibook.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@Composable
actual fun rememberOpenFile(mimeTypes: List<String>, extensions: List<String>, onPicked: (open: () -> InputStream) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onPicked)
    return remember(extensions) {
        {
            val dialog = FileDialog(null as Frame?, "Open", FileDialog.LOAD).apply {
                if (extensions.isNotEmpty()) setFilenameFilter { _, name -> extensions.any { name.endsWith(".$it", ignoreCase = true) } }
                isVisible = true
            }
            dialog.files.firstOrNull()?.let { file -> latest { file.inputStream() } }
        }
    }
}

@Composable
actual fun rememberSaveFile(onPicked: (open: () -> OutputStream) -> Unit): (suggestedName: String) -> Unit {
    val latest by rememberUpdatedState(onPicked)
    return remember {
        { suggestedName ->
            val dialog = FileDialog(null as Frame?, "Save", FileDialog.SAVE).apply {
                file = suggestedName
                isVisible = true
            }
            val name = dialog.file
            if (name != null) {
                val extension = suggestedName.substringAfterLast('.', "")
                val file = File(dialog.directory, if (extension.isEmpty() || name.endsWith(".$extension")) name else "$name.$extension")
                latest { file.outputStream() }
            }
        }
    }
}

@Composable
actual fun platformColorScheme(darkTheme: Boolean): ColorScheme? = null
