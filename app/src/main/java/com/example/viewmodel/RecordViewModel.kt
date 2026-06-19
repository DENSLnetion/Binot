package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.NoteEntity
import com.example.data.NoteRepository
import com.example.utils.AudioRecorderManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecordViewModel(
    private val audioRecorderManager: AudioRecorderManager,
    private val repository: NoteRepository
) : ViewModel() {

    val isRecording: StateFlow<Boolean> = audioRecorderManager.isRecording
    val amplitude: StateFlow<Float> = audioRecorderManager.amplitude
    val recognizedText: StateFlow<String> = audioRecorderManager.recognizedText

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()
    private var timerJob: Job? = null

    fun toggleRecording(isEmulator: Boolean) {
        if (isRecording.value) {
            // Abaikan file path karena kita ga nyimpen MP4
            audioRecorderManager.stopRecording()
            stopTimer()
        } else {
            audioRecorderManager.startRecording(isEmulator)
            startTimer()
        }
    }

    private fun startTimer() {
        _recordingSeconds.value = 0
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _recordingSeconds.value += 1
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun saveNote(onSaved: (Int) -> Unit) {
        val text = recognizedText.value.trim()
        if (text.isEmpty()) return
        
        viewModelScope.launch {
            val title = "Catatan " + System.currentTimeMillis().toString().takeLast(4)
            // audioPath dipaksa null karena hardware kaga ngizinin
            val note = NoteEntity(
                title = title, 
                rawText = text, 
                summary = null, 
                isPinned = false,
                audioPath = null 
            )
            val id = repository.insert(note).toInt()
            onSaved(id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorderManager.stopRecording()
        stopTimer()
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

