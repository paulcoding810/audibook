package com.paulcoding.audibook.feature.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulcoding.audibook.core.data.SettingsRepository
import com.paulcoding.audibook.core.tts.ModelManager
import com.paulcoding.audibook.core.tts.ModelState
import com.paulcoding.audibook.core.tts.TtsModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.paulcoding.audibook.core.data.EngineType
import org.koin.androidx.compose.koinViewModel

class ModelsViewModel(
    private val models: ModelManager,
    private val settings: SettingsRepository,
) : ViewModel() {
    val catalog = models.catalog
    val states = models.states
    val selected = settings.settings.map { if (it.engine == EngineType.SHERPA) it.sherpaModelId else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun download(model: TtsModel) = models.download(model)
    fun cancel(model: TtsModel) = models.cancel(model.id)
    fun delete(model: TtsModel) {
        models.delete(model.id)
        viewModelScope.launch {
            if (selected.value == model.id) {
                settings.setSherpaModel(null)
                settings.setEngine(EngineType.SYSTEM)
            }
        }
    }

    fun use(model: TtsModel) {
        viewModelScope.launch {
            settings.setSherpaModel(model.id)
            settings.setEngine(EngineType.SHERPA)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onBack: () -> Unit, viewModel: ModelsViewModel = koinViewModel()) {
    val states by viewModel.states.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice models") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Text(
                    "Models run entirely on this device with sherpa-onnx. Downloads come from the sherpa-onnx GitHub releases.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(viewModel.catalog, key = { it.id }) { model ->
                val state = states[model.id] ?: ModelState.NotInstalled
                ListItem(
                    headlineContent = { Text(model.name) },
                    supportingContent = {
                        Text(
                            when (state) {
                                is ModelState.Downloading -> state.progress?.let { "Downloading ${(it * 100).toInt()}%" } ?: "Downloading…"
                                ModelState.Extracting -> "Unpacking…"
                                is ModelState.Failed -> state.message
                                else -> "${model.language} · ${model.sizeMb} MB"
                            },
                            color = if (state is ModelState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = if (selected == model.id) {
                        { Icon(Icons.Default.CheckCircle, contentDescription = "In use", tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    trailingContent = {
                        ModelActions(model, state, isSelected = selected == model.id, viewModel)
                    },
                )
            }
        }
    }
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
