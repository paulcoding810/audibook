package com.paulcoding.audibook.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulcoding.audibook.BuildConfig
import com.paulcoding.audibook.core.data.SleepPrefs
import com.paulcoding.audibook.core.epub.ChapterContent
import com.paulcoding.audibook.core.epub.Paragraph
import com.paulcoding.audibook.core.playback.PlaybackState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
private const val SLEEP_MIN = 5
private const val SLEEP_MAX = 120
private const val SLEEP_STEP = 5
private val plainLink = TextLinkStyles(style = SpanStyle())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ReaderViewModel = koinViewModel(key = bookId) { parametersOf(bookId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val openFailed by viewModel.openFailed.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showToc by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    val sleepPrefs by viewModel.sleepPrefs.collectAsStateWithLifecycle()
    val ready = state.book?.id == bookId
    val topBarScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.sleepAutoStarted.collect { minutes ->
            val result = snackbar.showSnackbar("Sleep timer: ${formatMinutes(minutes)}", actionLabel = "Cancel", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.setSleepTimer(null)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = topBarScroll,
                title = {
                    Column {
                        Text(state.book?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        state.chapterTitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showToc = true }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Chapters")
                    }
                    IconButton(onClick = { showSleep = true }) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = "Sleep timer",
                            tint = if (state.sleepTimerEndsAt != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        bottomBar = {
            if (ready && state.isPlaying) PlayerControls(
                state = state,
                speed = speed,
                onToggle = viewModel::togglePlay,
                onPreviousChapter = viewModel::previousChapter,
                onNextChapter = viewModel::nextChapter,
                onPreviousSentence = viewModel::previousSentence,
                onNextSentence = viewModel::nextSentence,
                onSpeed = viewModel::setSpeed,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val chapter = state.chapter
        if (openFailed) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("This book can't be opened", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Its EPUB file isn't on this device. Re-add it from the library to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                TextButton(onClick = onBack) { Text("Back to library") }
            }
        } else if (!ready || chapter == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            ChapterText(
                chapter = chapter,
                state = state,
                onSentenceClick = { viewModel.seekTo(chapter.index, it) },
                onOpenChapter = viewModel::seekTo,
                onResume = viewModel::togglePlay,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showSleep) {
        SleepTimerSheet(
            prefs = sleepPrefs,
            endsAt = state.sleepTimerEndsAt,
            onStart = {
                viewModel.setSleepTimer(it)
                showSleep = false
            },
            onTurnOff = {
                viewModel.setSleepTimer(null)
                showSleep = false
            },
            onAutoStartChange = viewModel::setSleepAutoStart,
            onShakeToResumeChange = viewModel::setSleepShakeToResume,
            onStartTest = {
                viewModel.startTestSleepTimer()
                showSleep = false
            },
            onDismiss = { showSleep = false },
        )
    }

    if (showToc) {
        ChapterListSheet(
            chapters = state.chapters,
            current = state.position.chapter,
            onSelect = {
                viewModel.seekTo(it, 0)
                showToc = false
            },
            onDismiss = { showToc = false },
        )
    }
}

/** Shows the chapter; follows playback while playing, and acts as a plain reader while paused (tap a sentence to start there). */
@Composable
private fun ChapterText(
    chapter: ChapterContent,
    state: PlaybackState,
    onSentenceClick: (Int) -> Unit,
    onOpenChapter: (chapter: Int, sentence: Int) -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    val currentSentence = if (state.position.chapter == chapter.index) state.position.sentence else -1
    val previous = state.chapters.getOrNull(chapter.index - 1)
    val next = state.chapters.getOrNull(chapter.index + 1)
    // The "previous chapter" link is list item 0, so paragraphs start after it.
    val firstParagraphItem = if (previous != null) 1 else 0
    val currentParagraph = chapter.sentences.getOrNull(currentSentence)?.paragraph?.plus(firstParagraphItem) ?: -1

    val userScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) follow = false
                return Offset.Zero
            }
        }
    }
    val currentVisible by remember(currentParagraph) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.index == currentParagraph } }
    }

    val scope = rememberCoroutineScope()
    val lastItem = firstParagraphItem + chapter.paragraphs.size.coerceAtLeast(1) - 1 + (if (next != null) 1 else 0)
    val atChapterEnd = currentSentence > 0 && currentSentence == chapter.sentences.lastIndex

    LaunchedEffect(chapter.index) {
        follow = true
        // Arriving at the last sentence (e.g. "Previous chapter") shows the end of the chapter, like turning back a page.
        listState.scrollToItem(if (atChapterEnd) lastItem else currentParagraph.coerceAtLeast(0))
    }
    LaunchedEffect(currentParagraph, follow, state.isPlaying) {
        if (follow && state.isPlaying && currentParagraph >= 0) listState.revealParagraph(currentParagraph)
    }

    val scrolledAway = !follow && currentParagraph >= 0 && !currentVisible
    val paused = !state.isPlaying

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 16.dp + (if (paused) 72.dp else 0.dp) + (if (scrolledAway) 56.dp else 0.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().nestedScroll(userScroll),
        ) {
            if (previous != null) {
                item(key = "previous") {
                    ChapterLink(
                        label = if (previous.title == chapter.title) "Previous section" else "Previous chapter",
                        title = previous.title,
                        forward = false,
                        onClick = { onOpenChapter(previous.index, Int.MAX_VALUE) },
                    )
                }
            }
            itemsIndexed(chapter.paragraphs, key = { index, _ -> "${chapter.index}:$index" }) { _, paragraph ->
                ParagraphText(paragraph, chapter, currentSentence) {
                    follow = true
                    onSentenceClick(it)
                }
            }
            if (chapter.paragraphs.isEmpty()) {
                item {
                    Text(
                        "This section has no text.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (next != null) {
                item(key = "next") {
                    ChapterLink(
                        label = if (next.title == chapter.title) "Continue" else "Next chapter",
                        title = next.title,
                        forward = true,
                        onClick = { onOpenChapter(next.index, 0) },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            if (scrolledAway) {
                SmallFloatingActionButton(onClick = {
                    follow = true
                    scope.launch { listState.revealParagraph(currentParagraph) }
                }) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Go to current sentence")
                }
            }
            if (paused) {
                FloatingActionButton(onClick = onResume) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
            }
        }
    }
}

@Composable
private fun ChapterLink(label: String, title: String, forward: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!forward) Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Column(
                horizontalAlignment = if (forward) Alignment.End else Alignment.Start,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = if (forward) TextAlign.End else TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (forward) Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

private suspend fun LazyListState.revealParagraph(index: Int) {
    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    val comfortablyVisible = item != null &&
        item.offset >= 0 &&
        item.offset + item.size.coerceAtMost(viewportHeight / 2) <= viewportHeight * 3 / 4
    if (!comfortablyVisible) animateScrollToItem(index, -viewportHeight / 5)
}

@Composable
private fun ParagraphText(
    paragraph: Paragraph,
    chapter: ChapterContent,
    currentSentence: Int,
    onSentenceClick: (Int) -> Unit,
) {
    val highlight = MaterialTheme.colorScheme.primaryContainer
    val onHighlight = MaterialTheme.colorScheme.onPrimaryContainer
    val range = paragraph.firstSentence until paragraph.firstSentence + paragraph.sentenceCount
    val active = currentSentence.takeIf { it in range } ?: -1
    val text: AnnotatedString = remember(paragraph, active, highlight) {
        buildAnnotatedString {
            var cursor = 0
            for (index in range) {
                val sentence = chapter.sentences[index]
                append(paragraph.text.substring(cursor, sentence.range.first))
                withLink(LinkAnnotation.Clickable("s$index", plainLink) { onSentenceClick(index) }) {
                    if (index == active) {
                        withStyle(SpanStyle(background = highlight, color = onHighlight)) {
                            append(sentence.text)
                        }
                    } else {
                        append(sentence.text)
                    }
                }
                cursor = sentence.range.last + 1
            }
            append(paragraph.text.substring(cursor))
        }
    }
    Text(
        text,
        style = if (paragraph.isHeading) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge.copy(
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.15f,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    prefs: SleepPrefs,
    endsAt: Long?,
    onStart: (Int) -> Unit,
    onTurnOff: () -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onShakeToResumeChange: (Boolean) -> Unit,
    onStartTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableFloatStateOf(prefs.minutes.coerceIn(SLEEP_MIN, SLEEP_MAX).toFloat()) }
    val minutesLeft by produceState(endsAt?.let(::minutesUntil), endsAt) {
        while (endsAt != null) {
            value = minutesUntil(endsAt)
            delay(1_000)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge)
            minutesLeft?.let {
                Text(
                    "Stops in ${formatMinutes(it)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = minutes,
                    onValueChange = { minutes = it },
                    valueRange = SLEEP_MIN.toFloat()..SLEEP_MAX.toFloat(),
                    steps = (SLEEP_MAX - SLEEP_MIN) / SLEEP_STEP - 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatMinutes(minutes.roundToInt()),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(96.dp),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onAutoStartChange(!prefs.autoStart) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Start automatically when I press Play", modifier = Modifier.weight(1f))
                Switch(checked = prefs.autoStart, onCheckedChange = onAutoStartChange)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onShakeToResumeChange(!prefs.shakeToResume) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Shake to keep listening")
                    Text(
                        "For 5 minutes after the timer stops, shake the phone to resume",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = prefs.shakeToResume, onCheckedChange = onShakeToResumeChange)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                if (BuildConfig.DEBUG) TextButton(onClick = onStartTest) { Text("Test: 10 s") }
                if (endsAt != null) TextButton(onClick = onTurnOff) { Text("Turn off") }
                Button(onClick = { onStart(minutes.roundToInt()) }, modifier = Modifier.padding(start = 8.dp)) {
                    Text(if (endsAt != null) "Restart" else "Start")
                }
            }
        }
    }
}

private fun minutesUntil(endsAt: Long) = ((endsAt - System.currentTimeMillis() + 59_999) / 60_000).toInt().coerceAtLeast(0)

private fun formatMinutes(minutes: Int) = when {
    minutes < 60 -> "$minutes minutes"
    minutes % 60 == 0 -> if (minutes == 60) "1 hour" else "${minutes / 60} hours"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

@Composable
private fun PlayerControls(
    state: PlaybackState,
    speed: Float,
    onToggle: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onSpeed: (Float) -> Unit,
) {
    var showSpeeds by remember { mutableStateOf(false) }
    val sentences = state.chapter?.sentences?.size ?: 0
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { if (sentences == 0) 0f else (state.position.sentence + 1f) / sentences },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box {
                    TextButton(onClick = { showSpeeds = true }) { Text("${formatSpeed(speed)}×") }
                    DropdownMenu(expanded = showSpeeds, onDismissRequest = { showSpeeds = false }) {
                        speeds.forEach { option ->
                            DropdownMenuItem(text = { Text("${formatSpeed(option)}×") }, onClick = {
                                onSpeed(option)
                                showSpeeds = false
                            })
                        }
                    }
                }
                IconButton(onClick = onPreviousChapter) { Icon(Icons.Default.SkipPrevious, contentDescription = "Previous chapter") }
                IconButton(onClick = onPreviousSentence) { Icon(Icons.Default.FastRewind, contentDescription = "Previous sentence") }
                FilledIconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
                    when {
                        state.isPlaying && state.isBuffering -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        state.isPlaying -> Icon(Icons.Default.Pause, contentDescription = "Pause")
                        else -> Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                    }
                }
                IconButton(onClick = onNextSentence) { Icon(Icons.Default.FastForward, contentDescription = "Next sentence") }
                IconButton(onClick = onNextChapter) { Icon(Icons.Default.SkipNext, contentDescription = "Next chapter") }
            }
        }
    }
}

private fun formatSpeed(speed: Float) = if (speed % 1f == 0f) "%.1f".format(speed) else "%.2f".format(speed).trimEnd('0')
