package com.paulcoding.audibook.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulcoding.audibook.core.data.EngineType
import com.paulcoding.audibook.core.data.SettingsRepository
import com.paulcoding.audibook.core.playback.ReadAloudController
import com.paulcoding.audibook.core.tts.ModelCatalog
import com.paulcoding.audibook.core.tts.ModelManager
import com.paulcoding.audibook.core.tts.ModelState
import com.paulcoding.audibook.core.tts.TtsModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModelSection(val title: String, val models: List<TtsModel>)

class ModelsViewModel(
    private val models: ModelManager,
    private val settings: SettingsRepository,
    controller: ReadAloudController,
) : ViewModel() {
    val states = models.states
    val selected = settings.settings.map { if (it.engine == EngineType.SHERPA) it.sherpaModelId else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    /** Base language code to filter by (e.g. "vi"), or null for all; starts at the open book's language. */
    private val _language = MutableStateFlow(controller.state.value.book?.language?.let { ModelCatalog.baseLanguage(it.replace('-', '_')) })
    val language = _language.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val languages: StateFlow<List<String>> = models.catalog
        .map { all ->
            all.map { ModelCatalog.baseLanguage(it.language) }
                .filter { it.isNotEmpty() && it != ModelCatalog.MULTILINGUAL }
                .distinct()
                .sortedBy { ModelCatalog.languageLabel(it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sections: StateFlow<List<ModelSection>> = combine(models.catalog, models.states, _query, _language) { all, states, query, language ->
        fun matchesQuery(model: TtsModel) =
            query.isBlank() || "${model.name} ${ModelCatalog.languageLabel(model.language)} ${model.id}".contains(query.trim(), ignoreCase = true)
        // Installed voices ignore the language filter so they can always be managed.
        val installed = all.filter { states[it.id] == ModelState.Installed && matchesQuery(it) }
        val matches = all.filter { model ->
            val inLanguage = language == null || ModelCatalog.baseLanguage(model.language) == language || model.language == ModelCatalog.MULTILINGUAL
            inLanguage && matchesQuery(model)
        }
        val recommended = matches.filter { it.id in ModelCatalog.recommended && it !in installed }
        val others = (matches - installed.toSet() - recommended.toSet())
            .sortedWith(compareBy({ ModelCatalog.languageLabel(it.language) }, { it.name }))
        listOf(
            ModelSection("Installed", installed),
            ModelSection("Recommended", recommended),
            ModelSection("All voices (${others.size})", others),
        ).filter { it.models.isNotEmpty() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh(force = false)
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setLanguage(language: String?) {
        _language.value = language
    }

    fun refresh(force: Boolean = true) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                models.refreshCatalog(force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Couldn't load the voice list: ${e.message ?: "network error"}"
            }
            _loading.value = false
        }
    }

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
