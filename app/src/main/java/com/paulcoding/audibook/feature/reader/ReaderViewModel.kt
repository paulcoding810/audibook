package com.paulcoding.audibook.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulcoding.audibook.core.data.SettingsRepository
import com.paulcoding.audibook.core.playback.ReadAloudController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReaderViewModel(
    val bookId: String,
    private val controller: ReadAloudController,
    private val settings: SettingsRepository,
) : ViewModel() {
    val state = controller.state
    val errors = controller.errors
    val speed = settings.settings.map { it.speed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

    init {
        viewModelScope.launch { controller.open(bookId) }
    }

    fun togglePlay() = controller.togglePlay()
    fun seekTo(chapter: Int, sentence: Int) = controller.seekTo(chapter, sentence)
    fun nextSentence() = controller.nextSentence()
    fun previousSentence() = controller.previousSentence()
    fun nextChapter() = controller.nextChapter()
    fun previousChapter() = controller.previousChapter()
    fun setSleepTimer(minutes: Int?) = controller.setSleepTimer(minutes)
    fun setSpeed(speed: Float) {
        viewModelScope.launch { settings.setSpeed(speed) }
    }
}
