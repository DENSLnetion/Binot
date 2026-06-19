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
            // Simpan dulu dengan title fallback, langsung navigasi
            val fallbackTitle = "Catatan " + System.currentTimeMillis().toString().takeLast(4)
            val note = NoteEntity(
                title = fallbackTitle,
                rawText = text,
                summary = null,
                isPinned = false,
                audioPath = null
            )
            val id = repository.insert(note).toInt()
            onSaved(id)

            // Generate AI title di background, update setelah dapat hasil
            if (apiKey.isNotBlank()) {
                launch(Dispatchers.IO) {
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
                        e.printStackTrace() // Gagal generate title = fallback title tetap dipakai
                    }
                }
            }
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

