package com.paulcoding.audibook.core.epub

/** Finds paragraphs not worth reading aloud: Project Gutenberg's license header and footer, and contents entries. */
object Filler {
    private val gutenbergStart = Regex("^\\*{3}\\s*START OF (THE|THIS) PROJECT GUTENBERG", RegexOption.IGNORE_CASE)
    private val gutenbergEnd = Regex("^\\*{3}\\s*END OF (THE|THIS) PROJECT GUTENBERG", RegexOption.IGNORE_CASE)

    /** A contents block needs this many entries; fewer are just stray links (each still skipped). */
    private const val MIN_ENTRIES = 3

    /** Lines between entries (e.g. a title cell beside each "CHAPTER I." link) are at most this long. */
    private const val MAX_ENTRY_LENGTH = 120

    /** Indices of [paragraphs] to skip when reading aloud. */
    fun indices(paragraphs: List<Paragraph>): Set<Int> = buildSet {
        val start = paragraphs.indexOfFirst { gutenbergStart.containsMatchIn(it.text) }
        if (start >= 0) addAll(0..start)
        val end = paragraphs.indexOfFirst { gutenbergEnd.containsMatchIn(it.text) }
        if (end >= 0) addAll(end..paragraphs.lastIndex)
        addAll(contents(paragraphs))
    }

    private fun contents(paragraphs: List<Paragraph>): Set<Int> {
        val entries = paragraphs.indices.filter { paragraphs[it].target != null }
        if (entries.size < MIN_ENTRIES) return entries.toSet()
        val first = entries.first()
        val last = entries.last()
        val between = (first..last).filter { paragraphs[it].target == null }
        if (between.any { paragraphs[it].text.length > MAX_ENTRY_LENGTH }) return entries.toSet()

        // The longest run of lines between two entries; the same pattern may follow the last entry.
        var longestRun = 0
        var run = 0
        for (i in first..last) {
            run = if (paragraphs[i].target == null) run + 1 else 0
            longestRun = maxOf(longestRun, run)
        }
        var end = last
        repeat(longestRun) {
            val next = paragraphs.getOrNull(end + 1)
            if (next != null && next.target == null && !next.isHeading && next.text.length <= MAX_ENTRY_LENGTH) end++
        }
        val begin = if (first > 0 && paragraphs[first - 1].isHeading) first - 1 else first
        return (begin..end).toSet()
    }
}
