package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    // Ambil catatan aktif (bukan di tong sampah)
    @Query("SELECT * FROM notes WHERE isTrashed = 0 ORDER BY isPinned DESC, timestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    // Ambil catatan yang ada di tong sampah
    @Query("SELECT * FROM notes WHERE isTrashed = 1 ORDER BY timestamp DESC")
    fun getTrashedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrashed = 0")
    suspend fun getAllNotesSync(): List<NoteEntity>

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()

    // Hapus semua yang ada di tong sampah secara permanen
    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun emptyTrash()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long
    
    @Update
    suspend fun updateNote(note: NoteEntity)

    // Delete permanen per ID
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Int)
    
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Int): NoteEntity?
}

