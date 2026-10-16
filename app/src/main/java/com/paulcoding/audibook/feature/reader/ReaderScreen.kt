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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paulcoding.audibook.core.epub.ChapterContent
import com.paulcoding.audibook.core.epub.Paragraph
import com.paulcoding.audibook.core.playback.PlaybackState
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
private val sleepOptions = listOf(5, 15, 30, 45, 60)
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
    val snackbar = remember { SnackbarHostState() }
    var showToc by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    val ready = state.book?.id == bookId

    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
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
                    Box {
                        IconButton(onClick = { showSleep = true }) {
                            Icon(
                                Icons.Default.Bedtime,
                                contentDescription = "Sleep timer",
                                tint = if (state.sleepTimerEndsAt != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            )
                        }
                        DropdownMenu(expanded = showSleep, onDismissRequest = { showSleep = false }) {
                            state.sleepTimerEndsAt?.let { endsAt ->
                                val left = ((endsAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0) + 1
                                DropdownMenuItem(text = { Text("Turn off ($left min left)") }, onClick = {
                                    viewModel.setSleepTimer(null)
                                    showSleep = false
                                })
                            }
                            sleepOptions.forEach { minutes ->
                                DropdownMenuItem(text = { Text("$minutes minutes") }, onClick = {
                                    viewModel.setSleepTimer(minutes)
                                    showSleep = false
                                })
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        bottomBar = {
            if (ready) PlayerControls(
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
        if (!ready || chapter == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            ChapterText(
                chapter = chapter,
                state = state,
                onSentenceClick = { viewModel.seekTo(chapter.index, it) },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showToc) {
        ModalBottomSheet(onDismissRequest = { showToc = false }) {
            val tocState = rememberLazyListState(initialFirstVisibleItemIndex = (state.position.chapter - 2).coerceAtLeast(0))
            LazyColumn(state = tocState) {
                itemsIndexed(state.chapters) { index, chapter ->
                    val current = index == state.position.chapter
                    ListItem(
                        headlineContent = {
                            Text(chapter.title, fontWeight = if (current) FontWeight.Bold else null)
                        },
                        leadingContent = { Text("${index + 1}", style = MaterialTheme.typography.labelMedium) },
                        colors = if (current) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ListItemDefaults.colors(),
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.seekTo(index, 0)
                            showToc = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterText(
    chapter: ChapterContent,
    state: PlaybackState,
    onSentenceClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    val currentSentence = if (state.position.chapter == chapter.index) state.position.sentence else -1
    val currentParagraph = chapter.sentences.getOrNull(currentSentence)?.paragraph ?: -1

    val userScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) follow = false
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(chapter.index) {
        follow = true
        listState.scrollToItem(currentParagraph.coerceAtLeast(0))
    }
    LaunchedEffect(currentParagraph, follow) {
        if (follow && currentParagraph >= 0) listState.revealParagraph(currentParagraph)
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().nestedScroll(userScroll),
        ) {
            itemsIndexed(chapter.paragraphs, key = { index, _ -> "${chapter.index}:$index" }) { _, paragraph ->
                ParagraphText(paragraph, chapter, currentSentence, onSentenceClick)
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
        }
        if (!follow && currentParagraph >= 0) {
            ExtendedFloatingActionButton(
                onClick = { follow = true },
                icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                text = { Text("Current sentence") },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
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
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
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
                FilledIconButton(onClick = onToggle, modifier = Modifier.size(56.dp)) {
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
