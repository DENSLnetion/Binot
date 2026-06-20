package com.example.data

class NoteRepository(private val noteDao: NoteDao) {
    val allNotes = noteDao.getAllNotes()
    val trashedNotes = noteDao.getTrashedNotes() // Flow buat layar Trash

    suspend fun getAllNotesSync() = noteDao.getAllNotesSync()
    suspend fun deleteAllNotes() = noteDao.deleteAllNotes()
    suspend fun emptyTrash() = noteDao.emptyTrash() // Kosongin sampah
    suspend fun insertNotes(notes: List<NoteEntity>) = noteDao.insertNotes(notes)

    suspend fun insert(note: NoteEntity) = noteDao.insertNote(note)
    suspend fun update(note: NoteEntity) = noteDao.updateNote(note)
    suspend fun deleteById(id: Int) = noteDao.deleteNoteById(id) // Delete Permanen
    suspend fun getNoteById(id: Int) = noteDao.getNoteById(id)
}

