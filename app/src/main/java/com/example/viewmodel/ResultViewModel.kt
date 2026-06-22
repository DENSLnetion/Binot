package com.example.viewmodel

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Content
import com.example.data.FileData
import com.example.data.GenerateContentRequest
import com.example.data.GroqChatRequest
import com.example.data.GroqMessage
import com.example.data.NoteEntity
import com.example.data.NoteRepository
import com.example.data.Part
import com.example.data.RetrofitClient
import com.example.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val settingsRepository: SettingsRepository
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
            
            if (fetchedNote != null) {
                if ((fetchedNote.rawText.isBlank() || fetchedNote.rawText == "Pending Transcription") && fetchedNote.audioPath != null) {
                    transcribeAudio()
                } else if (fetchedNote.rawText == "Pending Transcription" && fetchedNote.audioPath == null) {
                    _error.value = "Failed: Audio file not found. Raw text is pending but no audio path exists."
                } else if (fetchedNote.rawText.isNotBlank() && fetchedNote.rawText != "Pending Transcription") {
                    checkAndTriggerAutoProcess(fetchedNote)
                }
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

    private fun checkAndTriggerAutoProcess(noteToProcess: NoteEntity) {
        viewModelScope.launch {
            val lang = settingsRepository.aiLanguageFlow.first()
            val task = settingsRepository.aiTaskFlow.first()
            val format = settingsRepository.aiFormatFlow.first()
            
            val currentMeta = "<!--BINOT_META:${lang}_${task}_${format}-->"
            
            if (noteToProcess.summary == null || !noteToProcess.summary.contains(currentMeta)) {
                processTextAuto(noteToProcess, lang, task, format, currentMeta)
            }
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
        val updatedNote = currentNote.copy(
            rawText = newRawText,
            originalRawText = null, 
            summary = null, 
            timestamp = System.currentTimeMillis()
        )
        _note.value = updatedNote
        viewModelScope.launch { 
            noteRepository.update(updatedNote) 
            checkAndTriggerAutoProcess(updatedNote)
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
        viewModelScope.launch { noteRepository.update(updatedNote) }
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
        _isExplaining.value = true
        _explainResult.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val provider = settingsRepository.aiProviderFlow.first()
                val apiKey = if (provider == 1) settingsRepository.groqApiKeyFlow.first() else settingsRepository.geminiApiKeyFlow.first()
                // Mengambil preferensi bahasa AI global untuk explain text
                val targetLanguage = settingsRepository.aiLanguageFlow.first()
                
                if (apiKey.isBlank()) {
                    launch(Dispatchers.Main) {
                        _explainResult.value = "API Key is missing. Please set it in Settings."
                        _isExplaining.value = false
                    }
                    return@launch
                }

                val systemPrompt = """
                    You are an expert encyclopedia. Explain the given term/sentence purely, briefly, and with high relevance. 
                    STRICT RULES YOU MUST OBEY:
                    1. Output language MUST follow: $targetLanguage.
                    2. NO conversational filler, pleasantries, or introductions.
                    3. Format nicely using Markdown if needed, but ABSOLUTELY NO BACKTICKS (`).
                    4. CRITICAL: DO NOT generate tables under any circumstances.
                """.trimIndent()
                
                val userPrompt = "Term to explain: \"$selectedText\""
                
                val resultText = if (provider == 1) { // Groq
                    val request = GroqChatRequest(
                        model = "llama-3.3-70b-versatile",
                        messages = listOf(
                            GroqMessage(role = "system", content = systemPrompt),
                            GroqMessage(role = "user", content = userPrompt)
                        )
                    )
                    RetrofitClient.groqService.generateContent("Bearer $apiKey", request).choices?.firstOrNull()?.message?.content
                } else { // Gemini
                    val request = GenerateContentRequest(
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                        contents = listOf(Content(parts = listOf(Part(text = userPrompt))))
                    )
                    RetrofitClient.service.generateContent(apiKey, request).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
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
        
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val provider = settingsRepository.aiProviderFlow.first()
            val apiKey = if (provider == 1) settingsRepository.groqApiKeyFlow.first() else settingsRepository.geminiApiKeyFlow.first()

            if (apiKey.isBlank()) { 
                launch(Dispatchers.Main) {
                    _error.value = "API Key is required to transcribe accurate audio. Please set it in Settings."
                    _isLoading.value = false
                }
                return@launch 
            }

            var remoteFileName: String? = null
            try {
                val file = File(audioPath)
                if (!file.exists()) throw Exception("Audio file missing from device storage.")

                var transcript: String? = null

                if (provider == 1) { // GROQ PROCESSING
                    if (file.length() > 25 * 1024 * 1024) {
                        launch(Dispatchers.Main) {
                            _error.value = "File is too large for Groq (Max 25MB). Please switch to Gemini in Settings to process long audio files."
                            _isLoading.value = false
                        }
                        return@launch
                    }

                    launch(Dispatchers.Main) { _loadingMessage.value = "Transcribing blazingly fast with Groq..." }
                    
                    val requestFile = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                    val model = "whisper-large-v3-turbo".toRequestBody("text/plain".toMediaTypeOrNull())
                    val format = "json".toRequestBody("text/plain".toMediaTypeOrNull())
                    
                    val response = RetrofitClient.groqService.transcribeAudio("Bearer $apiKey", body, model, format)
                    transcript = response.text?.trim()

                } else { // GEMINI PROCESSING
                    launch(Dispatchers.Main) { _loadingMessage.value = "Uploading audio to Google secure server..." }
                    val mimeType = "audio/mp4"
                    val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
                    val uploadResponse = RetrofitClient.service.uploadFile(
                        apiKey = apiKey, contentLength = file.length(), contentType = mimeType, mimeType = mimeType, fileBytes = requestBody
                    )
                    if (uploadResponse.file == null) throw Exception("Failed to upload file to Gemini server.")
                    
                    val uploadedFileUri = uploadResponse.file.uri
                    remoteFileName = uploadResponse.file.name

                    launch(Dispatchers.Main) { _loadingMessage.value = "Audio uploaded. Gemini is processing..." }
                    
                    val systemPrompt = """
                        You are a highly accurate audio transcription AI. Your ONLY task is to transcribe the audio exactly word-for-word.
                        
                        CRITICAL STRICT RULES:
                        1. NO HALLUCINATION: If the audio is silent, output exactly "[No speech detected]".
                        2. VERBATIM TRANSCRIBE: Transcribe exactly what is spoken word-by-word, including informal words, repeated words, and natural speech flow.
                        3. KEEP PUNCTUATION & CAPITALIZATION: You MUST add accurate punctuation (periods, commas, question marks) and use proper capitalization to make it readable.
                        4. NO GRAMMAR CORRECTION: Absolutely DO NOT fix the speaker's grammatical errors or restructure their sentences.
                        5. NO MARKDOWN & NO MATH FORMATTING: DO NOT add Markdown styling. DO NOT convert spoken math, numbers, or symbols into LaTeX format. Write them as plain text (e.g., write "two squared" or "dua pangkat tiga", do not use ², ^, ${'$'}, or ${'$'}${'$'}).
                        6. Automatically detect and transcribe in the spoken language.
                    """.trimIndent()
                    
                    val request = GenerateContentRequest(
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                        contents = listOf(Content(parts = listOf(Part(fileData = FileData(mimeType = mimeType, fileUri = uploadedFileUri)))))
                    )
                    
                    var fileState = uploadResponse.file.state
                    var attempts = 0
                    while (fileState == "PROCESSING" && attempts < 60) {
                        delay(3000)
                        fileState = RetrofitClient.service.getFile(remoteFileName, apiKey).state
                        attempts++
                    }
                    if (fileState != "ACTIVE") throw Exception("File processing timeout or failed at Google server.")

                    val response = RetrofitClient.service.generateContent(apiKey, request)
                    transcript = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                }

                launch(Dispatchers.Main) {
                    if (transcript != null && !transcript.contains("[No speech detected]")) {
                        val updatedNote = currentNote.copy(rawText = transcript, timestamp = System.currentTimeMillis())
                        _note.value = updatedNote
                        noteRepository.update(updatedNote)
                        
                        checkAndTriggerAutoProcess(updatedNote)
                        
                        launch(Dispatchers.IO) {
                            try { generateTitleFromTranscript(updatedNote, transcript, provider, apiKey) } catch (e: Exception) {}
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
                if (provider == 0 && remoteFileName != null) {
                    try { RetrofitClient.service.deleteFile(remoteFileName, apiKey) } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
    }

    private suspend fun generateTitleFromTranscript(note: NoteEntity, transcript: String, provider: Int, apiKey: String) {
        val systemPrompt = """
            Buat judul singkat 3-5 kata dalam bahasa yang sama dengan teks yang diberikan pengguna.
            RULES: Hanya output judulnya saja. Tanpa tanda kutip, tanpa titik di akhir, dan tanpa penjelasan apapun.
        """.trimIndent()
        val userPrompt = "Teks:\n${transcript.take(500)}"

        val aiTitle = if (provider == 1) { // Groq
            val request = GroqChatRequest(
                model = "llama-3.1-8b-instant",
                messages = listOf(
                    GroqMessage(role = "system", content = systemPrompt),
                    GroqMessage(role = "user", content = userPrompt)
                )
            )
            RetrofitClient.groqService.generateContent("Bearer $apiKey", request).choices?.firstOrNull()?.message?.content?.trim()
        } else { // Gemini
            val request = GenerateContentRequest(
                systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                contents = listOf(Content(parts = listOf(Part(text = userPrompt))))
            )
            RetrofitClient.service.generateContent(apiKey, request).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
        }
        
        if (!aiTitle.isNullOrBlank()) {
            val finalNote = note.copy(title = aiTitle)
            _note.value = finalNote
            noteRepository.update(finalNote)
        }
    }

    private fun processTextAuto(currentNote: NoteEntity, language: String, task: Int, format: Int, metaTag: String) {
        _isLoading.value = true
        _error.value = null
        _loadingMessage.value = "AI Engine is structuring your note..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val provider = settingsRepository.aiProviderFlow.first()
                val apiKey = if (provider == 1) settingsRepository.groqApiKeyFlow.first() else settingsRepository.geminiApiKeyFlow.first()
                
                if (apiKey.isBlank()) { 
                    launch(Dispatchers.Main) {
                        _error.value = "AI Engine Requires an API Key. Please configure it in Settings."
                        _isLoading.value = false
                    }
                    return@launch 
                }

                val taskInstruction = when (task) {
                    0 -> "Task: STRICT PROOFREADING (TIDY UP). Your ONLY job is to fix typos, fix grammar, and remove filler/stuttering words. You MUST preserve the exact original meaning, tone, and length. DO NOT add any explanations, analysis, facts, or context. If the input is just a phrase, output ONLY the corrected phrase. Example: if user inputs 'halo halo bandung, ibukota periangan', you output EXACTLY 'Halo-halo Bandung, ibukota Priangan.' NOTHING ELSE."
                    1 -> "Task: SUMMARIZE. Extract the core information and make a concise summary. Ignore filler words."
                    2 -> "Task: ANALYZE. Extract the main points, underlying sentiments, and any action items."
                    else -> "Task: STRICT PROOFREADING (TIDY UP)."
                }

                val formatInstruction = when (format) {
                    0 -> "Format: Structure the text with Markdown headers (#, ##) ONLY IF the text is long enough to have topic shifts. Use PARAGRAPHS for the details. DO NOT use bullet points. Use **bold** for key concepts/terms, *italic* for tone emphasis or foreign words, and > blockquotes for direct statements/quotes. DO NOT wrap your text in single quotes (') or double quotes (\")."
                    1 -> "Format: Structure the text with Markdown headers (#, ##) ONLY IF the text is long enough to have topic shifts. Use BULLET POINTS (strictly the minus sign '-') for details. NEVER use asterisks ('*'). Use **bold** to highlight the start of a bullet point or key concepts, *italic* for tone emphasis, and > blockquotes for direct statements/quotes."
                    else -> ""
                }

                val systemPrompt = """
                    [SYSTEM: ENGINE MODE ENABLED]
                    You are a strict text processing engine, NOT a conversational chatbot.
                    TARGET LANGUAGE: $language. You MUST translate the output to $language if the input is different.
                    
                    $taskInstruction
                    $formatInstruction
                    
                    CRITICAL STRICT RULES YOU MUST OBEY:
                    1. ZERO YAPPING: Output EXACTLY the final processed text. NO greetings, NO introductions, NO explanations of what you did.
                    2. NO QUOTES FOR ENTIRE TEXT: DO NOT wrap your entire output in quotes or markdown code blocks.
                    3. MANDATORY LATEX CONVERSION: If you detect ANY numbers, mathematical concepts, formulas, equations, or scientific symbols in the original text, convert them into valid LaTeX directly — NO wrapping in any extra characters. Use `${'$'}${'$'}` for block equations and `${'$'}` for inline math. NEVER use `${'$'}` as a plain text symbol. ONLY apply this IF the original text naturally contains math. DO NOT hallucinate math if there is none.
                    4. CRITICAL: DO NOT generate tables under any circumstances.
                """.trimIndent()
                
                val userContent = "Process this text strictly into $language:\n\n${currentNote.rawText}"
                
                val processedText = if (provider == 1) { // Groq
                    val request = GroqChatRequest(
                        model = "llama-3.3-70b-versatile",
                        messages = listOf(
                            GroqMessage(role = "system", content = systemPrompt),
                            GroqMessage(role = "user", content = userContent)
                        )
                    )
                    RetrofitClient.groqService.generateContent("Bearer $apiKey", request).choices?.firstOrNull()?.message?.content
                } else { // Gemini
                    val request = GenerateContentRequest(
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                        contents = listOf(Content(parts = listOf(Part(text = userContent))))
                    )
                    RetrofitClient.service.generateContent(apiKey, request).candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                }
                
                launch(Dispatchers.Main) {
                    if (processedText != null) {
                        // Quotes removal logic aman karena hanya menghapus quote di AWAL dan AKHIR blok teks (kalau AI ngeyel ngasih quotes ke seluruh response)
                        val cleanedText = processedText.trim().removeSurrounding("'", "'").removeSurrounding("\"", "\"")
                        val finalOutput = cleanedText + "\n\n" + metaTag
                        
                        val updatedNote = currentNote.copy(summary = finalOutput, timestamp = System.currentTimeMillis())
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
        fun provideFactory(
            noteId: Int, 
            repository: NoteRepository, 
            settingsRepository: SettingsRepository
        ): ViewModelProvider.Factory = 
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") 
                override fun <T : ViewModel> create(modelClass: Class<T>): T { 
                    return ResultViewModel(noteId, repository, settingsRepository) as T 
                }
            }
    }
}
