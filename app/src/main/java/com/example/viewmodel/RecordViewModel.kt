package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Content
import com.example.data.GenerateContentRequest
import com.example.data.NoteEntity
import com.example.data.NoteRepository
import com.example.data.Part
import com.example.data.RetrofitClient
import com.example.utils.AudioRecorderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordViewModel(
    private val audioRecorderManager: AudioRecorderManager,
    private val repository: NoteRepository,
    private val apiKey: String
) : ViewModel() {

    val isRecording: StateFlow<Boolean> = audioRecorderManager.isRecording
    val amplitude: StateFlow<Float> = audioRecorderManager.amplitude
    val recognizedText: StateFlow<String> = audioRecorderManager.recognizedText

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()
    private var timerJob: Job? = null

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private var pendingAudioPath: String? = null

    fun toggleRecording(isEmulator: Boolean, recordMode: Int) {
        if (isRecording.value) {
            pendingAudioPath = audioRecorderManager.stopRecording()
            stopTimer()
            _isPaused.value = false
        } else {
            _isPaused.value = false
            pendingAudioPath = null
            audioRecorderManager.startRecording(isEmulator, recordMode)
            startTimer()
        }
    }

    fun stopRecordingInstant() {
        pendingAudioPath = audioRecorderManager.stopRecording()
        stopTimer()
        _isPaused.value = false
        _recordingSeconds.value = 0
    }

    fun pauseRecording() {
        if (!isRecording.value || _isPaused.value) return
        _isPaused.value = true
        stopTimer()
        audioRecorderManager.pauseRecording()
    }

    fun resumeRecording() {
        if (!isRecording.value || !_isPaused.value) return
        _isPaused.value = false
        resumeTimer()
        audioRecorderManager.resumeRecording()
    }

    fun stopFromPaused(isEmulator: Boolean) {
        if (!_isPaused.value) return
        pendingAudioPath = audioRecorderManager.stopRecording()
        _isPaused.value = false
        _recordingSeconds.value = 0
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

    private fun resumeTimer() {
        timerJob?.cancel()
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

    suspend fun saveNote(recordMode: Int): Boolean {
        delay(300)

        val text = if (recordMode == 1) "Pending Transcription" else recognizedText.value.trim()
        val path = pendingAudioPath 
        
        // Mencegah save kalau mode Google tapi teksnya kosong
        if (recordMode == 0 && text.isEmpty()) {
            return false
        }

        val note = NoteEntity(
            title = "",
            rawText = text,
            summary = null,
            isPinned = false,
            audioPath = path
        )
        
        val id = withContext(Dispatchers.IO) { repository.insert(note).toInt() }

        if (apiKey.isNotBlank() && recordMode == 0) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val prompt = """
                        Buat judul singkat 3-5 kata dalam bahasa yang sama dengan teks berikut.
                        RULES:
                        - Hanya output judulnya saja, tanpa tanda kutip, tanpa penjelasan apapun.
                        - Maksimal 5 kata, padat dan informatif.
                        - Gunakan bahasa yang sama dengan teks input.
                        Teks: ${text.take(500)}
                    """.trimIndent()
                    val request = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = prompt))))
                    )
                    val response = RetrofitClient.service.generateContent(apiKey, request)
                    val aiTitle = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                    if (!aiTitle.isNullOrBlank()) {
                        val savedNote = repository.getNoteById(id)
                        if (savedNote != null) {
                            repository.update(savedNote.copy(title = aiTitle))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        pendingAudioPath = null
        return true
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorderManager.stopRecording()
        stopTimer()
        _isPaused.value = false
    }

    companion object {
        fun provideFactory(
            audioRecorderManager: AudioRecorderManager,
            repository: NoteRepository,
            apiKey: String
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RecordViewModel(audioRecorderManager, repository, apiKey) as T
                }
            }
    }
}
