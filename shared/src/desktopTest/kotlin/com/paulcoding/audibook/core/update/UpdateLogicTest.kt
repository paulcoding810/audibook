package com.paulcoding.audibook.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(isNewer("1.0.1", "1.0"))
        assertTrue(isNewer("v1.10", "1.2"))
        assertTrue(isNewer("2.0", "1.9.9"))
        assertFalse(isNewer("1.2", "v1.10"))
        assertFalse(isNewer("v1.0", "1.0"))
        assertFalse(isNewer("1.0.0", "1.0"))
        assertFalse(isNewer("1.0", "1.0.1"))
    }

    @Test
    fun ignoresNonNumericSuffixes() {
        assertTrue(isNewer("1.1-beta", "1.0"))
        assertFalse(isNewer("garbage", "1.0"))
    }

    private val assets = listOf(
        "AudiBook-v1.1-arm64-v8a.apk",
        "AudiBook-v1.1-armeabi-v7a.apk",
        "AudiBook-v1.1-x86.apk",
        "AudiBook-v1.1-x86_64.apk",
        "AudiBook-v1.1-universal.apk",
        "checksums.txt",
    )

    @Test
    fun picksPreferredAbi() {
        assertEquals("AudiBook-v1.1-arm64-v8a.apk", pickApk(assets, listOf("arm64-v8a", "armeabi-v7a", "armeabi")))
        assertEquals("AudiBook-v1.1-armeabi-v7a.apk", pickApk(assets, listOf("armeabi-v7a", "armeabi")))
        assertEquals("AudiBook-v1.1-x86_64.apk", pickApk(assets, listOf("x86_64", "x86")))
    }

    @Test
    fun acceptsReleaseSuffix() {
        val names = listOf("hviewer-v1.6.1-arm64-v8a-release.apk", "hviewer-v1.6.1-x86_64-release.apk", "hviewer-v1.6.1-universal-release.apk")
        assertEquals("hviewer-v1.6.1-arm64-v8a-release.apk", pickApk(names, listOf("arm64-v8a")))
        assertEquals("hviewer-v1.6.1-universal-release.apk", pickApk(names, listOf("armeabi-v7a")))
    }

    @Test
    fun stripsMarkdownFromReleaseNotes() {
        val markdown = "## What's new\r\n**Changes**\n\n\n- Faster `sync`\n* Fix [crash](https://x/1)\n"
        assertEquals("What's new\nChanges\n\n• Faster sync\n• Fix crash", plainReleaseNotes(markdown.replace("\r", "")))
    }

    @Test
    fun fallsBackToUniversal() {
        assertEquals("AudiBook-v1.1-universal.apk", pickApk(assets, listOf("riscv64")))
        assertNull(pickApk(listOf("notes.txt"), listOf("arm64-v8a")))
    }
}
