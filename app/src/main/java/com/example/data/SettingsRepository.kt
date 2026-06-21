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
        val THEME_MODE_KEY = intPreferencesKey("theme_mode")
        val RECORD_MODE_KEY = intPreferencesKey("record_mode") // Kunci baru untuk Record Mode
    }

    val userNameFlow: Flow<String> = context.dataStore.data.map { it[USER_NAME_KEY] ?: "" }
    val geminiApiKeyFlow: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val themeModeFlow: Flow<Int> = context.dataStore.data.map { it[THEME_MODE_KEY] ?: 0 }
    val recordModeFlow: Flow<Int> = context.dataStore.data.map { it[RECORD_MODE_KEY] ?: 0 } // 0 = Google, 1 = Gemini

    suspend fun saveUserName(name: String) {
        context.dataStore.edit { it[USER_NAME_KEY] = name }
    }

    suspend fun saveGeminiApiKey(key: String) {
        context.dataStore.edit { it[API_KEY] = key }
    }

    suspend fun saveThemeMode(mode: Int) {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode }
    }

    suspend fun saveRecordMode(mode: Int) {
        context.dataStore.edit { it[RECORD_MODE_KEY] = mode }
    }
}
