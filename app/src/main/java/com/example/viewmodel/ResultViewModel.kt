package com.example.viewmodel

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Candidate
import com.example.data.Content
import com.example.data.FileData
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
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

    // State baru buat pesan loading detail
    private val _loadingMessage = MutableStateFlow("")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _allLabels = MutableStateFlow<List<String>>(emptyList())
    val allLabels: StateFlow<List<String>> = _allLabels.asStateFlow()

    init {
        loadNote()
        loadAllLabels()
    }

    private fun loadNote() {
        viewModelScope.launch {
            val fetchedNote = noteRepository.getNoteById(noteId)
            _note.value = fetchedNote
            
            if (fetchedNote != null && (fetchedNote.rawText.isBlank() || fetchedNote.rawText == "Pending Transcription") && fetchedNote.audioPath != null) {
                transcribeWithFileApi()
            } else if (fetchedNote != null && fetchedNote.rawText == "Pending Transcription" && fetchedNote.audioPath == null) {
                _error.value = "Gagal transkrip: File audio tidak ditemukan di database. Pastikan RecordViewModel menyimpan path audio."
            }
        }
    }

    private fun loadAllLabels() {
        viewModelScope.launch(Dispatchers.IO) {
            val notes = noteRepository.getAllNotesSync()
            val systemNote = notes.find { it.title == "[[BINOT_SYSTEM_LABELS]]" }
            val customLabels = systemNote?.rawText?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
            val noteLabels = notes.filter { it.title != "[[BINOT_SYSTEM_LABELS]]" }
                .flatMap { it.label?.split("|")?.map { l -> l.trim() }?.filter { l -> l.isNotBlank() } ?: emptyList() }
            
            _allLabels.value = (customLabels + noteLabels).distinct().sorted()
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

    fun updateRawText(newRawText: String) {
        val currentNote = _note.value ?: return
        val original = currentNote.originalRawText ?: currentNote.rawText
        val updatedNote = currentNote.copy(
            rawText = newRawText,
            originalRawText = original,
            timestamp = System.currentTimeMillis()
        )
        _note.value = updatedNote
        viewModelScope.launch { 
            noteRepository.update(updatedNote) 
        }
    }

    fun toggleLabel(label: String) {
        val currentNote = _note.value ?: return
        val currentLabels = currentNote.label
            ?.split("|")?.map { it.trim() }?.filter { it.isNotBlank() }?.toMutableList()
            ?: mutableListOf()

        if (currentLabels.contains(label)) {
            currentLabels.remove(label)
        } else {
            currentLabels.add(label)
        }

        val newLabelString = if (currentLabels.isEmpty()) null else currentLabels.joinToString("|")
        val updatedNote = currentNote.copy(label = newLabelString, timestamp = System.currentTimeMillis())
        _note.value = updatedNote

        viewModelScope.launch(Dispatchers.IO) {
            noteRepository.update(updatedNote)

            if (label.isNotBlank()) {
                val notes = noteRepository.getAllNotesSync()
                val sysNote = notes.find { it.title == "[[BINOT_SYSTEM_LABELS]]" }
                if (sysNote != null) {
                    val labels = sysNote.rawText.split("|").filter { it.isNotBlank() }.toMutableSet()
                    labels.add(label)
                    noteRepository.update(sysNote.copy(rawText = labels.joinToString("|")))
                } else {
                    noteRepository.insert(NoteEntity(title = "[[BINOT_SYSTEM_LABELS]]", rawText = label, summary = null))
                }
            }
            loadAllLabels()
        }
    }

    fun restoreRawText() {
        val currentNote = _note.value ?: return
        if (currentNote.summary == null) return
        
        val updatedNote = currentNote.copy(summary = null, timestamp = System.currentTimeMillis())
        _note.value = updatedNote
        
        viewModelScope.launch { 
            noteRepository.update(updatedNote) 
        }
    }

    fun restoreOriginalRawText(onUndoAvailable: (NoteEntity) -> Unit) {
        val currentNote = _note.value ?: return
        if (currentNote.originalRawText == null) return

        val previousNote = currentNote.copy()
        val updatedNote = currentNote.copy(
            rawText = currentNote.originalRawText,
            timestamp = System.currentTimeMillis()
        )
        _note.value = updatedNote
        viewModelScope.launch { noteRepository.update(updatedNote) }
        
        onUndoAvailable(previousNote)
    }

    fun undoRestoreRawText(previousNote: NoteEntity) {
        _note.value = previousNote
        viewModelScope.launch { noteRepository.update(previousNote) }
    }

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

    // Logic File API Utama
    private fun transcribeWithFileApi() {
        val currentNote = _note.value ?: return
        val audioPath = currentNote.audioPath ?: return
        
        if (apiKey.isBlank()) { 
            _error.value = "AI processing requires an API Key. Please set your Gemini API Key in the Settings."
            return 
        }
        
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch(Dispatchers.IO) {
            var remoteFileName: String? = null
            try {
                val file = File(audioPath)
                if (!file.exists()) throw Exception("Audio file missing from device storage.")

                // TAHAP 1: Upload File ke Server Google
                launch(Dispatchers.Main) { _loadingMessage.value = "Uploading audio to secure server..." }
                
                val mimeType = "audio/mp4" // Sesuai dengan MediaRecorder.OutputFormat.MPEG_4
                val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
                
                val uploadResponse = RetrofitClient.service.uploadFile(
                    apiKey = apiKey,
                    contentLength = file.length(),
                    contentType = mimeType,
                    mimeType = mimeType,
                    fileBytes = requestBody
                )

                if (uploadResponse.file == null) {
                    throw Exception("Failed to upload file to Gemini server.")
                }

                val uploadedFileUri = uploadResponse.file.uri
                remoteFileName = uploadResponse.file.name

                // TAHAP 2: Polling Status (Tunggu sampai ACTIVE)
                launch(Dispatchers.Main) { _loadingMessage.value = "Audio uploaded. Gemini is processing..." }
                var fileState = uploadResponse.file.state
                var attempts = 0
                val maxAttempts = 60 // Max nunggu 60 * 3 = 180 detik (3 menit)

                while (fileState == "PROCESSING" && attempts < maxAttempts) {
                    delay(3000) // Polling tiap 3 detik
                    val statusResponse = RetrofitClient.service.getFile(remoteFileName, apiKey)
                    fileState = statusResponse.state
                    attempts++
                }

                if (fileState != "ACTIVE") {
                    throw Exception("File processing timeout or failed at Google server.")
                }

                // TAHAP 3: Request Transkripsi Pakai URI
                launch(Dispatchers.Main) { _loadingMessage.value = "Transcribing audio..." }
                val promptText = """
                    You are a highly accurate audio transcription AI. Your ONLY task is to transcribe the audio exactly word-for-word.
                    
                    CRITICAL RULES:
                    1. DO NOT hallucinate, guess, or make up words. If the audio is silent or contains no speech, output exactly "[No speech detected]".
                    2. DO NOT summarize. Output the exact raw transcript.
                    3. DO NOT add conversational filler, AI pleasantries, or introductions like "Here is the transcript".
                    4. Automatically detect the spoken language and transcribe in that exact language.
                    5. If the transcript contains mathematical concepts, equations, or symbols spelled out in words (e.g., "tambah", "kurang", "sigma", "kuadrat", "akar"), you MUST forcefully convert them into strict Mathematical Unicode Symbols (e.g., +, -, ∑, ², √).
                    6. ABSOLUTELY NO BACKTICKS (`). DO NOT use Markdown code blocks or inline code formatting. Write equations as plain normal text naturally integrated within the sentence.
                """.trimIndent()
                
                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(
                            Part(text = promptText), 
                            Part(fileData = FileData(mimeType = mimeType, fileUri = uploadedFileUri))
                        ))
                    )
                )
                
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val transcript = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()

                launch(Dispatchers.Main) {
                    if (transcript != null && !transcript.contains("[No speech detected]")) {
                        val updatedNote = currentNote.copy(rawText = transcript, timestamp = System.currentTimeMillis())
                        _note.value = updatedNote
                        noteRepository.update(updatedNote)
                        
                        // Generate Judul AI
                        launch(Dispatchers.IO) {
                            try {
                                val titlePrompt = """
                                    Buat judul singkat 3-5 kata dalam bahasa yang sama dengan teks berikut.
                                    RULES:
                                    - Hanya output judulnya saja, tanpa tanda kutip, tanpa penjelasan apapun.
                                    - Maksimal 5 kata, padat dan informatif.
                                    Teks: ${transcript.take(500)}
                                """.trimIndent()
                                val titleRequest = GenerateContentRequest(contents = listOf(Content(parts = listOf(Part(text = titlePrompt)))))
                                val titleResponse = RetrofitClient.service.generateContent(apiKey, titleRequest)
                                val aiTitle = titleResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                                
                                if (!aiTitle.isNullOrBlank()) {
                                    val finalNote = updatedNote.copy(title = aiTitle)
                                    _note.value = finalNote
                                    noteRepository.update(finalNote)
                                }
                            } catch (e: Exception) {
                                // Gagal generate title biarkan saja
                            }
                        }
                    } else if (transcript?.contains("[No speech detected]") == true) {
                        _error.value = "No clear speech detected in the audio recording."
                    } else { 
                        _error.value = "AI failed to process the transcript. The server response was empty." 
                    }
                    _isLoading.value = false
                }
            } catch (e: HttpException) { 
                launch(Dispatchers.Main) { 
                    _error.value = when (e.code()) {
                        400 -> "Bad Request (400). The audio format or data is unrecognized."
                        401 -> "Invalid API Key (401). Please check your Gemini API Key in the Settings."
                        403 -> "Access Denied (403). Your API Key does not have permission for this model."
                        429 -> "API Rate Limit Exceeded (429). Please wait a moment or use a different API Key."
                        500 -> "Gemini Server Error (500). Please try again later."
                        else -> "HTTP Error: ${e.code()} - Please check your connection or API Key."
                    }
                    _isLoading.value = false 
                }
            } catch (e: Exception) { 
                launch(Dispatchers.Main) { 
                    _error.value = "Processing failed: ${e.message}"
                    _isLoading.value = false 
                } 
            } finally {
                // TAHAP 4: CLEANUP. Hapus file dari server Google walau sukses atau gagal.
                if (remoteFileName != null) {
                    try {
                        RetrofitClient.service.deleteFile(remoteFileName, apiKey)
                    } catch (e: Exception) {
                        // Kalau gagal hapus yaudah biarin aja nunggu auto-delete 48 jam.
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun processText(language: String, mode: String) {
        val currentNote = _note.value ?: return
        
        if (apiKey.isBlank()) { 
            _error.value = "AI processing requires an API Key. Please set your Gemini API Key in the Settings."
            return 
        }
        
        _isLoading.value = true
        _error.value = null
        _loadingMessage.value = "AI is processing your text..."

        viewModelScope.launch(Dispatchers.IO) {
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
                
                launch(Dispatchers.Main) {
                    if (processedText != null) {
                        val updatedNote = currentNote.copy(summary = processedText, timestamp = System.currentTimeMillis())
                        _note.value = updatedNote
                        noteRepository.update(updatedNote)
                    } else { 
                        _error.value = "AI failed to process the text. The server response was empty." 
                    }
                    _isLoading.value = false
                }
            } catch (e: HttpException) { 
                launch(Dispatchers.Main) {
                    _error.value = when (e.code()) {
                        400 -> "Bad Request (400). The text format is unrecognized."
                        401 -> "Invalid API Key (401). Please check your Gemini API Key in the Settings."
                        403 -> "Access Denied (403). Your API Key does not have permission for this model."
                        429 -> "API Rate Limit Exceeded (429). Please wait a moment or use a different API Key."
                        500 -> "Gemini Server Error (500). Please try again later."
                        else -> "HTTP Error: ${e.code()} - Please check your connection or API Key."
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) { 
                launch(Dispatchers.Main) {
                    _error.value = "Processing failed: ${e.message}"
                    _isLoading.value = false
                }
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
        fun provideFactory(noteId: Int, repository: NoteRepository, apiKey: String): ViewModelProvider.Factory = 
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") 
                override fun <T : ViewModel> create(modelClass: Class<T>): T { 
                    return ResultViewModel(noteId, repository, apiKey) as T 
                }
            }
    }
}
