package com.paulcoding.audibook.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulcoding.audibook.core.tts.ModelCatalog
import com.paulcoding.audibook.core.tts.ModelState
import com.paulcoding.audibook.core.tts.TtsModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onBack: () -> Unit, viewModel: ModelsViewModel = koinViewModel()) {
    val states by viewModel.states.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showLanguages by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice models") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh voice list")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Text(
                    "Models run entirely on this device with sherpa-onnx. Light voices keep up on any phone; " +
                        "heavy voices sound more natural but need a fast phone and use more battery.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("Search voices") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    FilterChip(
                        selected = language != null,
                        onClick = { showLanguages = true },
                        label = { Text(language?.let(ModelCatalog::languageLabel) ?: "All languages") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                    )
                    DropdownMenu(expanded = showLanguages, onDismissRequest = { showLanguages = false }) {
                        DropdownMenuItem(text = { Text("All languages") }, onClick = {
                            viewModel.setLanguage(null)
                            showLanguages = false
                        })
                        languages.forEach { code ->
                            DropdownMenuItem(text = { Text(ModelCatalog.languageLabel(code)) }, onClick = {
                                viewModel.setLanguage(code)
                                showLanguages = false
                            })
                        }
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
            error?.let { message ->
                item {
                    ListItem(
                        headlineContent = { Text(message, color = MaterialTheme.colorScheme.error) },
                        trailingContent = { TextButton(onClick = { viewModel.refresh() }) { Text("Retry") } },
                    )
                }
            }
            if (sections.isEmpty() && !loading) {
                item {
                    Text(
                        "No voices match. Try another language or search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            sections.forEach { section ->
                item(key = "header:${section.title}") {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(section.models, key = { "${section.title}:${it.id}" }) { model ->
                    ModelRow(model, states[model.id] ?: ModelState.NotInstalled, selected == model.id, viewModel)
                }
            }
        }
    }
}

@Composable
private fun ModelRow(model: TtsModel, state: ModelState, isSelected: Boolean, viewModel: ModelsViewModel) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.name, modifier = Modifier.weight(1f, fill = false))
                VoiceWeightBadge(model.weight, Modifier.padding(start = 8.dp))
            }
        },
        supportingContent = {
            Text(
                when (state) {
                    is ModelState.Downloading -> state.progress?.let { "Downloading ${(it * 100).toInt()}%" } ?: "Downloading…"
                    ModelState.Extracting -> "Unpacking…"
                    is ModelState.Failed -> state.message
                    else -> ModelCatalog.languageLabel(model.language) + if (model.sizeMb > 0) " · ${model.sizeMb} MB" else ""
                },
                color = if (state is ModelState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = if (isSelected) {
            { Icon(Icons.Default.CheckCircle, contentDescription = "In use", tint = MaterialTheme.colorScheme.primary) }
        } else null,
        trailingContent = { ModelActions(model, state, isSelected, viewModel) },
    )
}

@Composable
private fun ModelActions(model: TtsModel, state: ModelState, isSelected: Boolean, viewModel: ModelsViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            ModelState.NotInstalled -> IconButton(onClick = { viewModel.download(model) }) {
                Icon(Icons.Default.Download, contentDescription = "Download")
            }
            is ModelState.Failed -> IconButton(onClick = { viewModel.download(model) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Retry")
            }
            is ModelState.Downloading -> {
                val progress = state.progress
                if (progress != null) {
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(24.dp))
                } else {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
                IconButton(onClick = { viewModel.cancel(model) }) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
            }
            ModelState.Extracting -> CircularProgressIndicator(Modifier.size(24.dp))
            ModelState.Installed -> {
                if (!isSelected) TextButton(onClick = { viewModel.use(model) }) { Text("Use") }
                IconButton(onClick = { viewModel.delete(model) }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
        }
    }
}
