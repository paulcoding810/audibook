package com.paulcoding.audibook.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulcoding.audibook.core.data.EngineType
import com.paulcoding.audibook.core.data.SettingsRepository
import com.paulcoding.audibook.core.tts.ModelManager
import com.paulcoding.audibook.core.tts.TtsModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
