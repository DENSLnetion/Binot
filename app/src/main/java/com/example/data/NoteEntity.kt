package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val rawText: String,
    val summary: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val audioPath: String? = null,
    // LOGIKA BARU: Kolom buat nyimpen Label/Kategori catatan
    val label: String? = null
)


