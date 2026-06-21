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
import com.example.data.GroqChatRequest
import com.example.data.GroqMessage
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
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class ResultViewModel(
    private val noteId: Int,
    private val noteRepository: NoteRepository,
    private val aiProvider: Int, // 0 = Gemini, 1 = Groq
    private val geminiApiKey: String,
    private val groqApiKey: String
) : ViewModel() {

    private val _note = MutableStateFlow<NoteEntity?>(null)
    val note: StateFlow<NoteEntity?> = _note.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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

    private val _explainResult = MutableStateFlow<String?>(null)
    val explainResult: StateFlow<String?> = _explainResult.asStateFlow()

    private val _isExplaining = MutableStateFlow(false)
    val isExplaining: StateFlow<Boolean> = _isExplaining.asStateFlow()

    init {
        loadNote()
        loadAllLabels()
    }

    private fun loadNote() {
        viewModelScope.launch {
            val fetchedNote = noteRepository.getNoteById(noteId)
            _note.value = fetchedNote
            
            if (fetchedNote != null && (fetchedNote.rawText.isBlank() || fetchedNote.rawText == "Pending Transcription") && fetchedNote.audioPath != null) {
                transcribeAudio()
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
        viewModelScope.launch { noteRepository.update(updatedNote) }
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
        viewModelScope.launch { noteRepository.update(updatedNote) }
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
        viewModelScope.launch { noteRepository.update(updatedNote) }
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
                    sourceFile.inputStream().use { input -> input.copyTo(output) }
                }
                launch(Dispatchers.Main) { onResult("Audio exported successfully!") }
            } catch (e: Exception) { 
                launch(Dispatchers.Main) { onResult("Failed to export audio: ${e.message}") } 
            }
        }
    }

    fun explainText(selectedText: String, deviceLanguage: String) {
        if (aiProvider == 0 && geminiApiKey.isBlank()) {
            _explainResult.value = "Gemini API Key is missing. Please set it in Settings."
            return
        }
        if (aiProvider == 1 && groqApiKey.isBlank()) {
            _explainResult.value = "Groq API Key is missing. Please set it in Settings."
            return
        }
        
        _isExplaining.value = true
        _explainResult.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val systemPrompt = """
                    You are an expert encyclopedia. Explain the given term/sentence purely, briefly, and with high relevance. 
                    STRICT RULES YOU MUST OBEY:
                    1. Output language MUST follow: $deviceLanguage.
                    2. NO conversational filler, pleasantries, or introductions.
                    3. Format nicely using Markdown if needed, but ABSOLUTELY NO BACKTICKS (`).
                """.trimIndent()
                
                val userPrompt = "Term to explain: \"$selectedText\""
                
                val resultText = if (aiProvider == 1) { // Groq
                    val request = GroqChatRequest(
                        model = "llama-3.3-70b-versatile",
                        messages = listOf(
                            GroqMessage(role = "system", content = systemPrompt),
                            GroqMessage(role = "user", content = userPrompt)
                        )
                    )
                    val response = RetrofitClient.groqService.generateContent("Bearer $groqApiKey", request)
                    response.choices?.firstOrNull()?.message?.content
                } else { // Gemini
                    val request = GenerateContentRequest(
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                        contents = listOf(Content(parts = listOf(Part(text = userPrompt))))
                    )
                    val response = RetrofitClient.service.generateContent(geminiApiKey, request)
                    response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                }
                
                launch(Dispatchers.Main) {
                    _explainResult.value = resultText?.trim() ?: "Failed to generate explanation. Empty response."
                    _isExplaining.value = false
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    _explainResult.value = handleExceptionError(e)
                    _isExplaining.value = false
                }
            }
        }
    }

    fun clearExplainResult() {
        _explainResult.value = null
    }

    fun saveHighlightNote(highlightText: String, noteText: String, lineIndex: Int = -1, startIndex: Int = -1, endIndex: Int = -1) {
        val currentNote = _note.value ?: return
        val currentJson = currentNote.highlightsInfo ?: "[]"

        try {
            val jsonArray = JSONArray(currentJson)
            var found = false
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val sameSpot = startIndex >= 0 && obj.optInt("start", -1) == startIndex && obj.optInt("line", -1) == lineIndex
                val sameLegacyText = startIndex < 0 && obj.getString("text") == highlightText && obj.optInt("start", -1) < 0
                if (sameSpot || sameLegacyText) {
                    obj.put("note", noteText)
                    obj.put("text", highlightText)
                    if (startIndex >= 0) {
                        obj.put("line", lineIndex)
                        obj.put("start", startIndex)
                        obj.put("end", endIndex)
                    }
                    found = true
                    break
                }
            }
            if (!found) {
                val newObj = JSONObject().apply {
                    put("text", highlightText)
                    put("note", noteText)
                    if (startIndex >= 0) {
                        put("line", lineIndex)
                        put("start", startIndex)
                        put("end", endIndex)
                    }
                }
                jsonArray.put(newObj)
            }
            val updatedNote = currentNote.copy(highlightsInfo = jsonArray.toString(), timestamp = System.currentTimeMillis())
            _note.value = updatedNote
            viewModelScope.launch { noteRepository.update(updatedNote) }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun removeHighlight(highlightText: String, lineIndex: Int = -1, startIndex: Int = -1) {
        val currentNote = _note.value ?: return
        val currentJson = currentNote.highlightsInfo ?: return
        try {
            val jsonArray = JSONArray(currentJson)
            val newArray = JSONArray()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val sameSpot = startIndex >= 0 && obj.optInt("start", -1) == startIndex && obj.optInt("line", -1) == lineIndex
                val sameLegacyText = startIndex < 0 && obj.getString("text") == highlightText && obj.optInt("start", -1) < 0
                if (!(sameSpot || sameLegacyText)) newArray.put(obj)
            }
            val updatedString = if (newArray.length() == 0) null else newArray.toString()
            val updatedNote = currentNote.copy(highlightsInfo = updatedString, timestamp = System.currentTimeMillis())
            _note.value = updatedNote
            viewModelScope.launch { noteRepository.update(updatedNote) }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun transcribeAudio() {
        val currentNote = _note.value ?: return
        val audioPath = currentNote.audioPath ?: return
        
        if (aiProvider == 0 && geminiApiKey.isBlank()) { 
            _error.value = "Gemini Processing requires an API Key. Please set it in Settings."
            return 
        }
        if (aiProvider == 1 && groqApiKey.isBlank()) {
            _error.value = "Groq Processing requires an API Key. Please set it in Settings."
            return 
        }
        
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch(Dispatchers.IO) {
            var remoteFileName: String? = null
            try {
                val file = File(audioPath)
                if (!file.exists()) throw Exception("Audio file missing from device storage.")

                var transcript: String? = null

                if (aiProvider == 1) { // GROQ PROCESSING
                    // Cek limit ukuran file untuk Groq (25 MB)
                    if (file.length() > 25 * 1024 * 1024) {
                        launch(Dispatchers.Main) {
                            _error.value = "File audio terlalu besar untuk Groq (Maks 25MB). Silakan ganti penyedia AI ke Gemini di Pengaturan untuk memproses file berdurasi panjang."
                            _isLoading.value = false
                        }
                        return@launch
                    }

                    launch(Dispatchers.Main) { _loadingMessage.value = "Transcribing blazingly fast with Groq..." }
                    
                    val requestFile = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                    val model = "whisper-large-v3-turbo".toRequestBody("text/plain".toMediaTypeOrNull())
                    val format = "json".toRequestBody("text/plain".toMediaTypeOrNull())
                    
                    val response = RetrofitClient.groqService.transcribeAudio("Bearer $groqApiKey", body, model, format)
                    transcript = response.text?.trim()

                } else { // GEMINI PROCESSING
                    launch(Dispatchers.Main) { _loadingMessage.value = "Uploading audio to Google secure server..." }
                    val mimeType = "audio/mp4"
                    val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
                    val uploadResponse = RetrofitClient.service.uploadFile(
                        apiKey = geminiApiKey, contentLength = file.length(), contentType = mimeType, mimeType = mimeType, fileBytes = requestBody
                    )
                    if (uploadResponse.file == null) throw Exception("Failed to upload file to Gemini server.")
                    
                    val uploadedFileUri = uploadResponse.file.uri
                    remoteFileName = uploadResponse.file.name

                    launch(Dispatchers.Main) { _loadingMessage.value = "Audio uploaded. Gemini is processing..." }
                    val systemPrompt = """
                        You are a highly accurate audio transcription AI. Your ONLY task is to transcribe the audio exactly word-for-word.
                        CRITICAL RULES:
                        1. DO NOT hallucinate. If the audio is silent, output exactly "[No speech detected]".
                        2. DO NOT summarize. Output the exact raw transcript.
                        3. DO NOT add conversational filler, introductions, or pleasantries.
                        4. Automatically detect and transcribe in the spoken language.
                        5. Convert mathematical concepts spelled in words into Mathematical Unicode Symbols.
                        6. ABSOLUTELY NO BACKTICKS (`). Write equations as plain text naturally integrated within the sentence.
                    """.trimIndent()
                    
                    val request = GenerateContentRequest(
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                        contents = listOf(Content(parts = listOf(Part(fileData = FileData(mimeType = mimeType, fileUri = uploadedFileUri)))))
                    )
                    val response = RetrofitClient.service.generateContent(geminiApiKey, request)
                    transcript = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                }

                launch(Dispatchers.Main) {
                    if (transcript != null && !transcript.contains("[No speech detected]")) {
                        val updatedNote = currentNote.copy(rawText = transcript, timestamp = System.currentTimeMillis())
                        _note.value = updatedNote
                        noteRepository.update(updatedNote)
                        
                        launch(Dispatchers.IO) {
                            try { generateTitleFromTranscript(updatedNote, transcript) } catch (e: Exception) {}
                        }
                    } else if (transcript?.contains("[No speech detected]") == true) {
                        _error.value = "No clear speech detected in the audio recording."
                    } else { 
                        _error.value = "AI failed to process the transcript. Server response was empty." 
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) { 
                launch(Dispatchers.Main) { 
                    _error.value = handleExceptionError(e)
                    _isLoading.value = false 
                } 
            } finally {
                if (remoteFileName != null) {
                    try { RetrofitClient.service.deleteFile(remoteFileName, geminiApiKey) } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
    }

    private suspend fun generateTitleFromTranscript(note: NoteEntity, transcript: String) {
        val systemPrompt = """
            Buat judul singkat 3-5 kata dalam bahasa yang sama dengan teks yang diberikan pengguna.
            RULES: Hanya output judulnya saja. Tanpa tanda kutip, tanpa titik di akhir, dan tanpa penjelasan apapun.
        """.trimIndent()
        val userPrompt = "Teks:\n${transcript.take(500)}"

        val aiTitle = if (aiProvider == 1) { // Groq
            val request = GroqChatRequest(
                model = "llama-3.1-8b-instant",
                messages = listOf(
                    GroqMessage(role = "system", content = systemPrompt),
                    GroqMessage(role = "user", content = userPrompt)
                )
            )
            RetrofitClient.groqService.generateContent("Bearer $groqApiKey", request).choices?.firstOrNull()?.message?.content?.trim()
        } else { // Gemini
            val request = GenerateContentRequest(
                systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                contents = listOf(Content(parts = listOf(Part(text = userPrompt))))
            )
            RetrofitClient.service.generateContent(geminiApiKey, request).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        }
        
        if (!aiTitle.isNullOrBlank()) {
            val finalNote = note.copy(title = aiTitle)
            _note.value = finalNote
            noteRepository.update(finalNote)
        }
    }

    fun processText(language: String, mode: String) {
        val currentNote = _note.value ?: return
        if (aiProvider == 0 && geminiApiKey.isBlank()) { 
            _error.value = "Gemini Processing requires an API Key. Please set it in Settings."
            return 
        }
        if (aiProvider == 1 && groqApiKey.isBlank()) {
            _error.value = "Groq Processing requires an API Key. Please set it in Settings."
            return 
        }
        
        _isLoading.value = true
        _error.value = null
        _loadingMessage.value = "AI is processing your text..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val systemPrompt = if (mode == "tidy") {
                    """
                        You are an elite proofreader and document formatter. Clean up the provided raw voice transcript into $language WITHOUT summarizing or omitting details.
                        
                        CRITICAL RULES YOU MUST OBEY:
                        1. MATHEMATICS & SYMBOLS: Convert all spoken math concepts to proper Unicode symbols (e.g., +, -, =, %, ∑, √, ², ½).
                        2. NO CODE BLOCKS: ABSOLUTELY NO BACKTICKS (`). NEVER wrap text or equations in markdown code blocks. Write equations naturally inline as plain text.
                        3. ELEGANT MARKDOWN: Use proper Markdown formatting to make it highly readable. Use '#' for main titles, '##' for headers, '-' for bullet points, and '**' for emphasis.
                        4. PRESERVE EVERYTHING: Do not summarize. Fix grammatical errors and remove stuttering/filler words, but keep all information intact.
                        5. ZERO YAPPING: Output ONLY the final formatted text. Do not add introductory words like "Here is the text" or concluding remarks.
                    """.trimIndent()
                } else {
                    """
                        You are an elite meeting assistant and professional summarizer. Summarize the provided raw voice transcript into $language.
                        
                        CRITICAL RULES YOU MUST OBEY:
                        1. MATHEMATICS & SYMBOLS: Convert all spoken math concepts to proper Unicode symbols (e.g., +, -, =, %, ∑, √, ², ½).
                        2. NO CODE BLOCKS: ABSOLUTELY NO BACKTICKS (`). NEVER wrap text or equations in markdown code blocks. Write equations naturally inline as plain text.
                        3. ELEGANT MARKDOWN: Structure the summary logically using neat Markdown. Use '#' for titles, '##' for section headers, and '-' for bullet lists to organize key points.
                        4. CLARITY: Ignore filler words and fix broken sentence structures. Make the summary comprehensive but concise.
                        5. ZERO YAPPING: Output ONLY the final summarized text. Do not add introductory words or conversational filler.
                    """.trimIndent()
                }
                
                val userContent = currentNote.rawText
                
                val processedText = if (aiProvider == 1) { // Groq
                    val request = GroqChatRequest(
                        model = "llama-3.3-70b-versatile",
                        messages = listOf(
                            GroqMessage(role = "system", content = systemPrompt),
                            GroqMessage(role = "user", content = userContent)
                        )
                    )
                    RetrofitClient.groqService.generateContent("Bearer $groqApiKey", request).choices?.firstOrNull()?.message?.content
                } else { // Gemini
                    val request = GenerateContentRequest(
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                        contents = listOf(Content(parts = listOf(Part(text = userContent))))
                    )
                    RetrofitClient.service.generateContent(geminiApiKey, request).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                }
                
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
            } catch (e: Exception) { 
                launch(Dispatchers.Main) {
                    _error.value = handleExceptionError(e)
                    _isLoading.value = false
                }
            }
        }
    }

    private fun handleExceptionError(e: Exception): String {
        return if (e is HttpException) {
            when (e.code()) {
                400 -> "Bad Request (400). File format or data is unrecognized."
                401 -> "Invalid API Key (401). Please check your API Key in the Settings."
                403 -> "Access Denied (403). Your API Key does not have permission."
                413 -> "Payload Too Large (413). The file is too big for the server."
                429 -> "API Rate Limit Exceeded (429). You are making too many requests. Please wait."
                500 -> "Internal Server Error (500). Provider is having trouble. Please try again later."
                503 -> "Service Unavailable (503). The AI Server is currently overloaded."
                else -> "HTTP Error: ${e.code()} - Please check your connection or API Key."
            }
        } else {
            "Processing failed: ${e.message}"
        }
    }

    override fun onCleared() { 
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
        progressJob?.cancel() 
    }

    companion object {
        fun provideFactory(noteId: Int, repository: NoteRepository, aiProvider: Int, geminiApiKey: String, groqApiKey: String): ViewModelProvider.Factory = 
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") 
                override fun <T : ViewModel> create(modelClass: Class<T>): T { 
                    return ResultViewModel(noteId, repository, aiProvider, geminiApiKey, groqApiKey) as T 
                }
            }
    }
}
