package com.paulcoding.audibook.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulcoding.audibook.BuildConfig
import com.paulcoding.audibook.core.data.EngineType
import com.paulcoding.audibook.core.data.TtsSettings
import com.paulcoding.audibook.core.tts.ModelCatalog
import com.paulcoding.audibook.core.tts.SystemVoice
import com.paulcoding.audibook.core.tts.TtsModel
import com.paulcoding.audibook.core.update.AppRelease
import com.paulcoding.audibook.core.update.UpdateState
import java.time.LocalDate
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val installed by viewModel.installedModels.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showVoices by remember { mutableStateOf(false) }
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val backupBusy by viewModel.backupBusy.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBackup)
    }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.updateMessages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.backupMessages.collect { snackbar.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long) }
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(updateState) {
        val message = when (val update = updateState) {
            UpdateState.UpToDate -> "You're on the latest version (${BuildConfig.VERSION_NAME})"
            is UpdateState.Failed -> update.message
            else -> return@LaunchedEffect
        }
        viewModel.dismissUpdate()
        scope.launch { snackbar.showSnackbar(message) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = settings ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            SectionTitle("Engine")
            EngineOption(
                selected = current.engine == EngineType.SYSTEM,
                title = "Google Text-to-Speech",
                description = "Your device's speech engine. Supports many languages; some voices need internet.",
                onClick = { viewModel.setEngine(EngineType.SYSTEM) },
            )
            EngineOption(
                selected = current.engine == EngineType.SHERPA,
                title = "On-device AI voices",
                description = "Neural voices that run fully offline. Download a voice model first.",
                onClick = { viewModel.setEngine(EngineType.SHERPA) },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            if (current.engine == EngineType.SYSTEM) {
                SectionTitle("Voice")
                ListItem(
                    headlineContent = { Text(current.systemVoice ?: "Automatic") },
                    supportingContent = { Text(if (current.systemVoice == null) "Matches each book's language" else "Tap to change") },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.loadVoices()
                        showVoices = true
                    },
                )
                ListItem(
                    headlineContent = { Text("Install more voices") },
                    supportingContent = { Text("Opens the system text-to-speech settings") },
                    modifier = Modifier.clickable {
                        try {
                            context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: ActivityNotFoundException) {
                        }
                    },
                )
            } else {
                SherpaSection(current, installed, viewModel, onOpenModels)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Delivery")
            SettingSlider("Speed", current.speed, 0.5f..3f, viewModel::setSpeed)
            if (current.engine == EngineType.SYSTEM) {
                SettingSlider("Pitch", current.pitch, 0.5f..2f, viewModel::setPitch)
            }
            Button(
                onClick = viewModel::preview,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("Play sample")
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Backup")
            ListItem(
                headlineContent = { Text("Export library") },
                supportingContent = { Text("Books, reading progress and settings in one .audibook file") },
                modifier = Modifier.clickable(enabled = backupBusy == null) {
                    exportLauncher.launch("AudiBook-${LocalDate.now()}.audibook")
                },
            )
            ListItem(
                headlineContent = { Text("Import backup") },
                supportingContent = { Text("Restores books and progress, and merges with what's here") },
                modifier = Modifier.clickable(enabled = backupBusy == null) { importLauncher.launch(arrayOf("*/*")) },
            )
            Text(
                "Reading progress and settings are also backed up automatically with your Google account. Voice models aren't included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("About")
            ListItem(
                headlineContent = {
                    Text(if (viewModel.canSelfUpdate) "Check for updates" else "Check for updates on Google Play")
                },
                supportingContent = { Text("Version ${BuildConfig.VERSION_NAME}") },
                trailingContent = {
                    if (updateState == UpdateState.Checking) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                },
                modifier = Modifier.clickable(enabled = updateState == UpdateState.Idle, onClick = viewModel::checkForUpdate),
            )
        }
    }

    backupBusy?.let { label ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(label) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
    }

    when (val update = updateState) {
        is UpdateState.Available -> UpdateAvailableDialog(
            release = update.release,
            onInstall = viewModel::installUpdate,
            onLater = viewModel::dismissUpdate,
        )
        is UpdateState.Downloading -> UpdateDownloadingDialog(update.release, update.progress, onCancel = viewModel::dismissUpdate)
        else -> Unit
    }

    if (showVoices) {
        ModalBottomSheet(onDismissRequest = { showVoices = false }) {
            VoicePicker(voices, settings?.systemVoice) {
                viewModel.setSystemVoice(it)
                showVoices = false
            }
        }
    }
}

@Composable
private fun UpdateAvailableDialog(release: AppRelease, onInstall: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Update to ${release.version}") },
        text = {
            Text(
                release.notes.ifBlank { "A new version of AudiBook is available." },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onInstall) { Text("Download & install") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Later") } },
    )
}

@Composable
private fun UpdateDownloadingDialog(release: AppRelease, progress: Float?, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Downloading ${release.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun SherpaSection(
    current: TtsSettings,
    installed: List<TtsModel>,
    viewModel: SettingsViewModel,
    onOpenModels: () -> Unit,
) {
    SectionTitle("Voice model")
    if (installed.isEmpty()) {
        Text(
            "No voice models downloaded yet.",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    installed.forEach { model ->
        EngineOption(
            selected = current.sherpaModelId == model.id,
            title = model.name,
            description = ModelCatalog.languageLabel(model.language) + if (model.sizeMb > 0) " · ${model.sizeMb} MB" else "",
            onClick = { viewModel.setModel(model.id) },
            badge = { VoiceWeightBadge(model.weight) },
        )
    }
    OutlinedButton(onClick = onOpenModels, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Download voice models")
    }
    val model = viewModel.model(current.sherpaModelId)
    val speakers = model?.let { viewModel.speakerCount(it.id) } ?: 0
    if (model != null && speakers > 1 && installed.any { it.id == model.id }) {
        var speaker by remember(current.sherpaSpeaker, model.id) { mutableFloatStateOf(current.sherpaSpeaker.toFloat()) }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Speaker ${speaker.toInt() + 1} of $speakers", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = speaker,
                onValueChange = { speaker = it },
                onValueChangeFinished = { viewModel.setSpeaker(speaker.toInt()) },
                valueRange = 0f..(speakers - 1).toFloat(),
                steps = if (speakers <= 20) speakers - 2 else 0,
            )
        }
    }
}

@Composable
private fun VoicePicker(voices: List<SystemVoice>?, selected: String?, onSelect: (String?) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search language or voice") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (voices == null) {
            CircularProgressIndicator(Modifier.padding(32.dp).align(Alignment.CenterHorizontally))
            return@Column
        }
        val filtered = voices.filter {
            query.isBlank() || it.name.contains(query, true) || it.locale.displayName.contains(query, true)
        }
        LazyColumn {
            item {
                ListItem(
                    headlineContent = { Text("Automatic") },
                    supportingContent = { Text("Match each book's language") },
                    leadingContent = { RadioButton(selected = selected == null, onClick = null) },
                    modifier = Modifier.clickable { onSelect(null) },
                )
            }
            items(filtered, key = { it.name }) { voice ->
                ListItem(
                    headlineContent = { Text(voice.locale.displayName) },
                    supportingContent = { Text(voice.name) },
                    leadingContent = { RadioButton(selected = selected == voice.name, onClick = null) },
                    trailingContent = {
                        if (voice.requiresNetwork) Icon(Icons.Default.Cloud, contentDescription = "Needs internet")
                    },
                    modifier = Modifier.clickable { onSelect(voice.name) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun EngineOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
    badge: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, modifier = Modifier.weight(1f, fill = false))
                badge?.invoke()
            }
        },
        supportingContent = { Text(description) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text("%.2f×".format(local), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = local,
            onValueChange = { local = (it * 20).toInt() / 20f },
            onValueChangeFinished = { onChange(local) },
            valueRange = range,
        )
        Spacer(Modifier.height(4.dp))
    }
}
