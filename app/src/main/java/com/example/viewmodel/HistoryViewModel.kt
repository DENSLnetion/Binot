package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GithubRelease
import com.example.data.NoteEntity
import com.example.data.NoteRepository
import com.example.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class HistoryViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedLabel = MutableStateFlow<String?>(null)
    val selectedLabel: StateFlow<String?> = _selectedLabel.asStateFlow()

    private val _latestRelease = MutableStateFlow<GithubRelease?>(null)
    val latestRelease: StateFlow<GithubRelease?> = _latestRelease.asStateFlow()

    // LOGIKA HACKER: Mesin Penyedot Label Mandiri
    val uniqueLabels: StateFlow<List<String>> = repository.allNotes.map { notes ->
        // 1. Cari catatan kamus sembunyi
        val systemNote = notes.find { it.title == "[[BINOT_SYSTEM_LABELS]]" }
        // 2. Ekstrak label mandiri dari rawText-nya
        val customLabels = systemNote?.rawText?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        // 3. Ekstrak label dari catatan-catatan asli
        val noteLabels = notes.filter { it.title != "[[BINOT_SYSTEM_LABELS]]" }.mapNotNull { it.label }
        
        // 4. Gabungin semuanya tanpa duplikat
        (customLabels + noteLabels).distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredNotes: StateFlow<List<NoteEntity>> = combine(
        repository.allNotes, _searchQuery, _selectedLabel
    ) { notes, query, label ->
        
        // WAJIB: Sembunyiin catatan kamus dari UI utama!
        val realNotes = notes.filter { it.title != "[[BINOT_SYSTEM_LABELS]]" }
        
        val labelFilteredNotes = if (label == null) realNotes else realNotes.filter { it.label == label }
        
        if (query.isBlank()) {
            labelFilteredNotes
        } else {
            labelFilteredNotes.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.rawText.contains(query, ignoreCase = true) || 
                (it.summary?.contains(query, ignoreCase = true) == true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun filterByLabel(label: String?) {
        _selectedLabel.value = label
    }

    // LOGIKA BARU: Cuma bikin label ke buku tabungan, KAGA bikin catatan kosong!
    fun createIndependentLabel(label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val notes = repository.getAllNotesSync()
            val sysNote = notes.find { it.title == "[[BINOT_SYSTEM_LABELS]]" }
            
            if (sysNote != null) {
                // Tambahin ke kamus yang udah ada
                val existingLabels = sysNote.rawText.split("|").filter { it.isNotBlank() }.toMutableSet()
                existingLabels.add(label)
                repository.update(sysNote.copy(rawText = existingLabels.joinToString("|")))
            } else {
                // Bikin kamus baru kalau belum ada
                val newSysNote = NoteEntity(
                    title = "[[BINOT_SYSTEM_LABELS]]", 
                    rawText = label, 
                    summary = null
                )
                repository.insert(newSysNote)
            }
        }
    }

    fun checkForAppUpdate(currentVersion: String) {
        if (_latestRelease.value != null) return 
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val release = RetrofitClient.githubService.getLatestRelease()
                if (isVersionGreater(release.tag_name, currentVersion)) {
                    _latestRelease.value = release
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun isVersionGreater(latest: String, current: String): Boolean {
        val l = latest.replace("v", "").replace("V", "").split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.replace("v", "").replace("V", "").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lVal = l.getOrNull(i) ?: 0
            val cVal = c.getOrNull(i) ?: 0
            if (lVal > cVal) return true
            if (lVal < cVal) return false
        }
        return false
    }

    fun dismissUpdateNotification() {
        _latestRelease.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteMultiple(ids: Set<Int>) {
        viewModelScope.launch {
            ids.forEach { repository.deleteById(it) }
        }
    }

    fun cloneMultiple(ids: Set<Int>) {
        viewModelScope.launch {
            ids.forEach { id ->
                val note = repository.getNoteById(id)
                if (note != null) {
                    val clonedNote = note.copy(id = 0, title = "${note.title} (Copy)", isPinned = false)
                    repository.insert(clonedNote)
                }
            }
        }
    }

    fun togglePinMultiple(ids: Set<Int>, pinState: Boolean) {
        viewModelScope.launch {
            ids.forEach { id ->
                val note = repository.getNoteById(id)
                if (note != null) {
                    repository.update(note.copy(isPinned = pinState))
                }
            }
        }
    }

    fun importAudio(context: Context, uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileName = "Binot_Import_${System.currentTimeMillis()}.mp3"
                val file = File(context.cacheDir, fileName)
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()

                val note = NoteEntity(title = "Imported Audio", rawText = "", summary = null, audioPath = file.absolutePath)
                val id = repository.insert(note).toInt()
                launch(Dispatchers.Main) { onResult(id) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    companion object {
        fun provideFactory(repository: NoteRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HistoryViewModel(repository) as T
                }
            }
    }
}

