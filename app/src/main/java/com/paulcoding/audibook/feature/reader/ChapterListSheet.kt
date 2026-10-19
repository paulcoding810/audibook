package com.paulcoding.audibook.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.paulcoding.audibook.core.epub.ChapterRef
import kotlinx.coroutines.launch
import java.text.Normalizer
import kotlin.math.roundToInt

private const val FAST_SCROLL_MIN_ITEMS = 40

/** Chapter list with search (by number or title) and a fast-scroll thumb for long books. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterListSheet(
    chapters: List<ChapterRef>,
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(chapters, query) { filterChapters(chapters, query) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))

    LaunchedEffect(query) {
        if (query.isNotBlank()) listState.scrollToItem(0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxHeight(0.9f).imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Chapter number or title") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let { onSelect(it.index) } }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (results.isEmpty()) {
                Text(
                    "No chapters match \"$query\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.index }) { chapter ->
                        val isCurrent = chapter.index == current
                        ListItem(
                            headlineContent = {
                                Text(
                                    chapter.title,
                                    fontWeight = if (isCurrent) FontWeight.Bold else null,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = { Text("${chapter.index + 1}", style = MaterialTheme.typography.labelMedium) },
                            colors = if (isCurrent) {
                                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            } else {
                                ListItemDefaults.colors()
                            },
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(chapter.index) },
                        )
                    }
                }
                if (results.size >= FAST_SCROLL_MIN_ITEMS) {
                    FastScroller(listState, results.size, label = { "${results[it].index + 1}" })
                }
            }
        }
    }
}

/** Exact chapter number first, then titles containing the query (case- and accent-insensitive, so "chuong 5" finds "Chương 5"). */
internal fun filterChapters(chapters: List<ChapterRef>, query: String): List<ChapterRef> {
    val q = normalize(query.trim())
    if (q.isEmpty()) return chapters
    val byNumber = q.toIntOrNull()?.let { chapters.getOrNull(it - 1) }
    val byTitle = chapters.filter { normalize(it.title).contains(q) }
    return listOfNotNull(byNumber) + byTitle.filter { it != byNumber }
}

private fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace('đ', 'd')

/** Draggable thumb on the right edge; shows the item label in a bubble while dragging. */
@Composable
private fun FastScroller(state: LazyListState, itemCount: Int, label: (Int) -> String) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbHeight = 48.dp
    val thumbPx = with(density) { thumbHeight.toPx() }
    var trackHeight by remember { mutableIntStateOf(0) }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    val fraction by remember(itemCount) {
        derivedStateOf {
            val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            val maxFirst = (itemCount - visible).coerceAtLeast(1)
            (state.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
        }
    }

    fun jumpTo(y: Float) {
        val usable = (trackHeight - thumbPx).coerceAtLeast(1f)
        val index = (((y - thumbPx / 2) / usable).coerceIn(0f, 1f) * (itemCount - 1)).roundToInt()
        dragIndex = index
        scope.launch { state.scrollToItem(index) }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { trackHeight = it.height }) {
        val thumbY = (fraction * (trackHeight - thumbPx)).roundToInt()
        dragIndex?.let { index ->
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(-with(density) { 44.dp.roundToPx() }, thumbY) },
            ) {
                Text(label(index), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(28.dp)
                .pointerInput(itemCount) {
                    detectVerticalDragGestures(
                        onDragStart = { jumpTo(it.y) },
                        onDragEnd = { dragIndex = null },
                        onDragCancel = { dragIndex = null },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            jumpTo(change.position.y)
                        },
                    )
                },
        ) {
            Box(
                Modifier
                    .offset { IntOffset(0, thumbY) }
                    .align(Alignment.TopCenter)
                    .width(6.dp)
                    .height(thumbHeight)
                    .background(
                        if (dragIndex != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}
