package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.NoteEntity
import com.example.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class HistoryViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredNotes: StateFlow<List<NoteEntity>> = combine(
        repository.allNotes, _searchQuery
    ) { notes, query ->
        if (query.isBlank()) {
            notes
        } else {
            notes.filter { 
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
                    val clonedNote = note.copy(
                        id = 0, 
                        title = "${note.title} (Copy)",
                        isPinned = false 
                    )
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

                val note = NoteEntity(
                    title = "Imported Audio",
                    rawText = "", 
                    summary = null,
                    isPinned = false,
                    audioPath = file.absolutePath
                )
                val id = repository.insert(note).toInt()
                launch(Dispatchers.Main) { onResult(id) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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


