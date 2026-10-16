package com.paulcoding.audibook.core.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.jsoup.select.NodeFilter
import java.io.Closeable
import java.io.File
import java.net.URLDecoder
import java.util.Locale
import java.util.zip.ZipFile

class EpubParser(file: File) : Closeable {
    private val zip = ZipFile(file)

    val book: EpubBook by lazy { parseBook() }

    fun readBytes(href: String): ByteArray? =
        zip.getEntry(href)?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }

    fun readChapter(ref: ChapterRef): ChapterContent {
        val html = readBytes(ref.href)?.toString(Charsets.UTF_8).orEmpty()
        val locale = book.language?.let(Locale::forLanguageTag) ?: Locale.getDefault()
        val paragraphs = mutableListOf<Paragraph>()
        val sentences = mutableListOf<Sentence>()
        extractBlocks(html).forEach { block ->
            val ranges = SentenceSplitter.split(block.text, locale)
            if (ranges.isEmpty()) return@forEach
            val paragraphIndex = paragraphs.size
            paragraphs += Paragraph(block.text, block.isHeading, sentences.size, ranges.size)
            ranges.forEach { sentences += Sentence(block.text.substring(it), paragraphIndex, it) }
        }
        return ChapterContent(ref.index, ref.title, paragraphs, sentences)
    }

    override fun close() = zip.close()

    private fun readXml(href: String): Document? =
        readBytes(href)?.let { Jsoup.parse(it.toString(Charsets.UTF_8), "", Parser.xmlParser()) }

    private fun parseBook(): EpubBook {
        val container = readXml("META-INF/container.xml") ?: error("Missing META-INF/container.xml")
        val opfPath = container.selectFirst("rootfile")?.attr("full-path")?.takeIf { it.isNotBlank() }
            ?: error("No rootfile in container.xml")
        val opf = readXml(opfPath) ?: error("Missing package document $opfPath")
        val baseDir = opfPath.substringBeforeLast('/', "")

        val manifest = opf.select("manifest > item").associate { item ->
            item.attr("id") to ManifestItem(
                href = resolve(baseDir, item.attr("href")),
                mediaType = item.attr("media-type"),
                properties = item.attr("properties").split(' ').toSet(),
            )
        }

        val title = opf.selectFirst("metadata > dc|title, metadata > title")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "Untitled"
        val author = opf.selectFirst("metadata > dc|creator, metadata > creator")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() }
        val language = opf.selectFirst("metadata > dc|language, metadata > language")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() }

        val toc = parseToc(opf, manifest)
        var lastTitle: String? = null
        val chapters = opf.select("spine > itemref")
            .filter { it.attr("linear") != "no" }
            .mapNotNull { manifest[it.attr("idref")] }
            .filter { it.mediaType.contains("html") }
            .mapIndexed { index, item ->
                val chapterTitle = toc[item.href] ?: lastTitle ?: "Section ${index + 1}"
                lastTitle = chapterTitle
                ChapterRef(index, chapterTitle, item.href)
            }

        return EpubBook(title, author, language, findCover(opf, manifest), chapters)
    }

    private fun parseToc(opf: Document, manifest: Map<String, ManifestItem>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val nav = manifest.values.firstOrNull { "nav" in it.properties }
        if (nav != null) {
            val doc = readBytes(nav.href)?.let { Jsoup.parse(it.toString(Charsets.UTF_8)) }
            val navDir = nav.href.substringBeforeLast('/', "")
            val tocNav = doc?.select("nav")?.firstOrNull { it.attr("epub:type") == "toc" } ?: doc?.selectFirst("nav")
            tocNav?.select("a[href]")?.forEach { a ->
                val href = resolve(navDir, a.attr("href").substringBefore('#'))
                val label = a.text().trim()
                if (label.isNotEmpty()) result.putIfAbsent(href, label)
            }
        }
        if (result.isNotEmpty()) return result

        val ncxId = opf.selectFirst("spine")?.attr("toc")
        val ncx = manifest[ncxId] ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
        if (ncx != null) {
            val doc = readXml(ncx.href)
            val ncxDir = ncx.href.substringBeforeLast('/', "")
            doc?.select("navPoint")?.forEach { point ->
                val label = point.children().firstOrNull { it.normalName() == "navlabel" }?.text()?.trim()
                val src = point.children().firstOrNull { it.normalName() == "content" }?.attr("src")
                if (!label.isNullOrEmpty() && !src.isNullOrEmpty()) {
                    result.putIfAbsent(resolve(ncxDir, src.substringBefore('#')), label)
                }
            }
        }
        return result
    }

    private fun findCover(opf: Document, manifest: Map<String, ManifestItem>): String? {
        manifest.values.firstOrNull { "cover-image" in it.properties }?.let { return it.href }
        val coverId = opf.select("metadata > meta[name=cover]").attr("content")
        manifest[coverId]?.takeIf { it.mediaType.startsWith("image/") }?.let { return it.href }
        return manifest.entries.firstOrNull { (id, item) ->
            item.mediaType.startsWith("image/") && (id.contains("cover", true) || item.href.contains("cover", true))
        }?.value?.href
    }

    private data class ManifestItem(val href: String, val mediaType: String, val properties: Set<String>)

    private data class Block(val text: String, val isHeading: Boolean)

    companion object {
        private val blockTags = setOf(
            "p", "div", "section", "article", "blockquote", "li", "ul", "ol", "dt", "dd", "pre",
            "h1", "h2", "h3", "h4", "h5", "h6", "tr", "td", "th", "figcaption", "header", "footer", "aside",
        )
        private val headingTags = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        private val skipTags = setOf("script", "style", "head", "title", "svg", "math", "rt", "rp")
        private val whitespace = Regex("[\\s\\u00A0\\u200B]+")

        fun resolve(baseDir: String, href: String): String {
            val decoded = runCatching { URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") }.getOrDefault(href)
            val parts = ArrayDeque<String>()
            val full = if (decoded.startsWith("/") || baseDir.isEmpty()) decoded.trimStart('/') else "$baseDir/$decoded"
            full.split('/').forEach { part ->
                when (part) {
                    "", "." -> Unit
                    ".." -> parts.removeLastOrNull()
                    else -> parts.addLast(part)
                }
            }
            return parts.joinToString("/")
        }

        private fun isNoteRef(element: Element): Boolean {
            val type = element.attr("epub:type")
            if (type.contains("noteref") || type.contains("footnote") || type.contains("endnote")) return true
            return element.tagName() == "sup" && element.selectFirst("a") != null
        }

        private fun extractBlocks(html: String): List<Block> {
            val body = Jsoup.parse(html).body()
            val blocks = mutableListOf<Block>()
            val buffer = StringBuilder()
            var headingDepth = 0

            fun flush() {
                val text = buffer.toString().replace(whitespace, " ").trim()
                if (text.isNotEmpty()) blocks += Block(text, headingDepth > 0)
                buffer.clear()
            }

            body.filter(object : NodeFilter {
                override fun head(node: Node, depth: Int): NodeFilter.FilterResult {
                    when (node) {
                        is TextNode -> buffer.append(node.wholeText)
                        is Element -> {
                            val tag = node.normalName()
                            if (tag in skipTags || isNoteRef(node)) return NodeFilter.FilterResult.SKIP_ENTIRELY
                            if (tag == "br") flush()
                            if (tag in blockTags) flush()
                            if (tag in headingTags) headingDepth++
                        }
                    }
                    return NodeFilter.FilterResult.CONTINUE
                }

                override fun tail(node: Node, depth: Int): NodeFilter.FilterResult {
                    if (node is Element) {
                        val tag = node.normalName()
                        if (tag in blockTags) flush()
                        if (tag in headingTags) headingDepth--
                    }
                    return NodeFilter.FilterResult.CONTINUE
                }
            })
            flush()
            return blocks
        }
    }
}
