package com.paulcoding.audibook.core.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@Composable
actual fun rememberOpenFile(mimeTypes: List<String>, extensions: List<String>, onPicked: (open: () -> InputStream) -> Unit): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onPicked { resolver.openInputStream(it) ?: throw IOException("Can't open the chosen file") } }
    }
    return { launcher.launch(mimeTypes.toTypedArray()) }
}

@Composable
actual fun rememberSaveFile(onPicked: (open: () -> OutputStream) -> Unit): (suggestedName: String) -> Unit {
    val resolver = LocalContext.current.contentResolver
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { onPicked { resolver.openOutputStream(it, "wt") ?: throw IOException("Can't write to the chosen file") } }
    }
    return { launcher.launch(it) }
}

@Composable
actual fun platformColorScheme(darkTheme: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}
