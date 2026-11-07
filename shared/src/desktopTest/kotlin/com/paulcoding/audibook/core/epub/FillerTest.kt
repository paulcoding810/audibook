package com.paulcoding.audibook.core.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class FillerTest {
    private fun paragraphs(vararg lines: Pair<String, Int?>, headings: Set<Int> = emptySet()) =
        lines.mapIndexed { i, (text, target) -> Paragraph(text, i in headings, i, 1, target) }

    @Test
    fun skipsGutenbergHeaderAndFooter() {
        val p = paragraphs(
            "The Project Gutenberg eBook of Alice" to null,
            "This eBook is for the use of anyone anywhere." to null,
            "*** START OF THE PROJECT GUTENBERG EBOOK ALICE ***" to null,
            "Alice was beginning to get very tired." to null,
            "*** END OF THE PROJECT GUTENBERG EBOOK ALICE ***" to null,
            "Updated editions will replace the previous one." to null,
        )
        assertEquals(setOf(0, 1, 2, 4, 5), Filler.indices(p))
    }

    @Test
    fun skipsContentsTableWithTitleCellsAndItsHeading() {
        val p = paragraphs(
            "by Lewis Carroll" to null,
            "Contents" to null,
            "CHAPTER I." to 1, "Down the Rabbit-Hole" to null,
            "CHAPTER II." to 2, "The Pool of Tears" to null,
            "CHAPTER III." to 3, "A Caucus-Race and a Long Tale" to null,
            "CHAPTER I." to null,
            "Alice was beginning to get very tired of sitting by her sister on the bank.".repeat(2) to null,
            headings = setOf(1, 8),
        )
        assertEquals((1..7).toSet(), Filler.indices(p))
    }

    @Test
    fun keepsProseBetweenScatteredLinks() {
        val long = "A paragraph of real story text that is clearly longer than any contents entry would ever be, so it stays."
        val p = paragraphs("One" to 1, "$long $long" to null, "Two" to 2, "Three" to 3)
        assertEquals(setOf(0, 2, 3), Filler.indices(p))
    }

    @Test
    fun ordinaryChapterHasNoFiller() {
        assertEquals(emptySet<Int>(), Filler.indices(paragraphs("Chapter 1" to null, "It was a dark night." to null, headings = setOf(0))))
    }
}
