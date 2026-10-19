package com.paulcoding.audibook.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paulcoding.audibook.core.data.BookRepository
import com.paulcoding.audibook.core.data.ImportOutcome
import com.paulcoding.audibook.core.data.hasFile
import com.paulcoding.audibook.core.data.db.BookEntity
import com.paulcoding.audibook.core.playback.ReadAloudController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryItem(val book: BookEntity, val missingFile: Boolean)

class LibraryViewModel(
    private val books: BookRepository,
    private val controller: ReadAloudController,
) : ViewModel() {
    val library = books.observeBooks()
        .map { list -> list.map { LibraryItem(it, missingFile = !it.hasFile) } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { runCatching { books.backfillFingerprints() } }
    }

    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    val playback = controller.state

    fun import(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            runCatching { books.import(uri) }
                .onSuccess { outcome ->
                    val title = outcome.book.title
                    _messages.emit(
                        when (outcome) {
                            is ImportOutcome.Added -> "Added \"$title\""
                            is ImportOutcome.AlreadyInLibrary -> "\"$title\" is already in your library"
                            is ImportOutcome.Reattached -> "Restored \"$title\" with your reading progress"
                        }
                    )
                }
                .onFailure { _messages.emit("Couldn't import: ${it.message ?: "invalid EPUB"}") }
            _isImporting.value = false
        }
    }

    fun delete(book: BookEntity) {
        controller.close(book.id)
        viewModelScope.launch { books.delete(book) }
    }

    fun togglePlay() = controller.togglePlay()
}
