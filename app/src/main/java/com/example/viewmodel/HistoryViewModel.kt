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

    private val _selectedLabels = MutableStateFlow<Set<String>>(emptySet())
    val selectedLabels: StateFlow<Set<String>> = _selectedLabels.asStateFlow()

    private val _isMultiSelectLabelMode = MutableStateFlow(false)
    val isMultiSelectLabelMode: StateFlow<Boolean> = _isMultiSelectLabelMode.asStateFlow()

    private val _sortMode = MutableStateFlow(0)
    val sortMode: StateFlow<Int> = _sortMode.asStateFlow()

    private val _latestRelease = MutableStateFlow<GithubRelease?>(null)
    val latestRelease: StateFlow<GithubRelease?> = _latestRelease.asStateFlow()

    val trashedNotes: StateFlow<List<NoteEntity>> = repository.trashedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uniqueLabels: StateFlow<List<String>> = repository.allNotes.map { notes ->
        val systemNote = notes.find { it.title == "[[BINOT_SYSTEM_LABELS]]" }
        val customLabels = systemNote?.rawText?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        val noteLabels = notes.filter { it.title != "[[BINOT_SYSTEM_LABELS]]" }
            .flatMap { it.label?.split("|")?.map { l -> l.trim() }?.filter { l -> l.isNotBlank() } ?: emptyList() }
        
        (customLabels + noteLabels).distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredNotes: StateFlow<List<NoteEntity>> = combine(
        repository.allNotes, _searchQuery, _selectedLabels, _sortMode
    ) { notes, query, labels, sort ->

        val realNotes = notes.filter { it.title != "[[BINOT_SYSTEM_LABELS]]" }

        val labelFilteredNotes = if (labels.isEmpty()) realNotes else realNotes.filter { note ->
            val noteLabels = note.label?.split("|")?.map { it.trim() }?.toSet() ?: emptySet()
            labels.all { it in noteLabels }
        }

        val searchedNotes = if (query.isBlank()) {
            labelFilteredNotes
        } else {
            labelFilteredNotes.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.rawText.contains(query, ignoreCase = true) ||
                (it.summary?.contains(query, ignoreCase = true) == true)
            }
        }

        when (sort) {
            1 -> searchedNotes.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenBy { it.timestamp })
            2 -> searchedNotes.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenBy { it.title.lowercase() })
            else -> searchedNotes.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.timestamp })
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun toggleLabelFilter(label: String) {
        if (_isMultiSelectLabelMode.value) {
            _selectedLabels.value = if (label in _selectedLabels.value) {
                _selectedLabels.value - label
            } else {
                _selectedLabels.value + label
            }
        } else {
            _selectedLabels.value = if (_selectedLabels.value == setOf(label)) {
                emptySet()
            } else {
                setOf(label)
            }
        }
    }

    fun clearLabelFilter() {
        _selectedLabels.value = emptySet()
    }

    fun setMultiSelectLabelMode(enabled: Boolean) {
        _isMultiSelectLabelMode.value = enabled
        if (!enabled) {
            _selectedLabels.value = emptySet()
        }
    }

    fun deleteMultipleLabels(labels: Set<String>) {
        labels.forEach { deleteLabel(it) }
    }

    fun setSortMode(mode: Int) {
        _sortMode.value = mode
    }

    fun createIndependentLabel(label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val notes = repository.getAllNotesSync()
            val sysNote = notes.find { it.title == "[[BINOT_SYSTEM_LABELS]]" }
            
            if (sysNote != null) {
                val existingLabels = sysNote.rawText.split("|").filter { it.isNotBlank() }.toMutableSet()
                existingLabels.add(label)
                repository.update(sysNote.copy(rawText = existingLabels.joinToString("|")))
            } else {
                val newSysNote = NoteEntity(
                    title = "[[BINOT_SYSTEM_LABELS]]", 
                    rawText = label, 
                    summary = null
                )
                repository.insert(newSysNote)
            }
        }
    }

    fun renameLabel(oldLabel: String, newLabel: String) {
        if (oldLabel.isBlank() || newLabel.isBlank() || oldLabel == newLabel) return
        viewModelScope.launch(Dispatchers.IO) {
            val notes = repository.getAllNotesSync()

            notes.filter { it.title != "[[BINOT_SYSTEM_LABELS]]" }.forEach { note ->
                val labels = note.label?.split("|")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                if (labels.contains(oldLabel)) {
                    val updatedLabels = labels.map { if (it == oldLabel) newLabel else it }.distinct()
                    repository.update(note.copy(label = updatedLabels.joinToString("|")))
                }
            }

            val sysNote = notes.find { it.title == "[[BINOT_SYSTEM_LABELS]]" }
            if (sysNote != null) {
                val existingLabels = sysNote.rawText.split("|").filter { it.isNotBlank() }.toMutableSet()
                existingLabels.remove(oldLabel)
                existingLabels.add(newLabel)
                repository.update(sysNote.copy(rawText = existingLabels.joinToString("|")))
            }

            if (oldLabel in _selectedLabels.value) {
                _selectedLabels.value = (_selectedLabels.value - oldLabel) + newLabel
            }
        }
    }

    fun deleteLabel(label: String) {
        if (label.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val notes = repository.getAllNotesSync()

            notes.filter { it.title != "[[BINOT_SYSTEM_LABELS]]" }.forEach { note ->
                val labels = note.label?.split("|")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                if (labels.contains(label)) {
                    val updatedLabels = labels.filter { it != label }
                    val newLabelString = if (updatedLabels.isEmpty()) null else updatedLabels.joinToString("|")
                    repository.update(note.copy(label = newLabelString))
                }
            }

            val sysNote = notes.find { it.title == "[[BINOT_SYSTEM_LABELS]]" }
            if (sysNote != null) {
                val existingLabels = sysNote.rawText.split("|").filter { it.isNotBlank() }.toMutableSet()
                existingLabels.remove(label)
                if (existingLabels.isEmpty()) {
                    repository.deleteById(sysNote.id)
                } else {
                    repository.update(sysNote.copy(rawText = existingLabels.joinToString("|")))
                }
            }

            if (label in _selectedLabels.value) {
                _selectedLabels.value = _selectedLabels.value - label
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

    private val _recentlyDeleted = MutableStateFlow<List<NoteEntity>>(emptyList())
    val recentlyDeleted: StateFlow<List<NoteEntity>> = _recentlyDeleted.asStateFlow()

    fun deleteMultiple(ids: Set<Int>) {
        viewModelScope.launch {
            val notesToTrash = ids.mapNotNull { repository.getNoteById(it) }
            _recentlyDeleted.value = notesToTrash
            // FIX LOGIKA BUG: Jangan cabut status isPinned saat masuk tong sampah.
            // Biarkan saja, query di DAO tetap tidak akan menampilkan karena isTrashed = true.
            notesToTrash.forEach { repository.update(it.copy(isTrashed = true)) }
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            _recentlyDeleted.value.forEach { note ->
                repository.update(note.copy(isTrashed = false))
            }
            _recentlyDeleted.value = emptyList()
        }
    }

    fun clearRecentlyDeleted() {
        _recentlyDeleted.value = emptyList()
    }

    fun restoreMultipleFromTrash(ids: Set<Int>) {
        viewModelScope.launch {
            ids.forEach { id ->
                val note = repository.getNoteById(id)
                if (note != null) repository.update(note.copy(isTrashed = false))
            }
        }
    }

    fun deletePermanentlyMultiple(ids: Set<Int>) {
        viewModelScope.launch {
            ids.forEach { repository.deleteById(it) }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
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
