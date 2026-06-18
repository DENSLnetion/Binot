package com.example.data

class NoteRepository(private val noteDao: NoteDao) {
    val allNotes = noteDao.getAllNotes()

    suspend fun insert(note: NoteEntity) = noteDao.insertNote(note)
    suspend fun update(note: NoteEntity) = noteDao.updateNote(note)
    suspend fun deleteById(id: Int) = noteDao.deleteNoteById(id)
    suspend fun getNoteById(id: Int) = noteDao.getNoteById(id)
}
