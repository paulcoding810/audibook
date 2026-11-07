package com.paulcoding.audibook.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

enum class EngineType { SYSTEM, SHERPA }

data class TtsSettings(
    val engine: EngineType = EngineType.SYSTEM,
    val systemVoice: String? = null,
    val sherpaModelId: String? = null,
    val sherpaSpeaker: Int = 0,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    /** Skip contents lists and Project Gutenberg license text when reading aloud. */
    val skipFiller: Boolean = true,
)

data class SleepPrefs(
    val minutes: Int = 30,
    val autoStart: Boolean = false,
    val shakeToResume: Boolean = true,
)

/** Everything user-configurable, as stored in a `.audibook` backup. */
@Serializable
data class SettingsBackup(
    val engine: String = EngineType.SYSTEM.name,
    val systemVoice: String? = null,
    val sherpaModelId: String? = null,
    val sherpaSpeaker: Int = 0,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val sleepMinutes: Int = 30,
    val sleepAutoStart: Boolean = false,
    val sleepShakeToResume: Boolean = true,
    val skipFiller: Boolean = true,
)

class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<TtsSettings> = store.data.map { prefs ->
        TtsSettings(
            engine = prefs[ENGINE]?.let { runCatching { EngineType.valueOf(it) }.getOrNull() } ?: EngineType.SYSTEM,
            systemVoice = prefs[SYSTEM_VOICE],
            sherpaModelId = prefs[SHERPA_MODEL],
            sherpaSpeaker = prefs[SHERPA_SPEAKER] ?: 0,
            speed = prefs[SPEED] ?: 1f,
            pitch = prefs[PITCH] ?: 1f,
            skipFiller = prefs[SKIP_FILLER] ?: true,
        )
    }.distinctUntilChanged()

    suspend fun setEngine(engine: EngineType) = store.edit { it[ENGINE] = engine.name }

    suspend fun setSystemVoice(name: String?) = store.edit {
        if (name == null) it.remove(SYSTEM_VOICE) else it[SYSTEM_VOICE] = name
    }

    suspend fun setSherpaModel(id: String?) = store.edit {
        if (id == null) it.remove(SHERPA_MODEL) else it[SHERPA_MODEL] = id
        it[SHERPA_SPEAKER] = 0
    }

    suspend fun setSherpaSpeaker(speaker: Int) = store.edit { it[SHERPA_SPEAKER] = speaker }

    suspend fun setSpeed(speed: Float) = store.edit { it[SPEED] = speed }

    suspend fun setPitch(pitch: Float) = store.edit { it[PITCH] = pitch }

    suspend fun setSkipFiller(enabled: Boolean) = store.edit { it[SKIP_FILLER] = enabled }

    val sleepPrefs: Flow<SleepPrefs> = store.data.map { prefs ->
        SleepPrefs(
            minutes = prefs[SLEEP_MINUTES] ?: 30,
            autoStart = prefs[SLEEP_AUTO] ?: false,
            shakeToResume = prefs[SLEEP_SHAKE] ?: true,
        )
    }.distinctUntilChanged()

    suspend fun setSleepMinutes(minutes: Int) = store.edit { it[SLEEP_MINUTES] = minutes }

    suspend fun setSleepAutoStart(enabled: Boolean) = store.edit { it[SLEEP_AUTO] = enabled }

    suspend fun setSleepShakeToResume(enabled: Boolean) = store.edit { it[SLEEP_SHAKE] = enabled }

    suspend fun exportSettings(): SettingsBackup {
        val tts = settings.first()
        val sleep = sleepPrefs.first()
        return SettingsBackup(
            engine = tts.engine.name,
            systemVoice = tts.systemVoice,
            sherpaModelId = tts.sherpaModelId,
            sherpaSpeaker = tts.sherpaSpeaker,
            speed = tts.speed,
            pitch = tts.pitch,
            sleepMinutes = sleep.minutes,
            sleepAutoStart = sleep.autoStart,
            sleepShakeToResume = sleep.shakeToResume,
            skipFiller = tts.skipFiller,
        )
    }

    suspend fun importSettings(backup: SettingsBackup) = store.edit {
        it[ENGINE] = backup.engine
        if (backup.systemVoice == null) it.remove(SYSTEM_VOICE) else it[SYSTEM_VOICE] = backup.systemVoice
        if (backup.sherpaModelId == null) it.remove(SHERPA_MODEL) else it[SHERPA_MODEL] = backup.sherpaModelId
        it[SHERPA_SPEAKER] = backup.sherpaSpeaker
        it[SPEED] = backup.speed
        it[PITCH] = backup.pitch
        it[SLEEP_MINUTES] = backup.sleepMinutes
        it[SLEEP_AUTO] = backup.sleepAutoStart
        it[SLEEP_SHAKE] = backup.sleepShakeToResume
        it[SKIP_FILLER] = backup.skipFiller
    }

    private companion object {
        val ENGINE = stringPreferencesKey("engine")
        val SYSTEM_VOICE = stringPreferencesKey("system_voice")
        val SHERPA_MODEL = stringPreferencesKey("sherpa_model")
        val SHERPA_SPEAKER = intPreferencesKey("sherpa_speaker")
        val SPEED = floatPreferencesKey("speed")
        val PITCH = floatPreferencesKey("pitch")
        val SLEEP_MINUTES = intPreferencesKey("sleep_minutes")
        val SLEEP_AUTO = booleanPreferencesKey("sleep_auto")
        val SLEEP_SHAKE = booleanPreferencesKey("sleep_shake")
        val SKIP_FILLER = booleanPreferencesKey("skip_filler")
    }
}
