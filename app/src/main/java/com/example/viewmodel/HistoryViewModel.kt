package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.NoteEntity
import com.example.data.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Logika Search Bar Realtime
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

    // Mode Multi-Select: Hapus
    fun deleteMultiple(ids: Set<Int>) {
        viewModelScope.launch {
            ids.forEach { repository.deleteById(it) }
        }
    }

    // Mode Multi-Select: Kloning (Perbaikan Logika)
    fun cloneMultiple(ids: Set<Int>) {
        viewModelScope.launch {
            ids.forEach { id ->
                // Ambil data pasti langsung dari database berdasarkan ID
                val note = repository.getNoteById(id)
                if (note != null) {
                    val clonedNote = note.copy(
                        id = 0, // 0 agar Room AutoGenerate ID baru
                        title = "${note.title} (Copy)"
                    )
                    repository.insert(clonedNote)
                }
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


