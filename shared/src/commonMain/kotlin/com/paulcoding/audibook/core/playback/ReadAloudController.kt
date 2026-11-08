package com.paulcoding.audibook.core.playback

import com.paulcoding.audibook.core.data.BookRepository
import com.paulcoding.audibook.core.data.EngineType
import com.paulcoding.audibook.core.data.SettingsRepository
import com.paulcoding.audibook.core.data.SleepPrefs
import com.paulcoding.audibook.core.data.hasFile
import com.paulcoding.audibook.core.data.TtsSettings
import com.paulcoding.audibook.core.data.db.BookEntity
import com.paulcoding.audibook.core.epub.ChapterContent
import com.paulcoding.audibook.core.epub.ChapterRef
import com.paulcoding.audibook.core.tts.SherpaTtsEngine
import com.paulcoding.audibook.core.tts.SpeechEngine
import com.paulcoding.audibook.core.tts.SystemSpeechEngine
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

/** Why [ReadAloudController.open] couldn't open a book. */
sealed interface OpenFailure {
    data object NotInLibrary : OpenFailure
    /** Its EPUB isn't on this device, e.g. after restoring a backup without books. */
    data object MissingFile : OpenFailure
    data object DrmProtected : OpenFailure
    data class Unreadable(val reason: String) : OpenFailure
}

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
    private val books: BookRepository,
    private val settings: SettingsRepository,
    private val systemEngine: SystemSpeechEngine,
    private val sherpaEngine: SherpaTtsEngine,
    focusFactory: AudioFocusFactory,
    private val shake: ShakeSensor,
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
    private var shakeWindowJob: Job? = null

    private val focus = focusFactory.create(
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

    /** Loads [bookId] for reading; returns why it couldn't, or null once it's open. */
    suspend fun open(bookId: String): OpenFailure? {
        if (_state.value.book?.id == bookId) return null
        val book = books.getBook(bookId) ?: return OpenFailure.NotInLibrary
        if (!book.hasFile) return OpenFailure.MissingFile
        pause(byUser = false)
        stopShakeWindow()
        val chapters: List<ChapterRef>
        var content: ChapterContent
        var chapterIndex: Int
        try {
            if (books.isDrmProtected(book)) return OpenFailure.DrmProtected
            chapters = books.chapters(book)
            if (chapters.isEmpty()) return OpenFailure.Unreadable("it has no readable chapters")
            chapterIndex = book.chapterIndex.coerceIn(0, chapters.lastIndex)
            content = books.chapter(book, chapterIndex)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return OpenFailure.Unreadable(e.message ?: "unknown error")
        }
        while (content.sentences.isEmpty() && chapterIndex < chapters.lastIndex) {
            content = books.chapter(book, ++chapterIndex)
        }
        var sentence = book.sentenceIndex.coerceIn(0, (content.sentences.size - 1).coerceAtLeast(0))
        // A book never read before starts at its first real text, past license pages and contents.
        if (book.lastReadAt == null && settings.settings.first().skipFiller) {
            utteranceAt(book, chapterIndex, sentence, skipFiller = true)?.let { first ->
                if (first.chapter != chapterIndex) content = books.chapter(book, first.chapter)
                chapterIndex = first.chapter
                sentence = first.sentence
            }
        }
        _state.value = PlaybackState(book, chapters, content, Position(chapterIndex, sentence))
        return null
    }

    fun close(bookId: String) {
        if (_state.value.book?.id != bookId) return
        pause()
        stopShakeWindow()
        _state.value = PlaybackState()
    }

    fun togglePlay() = if (_state.value.isPlaying) pause() else play()

    fun play() {
        val current = _state.value
        val book = current.book ?: return
        if (playJob?.isActive == true) return
        stopShakeWindow()
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
        if (byUser) {
            setSleepTimer(null)
            stopShakeWindow()
        }
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

    fun setSleepTimer(minutes: Int?) = setSleepTimerMs(minutes?.times(60_000L))

    /** Like [setSleepTimer] but in milliseconds; debug builds use it for a seconds-long test timer. */
    fun setSleepTimerMs(durationMs: Long?) {
        sleepJob?.cancel()
        if (durationMs == null) {
            _state.update { it.copy(sleepTimerEndsAt = null) }
            return
        }
        _state.update { it.copy(sleepTimerEndsAt = System.currentTimeMillis() + durationMs) }
        sleepJob = scope.launch {
            delay(durationMs)
            pause(byUser = false)
            _state.update { it.copy(sleepTimerEndsAt = null) }
            if (sleepPrefs.shakeToResume) startShakeWindow(durationMs)
        }
    }

    /** After the sleep timer stops, a shake within [SHAKE_WINDOW_MS] resumes reading with the same timer. */
    private fun startShakeWindow(timerMs: Long) {
        stopShakeWindow()
        if (!shake.isAvailable) return
        shake.start(SHAKE_WINDOW_MS) {
            stopShakeWindow()
            play()
            if (_state.value.isPlaying) setSleepTimerMs(timerMs)
        }
        shakeWindowJob = scope.launch {
            delay(SHAKE_WINDOW_MS)
            shake.stop()
        }
    }

    private fun stopShakeWindow() {
        shakeWindowJob?.cancel()
        shakeWindowJob = null
        shake.stop()
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
                // The first sentence is exactly where the user is; filler is skipped only from there on.
                engineFor(current).speak(first, current, book.language, { utteranceAt(book, it.chapter, it.sentence + 1, current.skipFiller) }) {
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
        utteranceAt(book, position.chapter, position.sentence, skipFiller = false)

    /** The sentence at or after [chapter]/[sentence], passing over filler paragraphs when [skipFiller] is set. */
    private suspend fun utteranceAt(book: BookEntity, chapter: Int, sentence: Int, skipFiller: Boolean): Utterance? =
        withContext(Dispatchers.Default) {
            var c = chapter
            var s = sentence
            while (c < book.chapterCount) {
                val content = books.chapter(book, c)
                while (s < content.sentences.size) {
                    val text = content.sentences[s]
                    if (!skipFiller || content.paragraphs[text.paragraph].spoken) return@withContext Utterance(c, s, text.text)
                    s++
                }
                c++
                s = 0
            }
            null
        }

    private fun engineFor(settings: TtsSettings): SpeechEngine =
        if (settings.engine == EngineType.SHERPA) sherpaEngine else systemEngine

    private companion object {
        // Within Media3's 10-minute foreground grace period, so resuming from the background is allowed.
        const val SHAKE_WINDOW_MS = 5 * 60_000L
    }
}
