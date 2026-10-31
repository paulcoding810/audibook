package com.paulcoding.audibook.core.tts

import com.paulcoding.audibook.core.data.TtsSettings
import com.paulcoding.audibook.core.platform.AppDirs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/** The OS speech synthesizer, run as a command that writes a WAV file: `say` (macOS), SAPI via PowerShell (Windows), espeak-ng (Linux). */
class DesktopSystemTtsEngine(dirs: AppDirs, private val output: PcmOutputFactory) : SystemSpeechEngine {
    private val workDir = File(dirs.cache, "tts")
    private val counter = AtomicLong()
    private val backend: Backend? = Backend.detect()
    private var cachedVoices: List<SystemVoice>? = null

    override val name get() = backend?.title ?: "System voices"
    override val description get() = if (backend != null) "Your computer's built-in speech synthesizer." else "No speech synthesizer found. On Linux, install espeak-ng."
    override val supportsPitch get() = backend == Backend.ESPEAK
    override val openSettings: (() -> Unit)? = null

    override suspend fun voices(): List<SystemVoice> = withContext(Dispatchers.IO) {
        cachedVoices ?: runCatching { backend?.voices().orEmpty() }.getOrDefault(emptyList())
            .sortedWith(compareBy({ it.locale.displayName }, { it.name }))
            .also { cachedVoices = it }
    }

    override suspend fun speak(
        first: Utterance,
        settings: TtsSettings,
        language: String?,
        next: suspend (Utterance) -> Utterance?,
        onStart: (Utterance) -> Unit,
    ) {
        val backend = backend ?: throw TtsException("No speech synthesizer found. Install espeak-ng, or use an on-device AI voice.")
        val voice = settings.systemVoice ?: language?.let { tag ->
            val wanted = Locale.forLanguageTag(tag)
            voices().filter { it.locale.language == wanted.language }
                .minByOrNull { if (wanted.country.isNotEmpty() && it.locale.country != wanted.country) 1 else 0 }?.name
        }
        playPipelined(first, next, onStart, BUFFERED_SENTENCES, output, Dispatchers.IO) { utterance ->
            synthesize(backend, utterance.text, voice, settings)
        }
    }

    private suspend fun synthesize(backend: Backend, text: String, voice: String?, settings: TtsSettings): PcmAudio {
        workDir.mkdirs()
        val id = counter.getAndIncrement()
        val textFile = File(workDir, "s$id.txt").apply { writeText(text) }
        val wav = File(workDir, "s$id.wav")
        val process = ProcessBuilder(backend.command(textFile, wav, voice, settings)).redirectErrorStream(true).start()
        try {
            while (!process.waitFor(20, TimeUnit.MILLISECONDS)) coroutineContext.ensureActive()
            if (process.exitValue() != 0 || !wav.exists()) {
                val message = process.inputStream.bufferedReader().readText().trim().take(200)
                throw TtsException("Speech synthesizer failed${if (message.isNotEmpty()) ": $message" else ""}")
            }
            return WavReader.decode(wav.readBytes())
        } finally {
            if (process.isAlive) process.destroyForcibly()
            textFile.delete()
            wav.delete()
        }
    }

    private enum class Backend(val title: String) {
        SAY("macOS voices") {
            override fun command(text: File, wav: File, voice: String?, settings: TtsSettings) = buildList {
                addAll(listOf("say", "-o", wav.absolutePath, "--data-format=LEI16@22050", "-r", "${(180 * settings.speed).toInt()}"))
                if (voice != null) addAll(listOf("-v", voice))
                addAll(listOf("-f", text.absolutePath))
            }

            override fun voices() = run("say", "-v", "?").mapNotNull { line ->
                SAY_VOICE.find(line)?.let { SystemVoice(it.groupValues[1].trim(), locale(it.groupValues[2]), false, 0) }
            }
        },
        ESPEAK("eSpeak NG") {
            override fun command(text: File, wav: File, voice: String?, settings: TtsSettings) = buildList {
                addAll(listOf(executable, "-w", wav.absolutePath, "-s", "${(175 * settings.speed).toInt()}", "-p", "${(50 * settings.pitch).toInt().coerceIn(0, 99)}"))
                if (voice != null) addAll(listOf("-v", voice))
                addAll(listOf("-f", text.absolutePath))
            }

            override fun voices() = run(executable, "--voices").drop(1).mapNotNull { line ->
                val columns = line.trim().split(Regex("\\s+"))
                columns.getOrNull(1)?.let { SystemVoice(it, locale(it), false, 0) }
            }
        },
        SAPI("Windows voices") {
            override fun command(text: File, wav: File, voice: String?, settings: TtsSettings): List<String> {
                val rate = ((settings.speed - 1f) * 10).toInt().coerceIn(-10, 10)
                val select = voice?.let { "\$s.SelectVoice('${it.replace("'", "''")}');" }.orEmpty()
                val script = "Add-Type -AssemblyName System.Speech; \$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                    "\$s.Rate = $rate; $select \$s.SetOutputToWaveFile('${wav.absolutePath}'); " +
                    "\$s.Speak([IO.File]::ReadAllText('${text.absolutePath}')); \$s.Dispose()"
                return listOf("powershell", "-NoProfile", "-Command", script)
            }

            override fun voices() = run(
                "powershell", "-NoProfile", "-Command",
                "Add-Type -AssemblyName System.Speech; (New-Object System.Speech.Synthesis.SpeechSynthesizer).GetInstalledVoices() | " +
                    "% { \$_.VoiceInfo.Name + '|' + \$_.VoiceInfo.Culture.Name }",
            ).mapNotNull { line ->
                line.split('|').takeIf { it.size == 2 }?.let { SystemVoice(it[0], Locale.forLanguageTag(it[1]), false, 0) }
            }
        };

        var executable = "espeak-ng"

        abstract fun command(text: File, wav: File, voice: String?, settings: TtsSettings): List<String>
        abstract fun voices(): List<SystemVoice>

        companion object {
            private val SAY_VOICE = Regex("^(.+?)\\s+([a-z]{2,3}[_-][A-Za-z0-9]+)\\s+#")

            fun detect(): Backend? {
                val os = System.getProperty("os.name").lowercase()
                return when {
                    os.contains("mac") -> SAY
                    os.contains("win") -> SAPI
                    else -> listOf("espeak-ng", "espeak").firstOrNull(::exists)?.let { ESPEAK.apply { executable = it } }
                }
            }

            private fun exists(command: String) = runCatching { run(command, "--version"); true }.getOrDefault(false)

            private fun locale(code: String): Locale = Locale.forLanguageTag(code.replace('_', '-'))

            private fun run(vararg command: String): List<String> {
                val process = ProcessBuilder(*command).redirectErrorStream(true).start()
                val lines = process.inputStream.bufferedReader().readLines()
                process.waitFor()
                return lines
            }
        }
    }

    private companion object {
        const val BUFFERED_SENTENCES = 3
    }
}
