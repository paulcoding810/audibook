package com.paulcoding.audibook.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulcoding.audibook.core.tts.TtsModel
import org.koin.compose.viewmodel.koinViewModel

private data class Library(val name: String, val license: String, val url: String)

/** Third-party code shipped in AudiBook. Add an entry when adding a dependency. */
private val libraries = listOf(
    Library("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache-2.0", "https://github.com/JetBrains/kotlin"),
    Library("Jetpack Compose and Compose Multiplatform", "Apache-2.0", "https://github.com/JetBrains/compose-multiplatform"),
    Library("AndroidX (Room, DataStore, Lifecycle, Navigation, Activity, Core)", "Apache-2.0", "https://developer.android.com/jetpack/androidx"),
    Library("AndroidX Media3", "Apache-2.0", "https://github.com/androidx/media"),
    Library("Koin", "Apache-2.0", "https://github.com/InsertKoinIO/koin"),
    Library("Ktor", "Apache-2.0", "https://github.com/ktorio/ktor"),
    Library("Coil", "Apache-2.0", "https://github.com/coil-kt/coil"),
    Library("jsoup", "MIT", "https://github.com/jhy/jsoup"),
    Library("Apache Commons Compress", "Apache-2.0", "https://commons.apache.org/proper/commons-compress/"),
    Library("sherpa-onnx", "Apache-2.0", "https://github.com/k2-fsa/sherpa-onnx"),
    Library("ONNX Runtime (inside sherpa-onnx)", "MIT", "https://github.com/microsoft/onnxruntime"),
    Library("SLF4J", "MIT", "https://www.slf4j.org"),
    Library("SQLite (desktop)", "Public domain", "https://sqlite.org/copyright.html"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit, viewModel: LicensesViewModel = koinViewModel()) {
    val voices by viewModel.installedVoices.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var shownVoice by remember { mutableStateOf<TtsModel?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Licenses") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item { Header("AudiBook") }
            item {
                ListItem(
                    headlineContent = { Text("GNU General Public License v3.0") },
                    supportingContent = { Text("Free software: you may use, study, share and change it under the GPL. Tap for the source code.") },
                    modifier = Modifier.clickable { uriHandler.openUri("https://github.com/paulcoding810/audibook") },
                )
            }
            item { Header("Open-source libraries") }
            items(libraries, key = { it.name }) { library ->
                ListItem(
                    headlineContent = { Text(library.name) },
                    supportingContent = { Text(library.license) },
                    modifier = Modifier.clickable { uriHandler.openUri(library.url) },
                )
            }
            item { Header("Voices") }
            item {
                Text(
                    "On-device voices are downloaded from the sherpa-onnx project and keep their original licenses, which differ per voice. " +
                        "Some, such as Meta's MMS voices, allow only non-commercial use. Google Text-to-Speech and desktop system voices are provided by your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (voices.isEmpty()) {
                item { ListItem(headlineContent = { Text("No voices downloaded") }) }
            }
            items(voices, key = { it.id }) { voice ->
                ListItem(
                    headlineContent = { Text(voice.name) },
                    supportingContent = { Text("View license or model card") },
                    modifier = Modifier.clickable { shownVoice = voice },
                )
            }
        }
    }

    shownVoice?.let { voice ->
        val text by produceState<String?>(null, voice.id) { value = viewModel.voiceLicense(voice.id) ?: "" }
        AlertDialog(
            onDismissRequest = { shownVoice = null },
            title = { Text(voice.name) },
            text = {
                SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        when {
                            text == null -> "Loading…"
                            text!!.isBlank() -> "This voice came without a license file. See https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models"
                            else -> text!!
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { shownVoice = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}
