package com.paulcoding.audibook.core.backup

import com.paulcoding.audibook.core.data.SettingsBackup
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupManifestTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun roundTrips() {
        val manifest = BackupManifest(
            exportedAt = 1_700_000_000_000,
            books = listOf(
                BackupBook("ab".repeat(32), "Alice", "Lewis Carroll", "en", 15, 3, 42, 1_699_999_000_000, 1_600_000_000_000, "books/${"ab".repeat(32)}.epub"),
                BackupBook("cd".repeat(32), "Missing file", chapterCount = 10),
            ),
            settings = SettingsBackup(engine = "SHERPA", sherpaModelId = "kokoro-int8-en-v0_19", speed = 1.55f, sleepMinutes = 45),
            models = listOf("kokoro-int8-en-v0_19"),
        )
        val decoded = json.decodeFromString(BackupManifest.serializer(), json.encodeToString(BackupManifest.serializer(), manifest))
        assertEquals(manifest, decoded)
        assertNull(decoded.books[1].file)
    }

    @Test
    fun toleratesFieldsAddedByNewerVersions() {
        val text = """{"version":1,"exportedAt":1,"books":[{"fingerprint":"x","title":"T","futureField":true}],"settings":{"speed":2.0,"newSetting":"y"}}"""
        val decoded = json.decodeFromString(BackupManifest.serializer(), text)
        assertEquals("T", decoded.books.single().title)
        assertEquals(2f, decoded.settings.speed)
        assertEquals(30, decoded.settings.sleepMinutes)
    }
}
