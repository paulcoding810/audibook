package com.paulcoding.audibook.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import java.io.InputStream
import java.io.OutputStream

/** Returns an action that lets the user pick a file; [onPicked] receives a way to read it. */
@Composable
expect fun rememberOpenFile(mimeTypes: List<String>, extensions: List<String>, onPicked: (open: () -> InputStream) -> Unit): () -> Unit

/** Returns an action that asks where to save a file with the given name; [onPicked] receives a way to write it. */
@Composable
expect fun rememberSaveFile(onPicked: (open: () -> OutputStream) -> Unit): (suggestedName: String) -> Unit

/** The platform's own color scheme (Material You on Android 12+), or null for the app's. */
@Composable
expect fun platformColorScheme(darkTheme: Boolean): ColorScheme?
