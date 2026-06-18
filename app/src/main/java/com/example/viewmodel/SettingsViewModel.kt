package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.NoteEntity
import com.example.data.NoteRepository
import com.example.data.SettingsRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val noteRepository: NoteRepository // Tambah repository buat narik data backup
) : ViewModel() {

    val userName: StateFlow<String> = settingsRepository.userNameFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val apiKey: StateFlow<String> = settingsRepository.geminiApiKeyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )
    
    val themeMode: StateFlow<Int> = settingsRepository.themeModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // Moshi buat JSON
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val type = Types.newParameterizedType(List::class.java, NoteEntity::class.java)
    private val adapter = moshi.adapter<List<NoteEntity>>(type)

    fun saveUserName(name: String) {
        viewModelScope.launch { settingsRepository.saveUserName(name) }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch { settingsRepository.saveGeminiApiKey(key) }
    }

    fun saveThemeMode(mode: Int) {
        viewModelScope.launch { settingsRepository.saveThemeMode(mode) }
    }

    // Fungsi Super: Backup Catatan ke File JSON
    fun exportBackup(context: Context, uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val notes = noteRepository.getAllNotesSync()
                val jsonStr = adapter.toJson(notes)
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonStr.toByteArray())
                }
                onResult("Backup successful!")
            } catch (e: Exception) {
                onResult("Backup failed: ${e.message}")
            }
        }
    }

    // Fungsi Super: Restore Catatan dari File JSON
    fun importBackup(context: Context, uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val stringBuilder = StringBuilder()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            stringBuilder.append(line)
                            line = reader.readLine()
                        }
                    }
                }
                
                val jsonStr = stringBuilder.toString()
                val notes = adapter.fromJson(jsonStr)
                
                if (notes != null) {
                    noteRepository.insertNotes(notes) // Ga hapus data lama, cuma nimpa ID yang sama atau nambah baru
                    onResult("Restore successful!")
                } else {
                    onResult("Invalid backup file.")
                }
            } catch (e: Exception) {
                onResult("Restore failed: ${e.message}")
            }
        }
    }

    companion object {
        fun provideFactory(
            settingsRepository: SettingsRepository,
            noteRepository: NoteRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(settingsRepository, noteRepository) as T
                }
            }
    }
}


