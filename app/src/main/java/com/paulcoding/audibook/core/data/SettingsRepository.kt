package com.paulcoding.audibook.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class EngineType { SYSTEM, SHERPA }

data class TtsSettings(
    val engine: EngineType = EngineType.SYSTEM,
    val systemVoice: String? = null,
    val sherpaModelId: String? = null,
    val sherpaSpeaker: Int = 0,
    val speed: Float = 1f,
    val pitch: Float = 1f,
)

data class SleepPrefs(
    val minutes: Int = 30,
    val autoStart: Boolean = false,
    val shakeToResume: Boolean = true,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(context: Context) {
    private val store = context.dataStore

    val settings: Flow<TtsSettings> = store.data.map { prefs ->
        TtsSettings(
            engine = prefs[ENGINE]?.let { runCatching { EngineType.valueOf(it) }.getOrNull() } ?: EngineType.SYSTEM,
            systemVoice = prefs[SYSTEM_VOICE],
            sherpaModelId = prefs[SHERPA_MODEL],
            sherpaSpeaker = prefs[SHERPA_SPEAKER] ?: 0,
            speed = prefs[SPEED] ?: 1f,
            pitch = prefs[PITCH] ?: 1f,
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
    }
}
