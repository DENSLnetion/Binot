package com.example.data

class NoteRepository(private val noteDao: NoteDao) {
    val allNotes = noteDao.getAllNotes()
    val trashedNotes = noteDao.getTrashedNotes()

    suspend fun getAllNotesSync() = noteDao.getAllNotesSync()
    suspend fun deleteAllNotes() = noteDao.deleteAllNotes()
    suspend fun emptyTrash() = noteDao.emptyTrash()
    suspend fun insertNotes(notes: List<NoteEntity>) = noteDao.insertNotes(notes)

    suspend fun insert(note: NoteEntity) = noteDao.insertNote(note)
    suspend fun update(note: NoteEntity) = noteDao.updateNote(note)
    suspend fun deleteById(id: Int) = noteDao.deleteNoteById(id)
    suspend fun getNoteById(id: Int) = noteDao.getNoteById(id)
}

