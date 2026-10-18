package com.paulcoding.audibook.core.playback

import android.content.Context
import com.paulcoding.audibook.core.data.BookRepository
import com.paulcoding.audibook.core.data.EngineType
import com.paulcoding.audibook.core.data.SettingsRepository
import com.paulcoding.audibook.core.data.SleepPrefs
import com.paulcoding.audibook.core.data.TtsSettings
import com.paulcoding.audibook.core.data.db.BookEntity
import com.paulcoding.audibook.core.epub.ChapterContent
import com.paulcoding.audibook.core.epub.ChapterRef
import com.paulcoding.audibook.core.tts.SherpaTtsEngine
import com.paulcoding.audibook.core.tts.SpeechEngine
import com.paulcoding.audibook.core.tts.SystemTtsEngine
import com.paulcoding.audibook.core.tts.Utterance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Position(val chapter: Int = 0, val sentence: Int = 0)

data class PlaybackState(
    val book: BookEntity? = null,
    val chapters: List<ChapterRef> = emptyList(),
    val chapter: ChapterContent? = null,
    val position: Position = Position(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val sleepTimerEndsAt: Long? = null,
) {
    val chapterTitle: String? get() = chapters.getOrNull(position.chapter)?.title
}

/** Single source of truth for what is being read aloud; all calls on the main thread. */
class ReadAloudController(
    context: Context,
    private val books: BookRepository,
    private val settings: SettingsRepository,
    private val systemEngine: SystemTtsEngine,
    private val sherpaEngine: SherpaTtsEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _sleepAutoStarted = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    /** Emits the duration whenever Play starts the sleep timer automatically. */
    val sleepAutoStarted: SharedFlow<Int> = _sleepAutoStarted.asSharedFlow()
    private var sleepPrefs = SleepPrefs()

    private var playJob: Job? = null
    private var previewJob: Job? = null
    private var audioJob: Job? = null
    private var sleepJob: Job? = null

    private val focus = AudioFocus(
        context,
        onPause = { transient -> if (transient) stopSpeaking() else pause(byUser = false) },
        onResume = ::play,
    )

    init {
        scope.launch {
            settings.settings.drop(1).collect { restartIfPlaying() }
        }
        scope.launch {
            settings.sleepPrefs.collect { sleepPrefs = it }
        }
    }

    suspend fun open(bookId: String) {
        if (_state.value.book?.id == bookId) return
        val book = books.getBook(bookId) ?: return
        pause(byUser = false)
        val chapters = books.chapters(book)
        var chapterIndex = book.chapterIndex.coerceIn(0, chapters.lastIndex)
        var content = books.chapter(book, chapterIndex)
        while (content.sentences.isEmpty() && chapterIndex < chapters.lastIndex) {
            content = books.chapter(book, ++chapterIndex)
        }
        val sentence = book.sentenceIndex.coerceIn(0, (content.sentences.size - 1).coerceAtLeast(0))
        _state.value = PlaybackState(book, chapters, content, Position(chapterIndex, sentence))
    }

    fun close(bookId: String) {
        if (_state.value.book?.id != bookId) return
        pause()
        _state.value = PlaybackState()
    }

    fun togglePlay() = if (_state.value.isPlaying) pause() else play()

    fun play() {
        val current = _state.value
        val book = current.book ?: return
        if (playJob?.isActive == true) return
        previewJob?.cancel()
        if (!focus.request()) {
            _errors.tryEmit("Audio is in use by another app")
            return
        }
        _state.update { it.copy(isPlaying = true) }
        playJob = startSpeaking(book, current.position)
        if (sleepPrefs.autoStart && _state.value.sleepTimerEndsAt == null) {
            setSleepTimer(sleepPrefs.minutes)
            _sleepAutoStarted.tryEmit(sleepPrefs.minutes)
        }
    }

    /** Stops reading; a pause the user asked for also cancels the sleep timer, interruptions keep it. */
    fun pause(byUser: Boolean = true) {
        if (byUser) setSleepTimer(null)
        val wasActive = playJob != null
        stopSpeaking()
        focus.abandon()
        if (wasActive) saveProgress()
    }

    fun seekTo(chapter: Int, sentence: Int) {
        val current = _state.value
        val book = current.book ?: return
        if (current.chapters.isEmpty()) return
        val wasPlaying = current.isPlaying
        playJob?.cancel()
        playJob = null
        scope.launch {
            val index = chapter.coerceIn(0, current.chapters.lastIndex)
            val content = books.chapter(book, index)
            val position = Position(index, sentence.coerceIn(0, (content.sentences.size - 1).coerceAtLeast(0)))
            _state.update { it.copy(chapter = content, position = position) }
            saveProgress()
            if (wasPlaying && _state.value.isPlaying) playJob = startSpeaking(book, position)
        }
    }

    fun nextSentence() {
        val (chapter, sentence) = _state.value.position
        val size = _state.value.chapter?.sentences?.size ?: 0
        if (sentence + 1 < size) seekTo(chapter, sentence + 1) else nextChapter()
    }

    fun previousSentence() {
        val (chapter, sentence) = _state.value.position
        if (sentence > 0) seekTo(chapter, sentence - 1) else if (chapter > 0) seekTo(chapter - 1, Int.MAX_VALUE)
    }

    fun nextChapter() {
        val current = _state.value
        if (current.position.chapter < current.chapters.lastIndex) seekTo(current.position.chapter + 1, 0)
    }

    fun previousChapter() {
        val (chapter, sentence) = _state.value.position
        if (sentence > 0 || chapter == 0) seekTo(chapter, 0) else seekTo(chapter - 1, 0)
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _state.update { it.copy(sleepTimerEndsAt = null) }
            return
        }
        val durationMs = minutes * 60_000L
        _state.update { it.copy(sleepTimerEndsAt = System.currentTimeMillis() + durationMs) }
        sleepJob = scope.launch {
            delay(durationMs)
            pause(byUser = false)
            _state.update { it.copy(sleepTimerEndsAt = null) }
        }
    }

    fun preview(text: String, language: String?) {
        pause(byUser = false)
        previewJob = launchAudio {
            try {
                val current = settings.settings.first()
                engineFor(current).speak(Utterance(-1, -1, text), current, language, { null }) {}
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.tryEmit(e.message ?: "Preview failed")
            }
        }
    }

    private fun restartIfPlaying() {
        val current = _state.value
        val book = current.book ?: return
        if (!current.isPlaying) return
        playJob?.cancel()
        playJob = startSpeaking(book, current.position)
    }

    private fun stopSpeaking() {
        playJob?.cancel()
        playJob = null
        _state.update { it.copy(isPlaying = false, isBuffering = false) }
    }

    /** Starts audio output only once the previous audio job has fully stopped, so samples and playback never overlap. */
    private fun launchAudio(block: suspend CoroutineScope.() -> Unit): Job {
        val previous = audioJob
        previous?.cancel()
        return scope.launch {
            previous?.join()
            block()
        }.also { audioJob = it }
    }

    private fun startSpeaking(book: BookEntity, from: Position): Job = launchAudio {
        val self = coroutineContext.job
        try {
            val first = utteranceAt(book, from)
            if (first != null) {
                val current = settings.settings.first()
                _state.update { it.copy(isBuffering = true) }
                engineFor(current).speak(first, current, book.language, { utteranceAt(book, it.chapter, it.sentence + 1) }) {
                    onSentenceStart(book, it)
                }
            }
            onFinished(self)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (playJob !== self) return@launchAudio
            _errors.tryEmit(e.message ?: "Playback failed")
            onFinished(self)
        }
    }

    private fun onFinished(job: Job) {
        if (playJob !== job) return
        playJob = null
        stopSpeaking()
        focus.abandon()
        saveProgress()
    }

    private fun onSentenceStart(book: BookEntity, utterance: Utterance) {
        val current = _state.value
        if (current.book?.id != book.id) return
        _state.update { it.copy(position = Position(utterance.chapter, utterance.sentence), isBuffering = false) }
        if (current.chapter?.index != utterance.chapter) {
            scope.launch {
                val content = books.chapter(book, utterance.chapter)
                _state.update { if (it.book?.id == book.id) it.copy(chapter = content) else it }
            }
        }
        saveProgress()
    }

    private fun saveProgress() {
        val current = _state.value
        val book = current.book ?: return
        scope.launch(Dispatchers.IO + NonCancellable) {
            books.saveProgress(book.id, current.position.chapter, current.position.sentence)
        }
    }

    private suspend fun utteranceAt(book: BookEntity, position: Position) =
        utteranceAt(book, position.chapter, position.sentence)

    private suspend fun utteranceAt(book: BookEntity, chapter: Int, sentence: Int): Utterance? =
        withContext(Dispatchers.Default) {
            var c = chapter
            var s = sentence
            while (c < book.chapterCount) {
                val content = books.chapter(book, c)
                if (s < content.sentences.size) return@withContext Utterance(c, s, content.sentences[s].text)
                c++
                s = 0
            }
            null
        }

    private fun engineFor(settings: TtsSettings): SpeechEngine =
        if (settings.engine == EngineType.SHERPA) sherpaEngine else systemEngine
}
