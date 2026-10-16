package com.paulcoding.audibook.core.epub

import java.text.BreakIterator
import java.util.Locale

object SentenceSplitter {
    const val MAX_LENGTH = 300

    private val abbreviations = setOf(
        "mr.", "mrs.", "ms.", "dr.", "st.", "jr.", "sr.", "prof.", "vs.", "etc.", "e.g.", "i.e.", "no.", "mt.", "capt.", "col.", "gen.", "lt.", "rev.",
    )
    private val softBreaks = charArrayOf(',', ';', ':', '—', '–', ')')

    fun split(text: String, locale: Locale = Locale.getDefault()): List<IntRange> {
        val iterator = BreakIterator.getSentenceInstance(locale).apply { setText(text) }
        val raw = mutableListOf<IntRange>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            trimmed(text, start, end)?.let(raw::add)
            start = end
            end = iterator.next()
        }
        return mergeAbbreviations(text, raw).flatMap { splitLong(text, it) }
    }

    private fun trimmed(text: String, start: Int, end: Int): IntRange? {
        var s = start
        var e = end
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        return if (s < e) s until e else null
    }

    private fun mergeAbbreviations(text: String, ranges: List<IntRange>): List<IntRange> {
        val result = mutableListOf<IntRange>()
        for (range in ranges) {
            val previous = result.lastOrNull()
            if (previous != null && endsWithAbbreviation(text.substring(previous))) {
                result[result.lastIndex] = previous.first..range.last
            } else {
                result += range
            }
        }
        return result
    }

    private fun endsWithAbbreviation(sentence: String): Boolean {
        val lastWord = sentence.substringAfterLast(' ').lowercase()
        return lastWord in abbreviations || (lastWord.length == 2 && lastWord[0].isLetter() && lastWord[1] == '.')
    }

    private fun splitLong(text: String, range: IntRange): List<IntRange> {
        val parts = mutableListOf<IntRange>()
        var start = range.first
        while (range.last + 1 - start > MAX_LENGTH) {
            val windowEnd = start + MAX_LENGTH
            val window = text.substring(start, windowEnd)
            val cut = window.lastIndexOfAny(softBreaks).takeIf { it > MAX_LENGTH / 3 }
                ?: window.lastIndexOf(' ').takeIf { it > 0 }
                ?: (MAX_LENGTH - 1)
            trimmed(text, start, start + cut + 1)?.let(parts::add)
            start += cut + 1
        }
        trimmed(text, start, range.last + 1)?.let(parts::add)
        return parts
    }
}
