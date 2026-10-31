package com.paulcoding.audibook.core.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SentenceSplitterTest {
    private fun split(text: String) = SentenceSplitter.split(text, Locale.US).map { text.substring(it) }

    @Test
    fun splitsSimpleSentences() {
        assertEquals(
            listOf("It was dark.", "The wind howled!", "Was anyone there?"),
            split("It was dark. The wind howled! Was anyone there?"),
        )
    }

    @Test
    fun keepsAbbreviationsTogether() {
        assertEquals(
            listOf("Mr. Smith met Dr. Watson on Baker St. at noon.", "They talked."),
            split("Mr. Smith met Dr. Watson on Baker St. at noon. They talked."),
        )
    }

    @Test
    fun keepsInitialsTogether() {
        assertEquals(listOf("J. R. R. Tolkien wrote it."), split("J. R. R. Tolkien wrote it."))
    }

    @Test
    fun rangesAreTrimmed() {
        val text = "  Hello there.   General Kenobi.  "
        SentenceSplitter.split(text, Locale.US).forEach { range ->
            assertTrue(!text[range.first].isWhitespace() && !text[range.last].isWhitespace())
        }
    }

    @Test
    fun splitsLongSentencesAtSoftBreaks() {
        val clause = "and the river kept running past the old mill where nobody worked anymore"
        val text = List(10) { clause }.joinToString(", ") + "."
        val parts = split(text)
        assertTrue(parts.size > 1)
        parts.forEach { assertTrue(it.length <= SentenceSplitter.MAX_LENGTH) }
        assertEquals(text.replace(" ", ""), parts.joinToString("").replace(" ", ""))
    }

    @Test
    fun splitsLongTextWithoutBreaks() {
        val text = "x".repeat(SentenceSplitter.MAX_LENGTH * 2 + 10)
        val parts = split(text)
        assertEquals(3, parts.size)
        assertEquals(text, parts.joinToString(""))
    }

    @Test
    fun emptyTextHasNoSentences() {
        assertEquals(emptyList<String>(), split("   "))
    }
}
