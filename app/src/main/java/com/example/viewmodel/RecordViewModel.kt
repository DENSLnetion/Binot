package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.NoteEntity
import com.example.data.NoteRepository
import com.example.utils.AudioRecorderManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RecordViewModel(
    private val audioRecorderManager: AudioRecorderManager,
    private val repository: NoteRepository
) : ViewModel() {

    val isRecording: StateFlow<Boolean> = audioRecorderManager.isRecording
    val amplitude: StateFlow<Float> = audioRecorderManager.amplitude
    val recognizedText: StateFlow<String> = audioRecorderManager.recognizedText

    fun toggleRecording(isEmulator: Boolean) {
        if (isRecording.value) {
            audioRecorderManager.stopRecording()
        } else {
            audioRecorderManager.startRecording(isEmulator)
        }
    }

    fun saveNote(onSaved: (Int) -> Unit) {
        val text = recognizedText.value.trim()
        if (text.isEmpty()) return
        
        viewModelScope.launch {
            val title = "Catatan " + System.currentTimeMillis().toString().takeLast(4)
            val note = NoteEntity(title = title, rawText = text, summary = null)
            val id = repository.insert(note).toInt()
            onSaved(id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorderManager.stopRecording()
    }

    companion object {
        fun provideFactory(
            audioRecorderManager: AudioRecorderManager,
            repository: NoteRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RecordViewModel(audioRecorderManager, repository) as T
                }
            }
    }
}
