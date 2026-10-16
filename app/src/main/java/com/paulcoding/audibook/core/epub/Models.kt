package com.paulcoding.audibook.core.epub

data class EpubBook(
    val title: String,
    val author: String?,
    val language: String?,
    val coverHref: String?,
    val chapters: List<ChapterRef>,
)

data class ChapterRef(
    val index: Int,
    val title: String,
    val href: String,
)

data class Sentence(
    val text: String,
    val paragraph: Int,
    val range: IntRange,
)

data class Paragraph(
    val text: String,
    val isHeading: Boolean,
    val firstSentence: Int,
    val sentenceCount: Int,
)

data class ChapterContent(
    val index: Int,
    val title: String,
    val paragraphs: List<Paragraph>,
    val sentences: List<Sentence>,
)
