package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase

// Versi database naik jadi 2 karena ada tambahan kolom isPinned
@Database(entities = [NoteEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}
