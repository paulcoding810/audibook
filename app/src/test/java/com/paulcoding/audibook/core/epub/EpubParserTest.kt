package com.paulcoding.audibook.core.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun epub(files: Map<String, String>, binary: Map<String, ByteArray> = emptyMap()): File {
        val file = temp.newFile("book.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            (files.mapValues { it.value.toByteArray() } + binary).forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
            }
        }
        return file
    }

    private val container = """
        <?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
        </container>
    """.trimIndent()

    private fun xhtml(body: String) =
        """<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>x</title><style>p{}</style></head><body>$body</body></html>"""

    private fun epub3() = epub(
        mapOf(
            "META-INF/container.xml" to container,
            "OEBPS/content.opf" to """
                <?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>The Test Book</dc:title>
                    <dc:creator>Jane Doe</dc:creator>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="cover" href="images/cover%20art.jpg" media-type="image/jpeg" properties="cover-image"/>
                    <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c1b" href="text/ch1b.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="notes" href="text/notes.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/><itemref idref="c1b"/><itemref idref="c2"/><itemref idref="notes" linear="no"/>
                  </spine>
                </package>
            """.trimIndent(),
            "OEBPS/nav.xhtml" to xhtml(
                """<nav epub:type="toc"><ol>
                    <li><a href="text/ch1.xhtml">Chapter One</a></li>
                    <li><a href="text/ch2.xhtml#start">Chapter Two</a></li>
                </ol></nav>"""
            ),
            "OEBPS/text/ch1.xhtml" to xhtml(
                """<h1>Chapter One</h1>
                   <p>It was a dark night. The <em>wind</em> howled<sup><a href="notes.xhtml#n1">1</a></sup>.</p>
                   <script>var x = 1;</script>
                   <div><p>Nested paragraph.</p><p>Another&#160;one.</p></div>
                   <p>Line one<br/>Line two</p>"""
            ),
            "OEBPS/text/ch1b.xhtml" to xhtml("<p>Continued text.</p>"),
            "OEBPS/text/ch2.xhtml" to xhtml("<h2 id=\"start\">Chapter Two</h2><p>The end.</p>"),
            "OEBPS/text/notes.xhtml" to xhtml("<p>Footnote.</p>"),
        ),
        binary = mapOf("OEBPS/images/cover art.jpg" to byteArrayOf(1, 2, 3)),
    )

    @Test
    fun parsesEpub3Metadata() {
        EpubParser(epub3()).use { parser ->
            val book = parser.book
            assertEquals("The Test Book", book.title)
            assertEquals("Jane Doe", book.author)
            assertEquals("en", book.language)
            assertEquals("OEBPS/images/cover art.jpg", book.coverHref)
            assertNotNull(parser.readBytes(book.coverHref!!))
        }
    }

    @Test
    fun usesSpineOrderAndTocTitles() {
        EpubParser(epub3()).use { parser ->
            val chapters = parser.book.chapters
            assertEquals(listOf("OEBPS/text/ch1.xhtml", "OEBPS/text/ch1b.xhtml", "OEBPS/text/ch2.xhtml"), chapters.map { it.href })
            assertEquals(listOf("Chapter One", "Chapter One", "Chapter Two"), chapters.map { it.title })
        }
    }

    @Test
    fun extractsParagraphsAndSentences() {
        EpubParser(epub3()).use { parser ->
            val chapter = parser.readChapter(parser.book.chapters[0])
            assertEquals(
                listOf("Chapter One", "It was a dark night. The wind howled.", "Nested paragraph.", "Another one.", "Line one", "Line two"),
                chapter.paragraphs.map { it.text },
            )
            assertTrue(chapter.paragraphs.first().isHeading)
            assertFalse(chapter.paragraphs[1].isHeading)
            assertEquals(
                listOf("Chapter One", "It was a dark night.", "The wind howled.", "Nested paragraph.", "Another one.", "Line one", "Line two"),
                chapter.sentences.map { it.text },
            )
            chapter.sentences.forEachIndexed { index, sentence ->
                val paragraph = chapter.paragraphs[sentence.paragraph]
                assertTrue(index in paragraph.firstSentence until paragraph.firstSentence + paragraph.sentenceCount)
                assertEquals(sentence.text, paragraph.text.substring(sentence.range))
            }
        }
    }

    @Test
    fun parsesEpub2WithNcx() {
        val file = epub(
            mapOf(
                "META-INF/container.xml" to container,
                "OEBPS/content.opf" to """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Old Book</dc:title>
                        <meta name="cover" content="cov"/>
                      </metadata>
                      <manifest>
                        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                        <item id="cov" href="cover.png" media-type="image/png"/>
                        <item id="a" href="a.html" media-type="application/xhtml+xml"/>
                        <item id="b" href="b.html" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine toc="ncx"><itemref idref="a"/><itemref idref="b"/></spine>
                    </package>
                """.trimIndent(),
                "OEBPS/toc.ncx" to """
                    <?xml version="1.0"?>
                    <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
                      <navPoint id="p1"><navLabel><text>Part I</text></navLabel><content src="a.html"/>
                        <navPoint id="p2"><navLabel><text>Inner</text></navLabel><content src="a.html#x"/></navPoint>
                      </navPoint>
                      <navPoint id="p3"><navLabel><text>Part II</text></navLabel><content src="b.html"/></navPoint>
                    </navMap></ncx>
                """.trimIndent(),
                "OEBPS/a.html" to xhtml("<p>A.</p>"),
                "OEBPS/b.html" to xhtml("<p>B.</p>"),
            ),
        )
        EpubParser(file).use { parser ->
            val book = parser.book
            assertEquals("Old Book", book.title)
            assertEquals("OEBPS/cover.png", book.coverHref)
            assertEquals(listOf("Part I", "Part II"), book.chapters.map { it.title })
        }
    }

    @Test
    fun resolvesRelativePaths() {
        assertEquals("OEBPS/images/a.png", EpubParser.resolve("OEBPS/text", "../images/a.png"))
        assertEquals("a b.html", EpubParser.resolve("", "a%20b.html"))
        assertEquals("x/c+d.html", EpubParser.resolve("x", "c+d.html"))
    }
}
