package com.wxn.bookread.data.source.local

import android.content.Context
import androidx.core.app.LocaleManagerCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxn.base.bean.TTSEngineType
import com.wxn.bookread.data.model.preference.TtsPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.ttsPreferencesDataStore by preferencesDataStore(name = "tts_preferences")

class TtsPreferencesUtil @Inject constructor(
    val context: Context
) {
    private val dataStore = context.ttsPreferencesDataStore

    companion object {
        private const val DEFAULT_STORYVOICE_MODEL = "taiwan-story-voice"
        val SPEED = floatPreferencesKey("speed")
        val PITCH = floatPreferencesKey("spitch")
        val LANGUAGE = stringPreferencesKey("language")

        val ENGINE_TYPE = stringPreferencesKey("engine_type")

        val AI_ENGINE_MODEL = stringPreferencesKey("engine_model")

        val AI_ENGINE_SPEAKER = intPreferencesKey("engine_speaker")

        val FIRST_AI_TTS = booleanPreferencesKey("is_first_ai_tts")

        val defaultPreferences = TtsPreferences(
            localeCode = "" , //"en",
            speed = 1.0f,
            pitch = 1.0f,

            ttsEngineType = TTSEngineType.OFFLINE_NEURAL_AI,
            selectedTTSModel = DEFAULT_STORYVOICE_MODEL,
            selectedSpeaker = 0,
            isFirstAiTtsSelection = false
        )
    }

    val ttsPreferencesFlow: Flow<TtsPreferences> = dataStore.data.map { preferences ->

        var locale = preferences[LANGUAGE] ?:  defaultPreferences.localeCode

        if (locale.isEmpty()) {
            val systemLocales = LocaleManagerCompat.getSystemLocales(context)
            val systemLocale = if (!systemLocales.isEmpty) {
                systemLocales.get(0)
            }else {
                null
            }
            if (systemLocale != null) {
                locale = systemLocale.toLanguageTag()
            }
        }

        val strType = preferences[ENGINE_TYPE]
        val engineType = when (strType) {
            null -> defaultPreferences.ttsEngineType
            TTSEngineType.SYSTEM.name -> TTSEngineType.SYSTEM
            else -> TTSEngineType.OFFLINE_NEURAL_AI
        }

        TtsPreferences(
            localeCode = locale,
            speed = preferences[SPEED] ?: defaultPreferences.speed,
            pitch = preferences[PITCH] ?: defaultPreferences.pitch,
            ttsEngineType = engineType,
            selectedTTSModel = preferences[AI_ENGINE_MODEL] ?: defaultPreferences.selectedTTSModel,
            selectedSpeaker = preferences[AI_ENGINE_SPEAKER] ?: defaultPreferences.selectedSpeaker,
            isFirstAiTtsSelection = preferences[FIRST_AI_TTS] ?: defaultPreferences.isFirstAiTtsSelection
        )
    }

    suspend fun updatePreferences(newPreferences: TtsPreferences) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE] = newPreferences.localeCode
            preferences[SPEED] = newPreferences.speed
            preferences[PITCH] = newPreferences.pitch

            preferences[ENGINE_TYPE] = newPreferences.ttsEngineType.name
            preferences[AI_ENGINE_MODEL] = newPreferences.selectedTTSModel.orEmpty()
            preferences[AI_ENGINE_SPEAKER] = newPreferences.selectedSpeaker
            preferences[FIRST_AI_TTS] = newPreferences.isFirstAiTtsSelection
        }
    }

    suspend fun resetSelectedModel() {
        dataStore.edit { prefs ->
            prefs[AI_ENGINE_MODEL] = ""
            prefs[AI_ENGINE_SPEAKER] = 0
        }
    }
}
