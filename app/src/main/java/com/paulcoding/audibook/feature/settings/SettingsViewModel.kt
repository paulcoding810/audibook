package com.paulcoding.audibook.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulcoding.audibook.core.data.EngineType
import com.paulcoding.audibook.core.data.SettingsRepository
import com.paulcoding.audibook.core.data.TtsSettings
import com.paulcoding.audibook.core.playback.ReadAloudController
import com.paulcoding.audibook.core.tts.ModelManager
import com.paulcoding.audibook.core.update.AppUpdater
import com.paulcoding.audibook.core.tts.ModelState
import com.paulcoding.audibook.core.tts.SystemTtsEngine
import com.paulcoding.audibook.core.tts.SystemVoice
import com.paulcoding.audibook.core.tts.TtsModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val systemEngine: SystemTtsEngine,
    private val models: ModelManager,
    private val controller: ReadAloudController,
    private val updater: AppUpdater,
) : ViewModel() {
    val updateState = updater.state
    val updateMessages = updater.messages
    val canSelfUpdate = updater.canSelfUpdate

    fun checkForUpdate() = updater.check()
    fun installUpdate() = updater.downloadAndInstall()
    fun dismissUpdate() = updater.dismiss()

    val state: StateFlow<TtsSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val installedModels: StateFlow<List<TtsModel>> = models.states
        .map { states -> models.catalog.filter { states[it.id] == ModelState.Installed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val errors = controller.errors

    private val _voices = MutableStateFlow<List<SystemVoice>?>(null)
    val voices = _voices.asStateFlow()

    fun loadVoices() {
        if (_voices.value != null) return
        viewModelScope.launch {
            _voices.value = runCatching { systemEngine.voices() }.getOrDefault(emptyList())
        }
    }

    fun model(id: String?) = id?.let(models::model)

    fun setEngine(engine: EngineType) = launch { settings.setEngine(engine) }
    fun setSystemVoice(name: String?) = launch { settings.setSystemVoice(name) }
    fun setModel(id: String) = launch {
        settings.setSherpaModel(id)
        settings.setEngine(EngineType.SHERPA)
    }
    fun setSpeaker(speaker: Int) = launch { settings.setSherpaSpeaker(speaker) }
    fun setSpeed(speed: Float) = launch { settings.setSpeed(speed) }
    fun setPitch(pitch: Float) = launch { settings.setPitch(pitch) }

    fun preview() {
        val language = controller.state.value.book?.language
        val sample = controller.state.value.chapter?.sentences
            ?.firstOrNull { it.text.length in 40..200 }?.text
            ?: "Hello! This is how your books will sound when read aloud."
        controller.preview(sample, language)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
