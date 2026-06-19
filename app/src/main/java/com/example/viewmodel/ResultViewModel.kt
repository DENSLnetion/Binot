package com.example.viewmodel

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.File

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

    // LOGIKA AUDIO PLAYER
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

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
        val updatedNote = currentNote.copy(title = newTitle, timestamp = System.currentTimeMillis())
        _note.value = updatedNote
        viewModelScope.launch {
            noteRepository.update(updatedNote)
        }
    }

    // Fungsi Mutakhir Pemutar Audio Asli
    fun toggleAudio() {
        val path = _note.value?.audioPath ?: return
        val file = File(path)
        if (!file.exists()) {
            _error.value = "Original audio file not found or corrupted."
            return
        }

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _playbackProgress.value = 0f
                    progressJob?.cancel()
                }
            }
        }

        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            _isPlaying.value = false
            progressJob?.cancel()
        } else {
            mediaPlayer?.start()
            _isPlaying.value = true
            startProgressTracker()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (_isPlaying.value) {
                mediaPlayer?.let {
                    if (it.duration > 0) {
                        _playbackProgress.value = it.currentPosition.toFloat() / it.duration.toFloat()
                    }
                }
                delay(100)
            }
        }
    }

    fun seekAudio(progress: Float) {
        mediaPlayer?.let {
            val seekTo = (it.duration * progress).toInt()
            it.seekTo(seekTo)
            _playbackProgress.value = progress
        }
    }

    // Fungsi Export File MP3/MP4 ke direktori luar
    fun exportAudio(context: Context, uri: Uri, onResult: (String) -> Unit) {
        val path = _note.value?.audioPath
        if (path == null || !File(path).exists()) {
            onResult("Audio file not found!")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceFile = File(path)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                launch(Dispatchers.Main) { onResult("Audio exported successfully!") }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onResult("Failed to export audio: ${e.message}") }
            }
        }
    }

    fun processText(language: String, mode: String) {
        val currentNote = _note.value ?: return
        if (apiKey.isBlank()) {
            _error.value = "API Key not found. Please set it in Settings."
            return
        }

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val prompt = if (mode == "tidy") {
                    """
                        You are a professional proofreader and editor. Your task is to clean up and perfectly format the following raw voice transcript into $language WITHOUT summarizing or omitting any details.
                        
                        CRITICAL MATHEMATICAL RULES:
                        1. If the transcript contains mathematical concepts, equations, or symbols spelled out in words (e.g., "tambah", "kurang", "sigma", "kuadrat", "akar", "integral", "setengah", "per", "sama dengan", "tak hingga"), you MUST forcefully convert them into strict Mathematical Unicode Symbols (e.g., +, -, ∑, ², √, ∫, ½, /, =, ∞). 
                        2. ABSOLUTELY NO BACKTICKS (`). DO NOT use Markdown code blocks or inline code formatting for math formulas. NEVER output the ` character anywhere. Write equations as plain normal text naturally integrated within the sentence.
                        
                        STRICT FORMATTING RULES:
                        1. DO NOT summarize. Preserve every single detail, thought, and information from the raw transcript.
                        2. ONLY fix grammatical errors, remove filler words (e.g., "umm", "uh", repetitions), and structure the text logically with proper punctuation.
                        3. MUST use neat Markdown formatting:
                           - Use '# ' for Main Title (H1).
                           - Use '## ' for Subtitles (H2) if the topic shifts naturally.
                           - Use bullet points ('- ') for lists if the speaker is listing items.
                           - Provide empty lines between paragraphs for readability.
                        4. STRICTLY NO conversational filler, introductions, or AI pleasantries. Output ONLY the perfectly tidied up text.
                        
                        Raw Text:
                        ${currentNote.rawText}
                    """.trimIndent()
                } else {
                    """
                        You are a professional minutes assistant. Your task is to clean up and summarize the following raw voice transcript into $language.
                        
                        CRITICAL MATHEMATICAL RULES:
                        1. If the transcript contains mathematical concepts, equations, or symbols spelled out in words (e.g., "tambah", "kurang", "sigma", "kuadrat", "akar", "integral", "setengah", "per", "sama dengan", "tak hingga"), you MUST forcefully convert them into strict Mathematical Unicode Symbols (e.g., +, -, ∑, ², √, ∫, ½, /, =, ∞). 
                        2. ABSOLUTELY NO BACKTICKS (`). DO NOT use Markdown code blocks or inline code formatting for math formulas. NEVER output the ` character anywhere. Write equations as plain normal text naturally integrated within the sentence.
                        
                        STRICT FORMATTING RULES:
                        1. Ignore filler words (e.g., "umm", "uh") and fix broken sentence structures.
                        2. Create a comprehensive summary without losing key points.
                        3. MUST use neat Markdown formatting:
                           - Use '# ' for Main Title (H1).
                           - Use '## ' for Subtitles (H2).
                           - Use bullet points ('- ') for lists.
                           - Provide empty lines between paragraphs.
                        4. STRICTLY NO conversational filler, introductions, or AI pleasantries. Output ONLY the summarized text.
                        
                        Raw Text:
                        ${currentNote.rawText}
                    """.trimIndent()
                }
                
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt))))
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val processedText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (processedText != null) {
                    val updatedNote = currentNote.copy(summary = processedText, timestamp = System.currentTimeMillis())
                    _note.value = updatedNote
                    noteRepository.update(updatedNote)
                } else {
                    _error.value = "Failed to process text. Empty response from AI."
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

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
        progressJob?.cancel()
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


