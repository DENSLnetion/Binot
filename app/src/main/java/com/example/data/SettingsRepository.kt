package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val THEME_MODE = intPreferencesKey("theme_mode") // 0 = System, 1 = Light, 2 = Dark
        
        // Tambahan Key untuk nama User (Sinkron dengan Sektor 2)
        val USER_NAME = stringPreferencesKey("user_name") 
    }

    val geminiApiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GEMINI_API_KEY] ?: ""
    }

    val themeModeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: 0
    }

    // Flow untuk ngebaca nama user
    val userNameFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_NAME] ?: ""
    }

    suspend fun saveGeminiApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_API_KEY] = key
        }
    }

    suspend fun saveThemeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    // Fungsi untuk nge-save nama user
    suspend fun saveUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_NAME] = name
        }
    }
}
