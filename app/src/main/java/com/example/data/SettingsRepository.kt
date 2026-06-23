package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val USER_NAME_KEY = stringPreferencesKey("user_name")
        val API_KEY = stringPreferencesKey("api_key")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val THEME_MODE_KEY = intPreferencesKey("theme_mode")
        val RECORD_MODE_KEY = intPreferencesKey("record_mode") 
        val AI_PROVIDER_KEY = intPreferencesKey("ai_provider") // 0 = Gemini, 1 = Groq
        
        // --- GLOBAL AI PREFERENCES ---
        val AI_LANGUAGE_KEY = stringPreferencesKey("ai_language")
        val AI_TASK_KEY = intPreferencesKey("ai_task") // 0: Tidy Up, 1: Summarize, 2: Analyze
        val AI_FORMAT_KEY = intPreferencesKey("ai_format") // 0: Paragraphs, 1: Bullets

        // --- TUTORIAL / COACH MARKS FLAGS ---
        val HAS_SEEN_RECORD_TOUR = booleanPreferencesKey("has_seen_record_tour")
        val HAS_SEEN_HISTORY_TOUR = booleanPreferencesKey("has_seen_history_tour")
        val HAS_SEEN_RESULT_TOUR = booleanPreferencesKey("has_seen_result_tour")
    }

    val userNameFlow: Flow<String> = context.dataStore.data.map { it[USER_NAME_KEY] ?: "" }
    val geminiApiKeyFlow: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val groqApiKeyFlow: Flow<String> = context.dataStore.data.map { it[GROQ_API_KEY] ?: "" }
    val themeModeFlow: Flow<Int> = context.dataStore.data.map { it[THEME_MODE_KEY] ?: 0 }
    val recordModeFlow: Flow<Int> = context.dataStore.data.map { it[RECORD_MODE_KEY] ?: 0 } 
    
    // Default fallback diubah jadi 1 (Groq) karena lebih ramah untuk user mainstream
    val aiProviderFlow: Flow<Int> = context.dataStore.data.map { it[AI_PROVIDER_KEY] ?: 1 }

    // Default: English, Tidy Up (0), Paragraphs (0)
    val aiLanguageFlow: Flow<String> = context.dataStore.data.map { it[AI_LANGUAGE_KEY] ?: "English" }
    val aiTaskFlow: Flow<Int> = context.dataStore.data.map { it[AI_TASK_KEY] ?: 0 }
    val aiFormatFlow: Flow<Int> = context.dataStore.data.map { it[AI_FORMAT_KEY] ?: 0 }

    // Tutorial Flags
    val hasSeenRecordTourFlow: Flow<Boolean> = context.dataStore.data.map { it[HAS_SEEN_RECORD_TOUR] ?: false }
    val hasSeenHistoryTourFlow: Flow<Boolean> = context.dataStore.data.map { it[HAS_SEEN_HISTORY_TOUR] ?: false }
    val hasSeenResultTourFlow: Flow<Boolean> = context.dataStore.data.map { it[HAS_SEEN_RESULT_TOUR] ?: false }

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { it[USER_NAME_KEY] = name }
    }

    suspend fun saveGeminiApiKey(key: String) {
        context.dataStore.edit { it[API_KEY] = key }
    }

    suspend fun saveGroqApiKey(key: String) {
        context.dataStore.edit { it[GROQ_API_KEY] = key }
    }

    suspend fun saveThemeMode(mode: Int) {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode }
    }

    suspend fun saveRecordMode(mode: Int) {
        context.dataStore.edit { it[RECORD_MODE_KEY] = mode }
    }

    suspend fun saveAiProvider(provider: Int) {
        context.dataStore.edit { it[AI_PROVIDER_KEY] = provider }
    }

    suspend fun saveAiLanguage(language: String) {
        context.dataStore.edit { it[AI_LANGUAGE_KEY] = language }
    }

    suspend fun saveAiTask(task: Int) {
        context.dataStore.edit { it[AI_TASK_KEY] = task }
    }

    suspend fun saveAiFormat(format: Int) {
        context.dataStore.edit { it[AI_FORMAT_KEY] = format }
    }

    suspend fun setRecordTourSeen() {
        context.dataStore.edit { it[HAS_SEEN_RECORD_TOUR] = true }
    }

    suspend fun setHistoryTourSeen() {
        context.dataStore.edit { it[HAS_SEEN_HISTORY_TOUR] = true }
    }

    suspend fun setResultTourSeen() {
        context.dataStore.edit { it[HAS_SEEN_RESULT_TOUR] = true }
    }
}
