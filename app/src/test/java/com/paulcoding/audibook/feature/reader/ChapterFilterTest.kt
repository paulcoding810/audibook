package com.paulcoding.audibook.feature.reader

import com.paulcoding.audibook.core.epub.ChapterRef
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterFilterTest {
    private val chapters = (1..400).map { ChapterRef(it - 1, "Chương $it: Đầu đề $it", "c$it.xhtml") }

    @Test
    fun emptyQueryKeepsAll() {
        assertEquals(400, filterChapters(chapters, "  ").size)
    }

    @Test
    fun numberJumpsToThatChapterFirst() {
        val result = filterChapters(chapters, "35")
        assertEquals(34, result.first().index)
        assertEquals(result.size, result.distinct().size)
    }

    @Test
    fun matchesTitlesIgnoringCaseAndAccents() {
        assertEquals(listOf(349), filterChapters(chapters, "chuong 350:").map { it.index })
        assertEquals(listOf(9) + (99..108), filterChapters(chapters, "DAU DE 10").map { it.index })
    }

    @Test
    fun outOfRangeNumberFallsBackToTitles() {
        assertEquals(0, filterChapters(chapters, "9999").size)
    }
}
