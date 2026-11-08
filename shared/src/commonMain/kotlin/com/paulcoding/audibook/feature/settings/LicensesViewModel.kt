package com.paulcoding.audibook.feature.settings

import androidx.lifecycle.ViewModel
import com.paulcoding.audibook.core.tts.ModelManager
import com.paulcoding.audibook.core.tts.TtsModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class LicensesViewModel(private val models: ModelManager) : ViewModel() {
    val installedVoices: StateFlow<List<TtsModel>> = models.installed

    suspend fun voiceLicense(id: String): String? = withContext(Dispatchers.IO) { models.licenseText(id) }
}
