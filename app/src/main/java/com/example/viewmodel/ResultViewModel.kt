package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Candidate
import com.example.data.Content
import com.example.data.GenerateContentRequest
import com.example.data.NoteEntity
import com.example.data.NoteRepository
import com.example.data.Part
import com.example.data.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResultViewModel(
    private val noteId: Int,
    private val noteRepository: NoteRepository,
    private val apiKey: String
) : ViewModel() {

    private val _note = MutableStateFlow<NoteEntity?>(null)
    val note: StateFlow<NoteEntity?> = _note.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadNote()
    }

    private fun loadNote() {
        viewModelScope.launch {
            _note.value = noteRepository.getNoteById(noteId)
        }
    }

    fun updateTitle(newTitle: String) {
        val currentNote = _note.value ?: return
        val updatedNote = currentNote.copy(title = newTitle)
        _note.value = updatedNote
        viewModelScope.launch {
            noteRepository.update(updatedNote)
        }
    }

    fun summarizeText(language: String) {
        val currentNote = _note.value ?: return
        if (apiKey.isBlank()) {
            _error.value = "Kunci API Gemini tidak ditemukan. Harap atur di Pengaturan."
            return
        }

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val prompt = "Rangkum teks berikut secara ekstensif dan detail dalam bahasa $language. Wajib gunakan format Markdown yang RAPI: gunakan '# ' untuk Judul Utama (H1), '## ' untuk Sub Judul (H2), '**teks**' untuk cetak tebal, '*teks*' untuk miring, dan list wajib menggunakan '- ' atau angka '1. '. Jangan gunakan blok kode.\n\nTeks mentah:\n${currentNote.rawText}"
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt))))
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val summaryText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (summaryText != null) {
                    val updatedNote = currentNote.copy(summary = summaryText)
                    _note.value = updatedNote
                    noteRepository.update(updatedNote)
                } else {
                    _error.value = "Gagal mendapatkan rangkuman. Respons kosong."
                }
            } catch (e: Exception) {
                _error.value = "Terjadi kesalahan: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    companion object {
        fun provideFactory(
            noteId: Int,
            repository: NoteRepository,
            apiKey: String
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ResultViewModel(noteId, repository, apiKey) as T
                }
            }
    }
}
