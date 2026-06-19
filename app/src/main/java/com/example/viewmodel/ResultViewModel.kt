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
import retrofit2.HttpException

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
            // PERBAIKAN: Pas cuma dibuka, TIMESTAMP GA BERUBAH. Jadi posisinya tetep!
            _note.value = noteRepository.getNoteById(noteId)
        }
    }

    fun updateTitle(newTitle: String) {
        val currentNote = _note.value ?: return
        // PERBAIKAN: Baru pas di-edit judulnya, timestamp diupdate biar naik ke atas
        val updatedNote = currentNote.copy(title = newTitle, timestamp = System.currentTimeMillis())
        _note.value = updatedNote
        viewModelScope.launch {
            noteRepository.update(updatedNote)
        }
    }

    fun summarizeText(language: String) {
        val currentNote = _note.value ?: return
        if (apiKey.isBlank()) {
            _error.value = "API Key not found. Please set it in Settings."
            return
        }

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                // PERBAIKAN PENTING: Larang keras penggunaan Backtick (`)
                val prompt = """
                    You are a professional minutes assistant. Your task is to clean up and summarize the following raw voice transcript into $language.
                    
                    CRITICAL MATHEMATICAL RULES:
                    1. If the transcript contains mathematical concepts, equations, or symbols spelled out in words (e.g., "tambah", "kurang", "sigma", "kuadrat", "akar", "integral", "setengah", "per", "sama dengan", "tak hingga"), you MUST forcefully convert them into strict Mathematical Unicode Symbols (e.g., +, -, ∑, ², √, ∫, ½, /, =, ∞). 
                    2. DO NOT use Markdown backticks (`), quotes, or code blocks for math formulas. Write them as plain normal text within the sentence.
                    
                    STRICT FORMATTING RULES:
                    1. Ignore filler words (e.g., "umm", "uh") and fix broken sentence structures.
                    2. Create a comprehensive summary without losing key points.
                    3. MUST use neat Markdown formatting:
                       - Use '# ' for Main Title (H1).
                       - Use '## ' for Subtitles (H2).
                       - Use bullet points ('- ') for lists.
                       - Provide empty lines between paragraphs.
                    
                    Raw Text:
                    ${currentNote.rawText}
                """.trimIndent()
                
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt))))
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val summaryText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (summaryText != null) {
                    // PERBAIKAN: Kalau sukses diringkas, baru timestamp diupdate biar naik ke atas
                    val updatedNote = currentNote.copy(summary = summaryText, timestamp = System.currentTimeMillis())
                    _note.value = updatedNote
                    noteRepository.update(updatedNote)
                } else {
                    _error.value = "Failed to generate summary. Empty response."
                }
            } catch (e: HttpException) {
                if (e.code() == 429) {
                    _error.value = "API Key limit exhausted. Please wait or use a different key."
                } else {
                    _error.value = "HTTP Error: ${e.code()} - ${e.message()}"
                }
            } catch (e: Exception) {
                _error.value = "An error occurred: ${e.message}"
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

