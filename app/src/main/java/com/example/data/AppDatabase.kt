package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// DB VERSION 4: Tambah isTrashed
@Database(entities = [NoteEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // MIGRATION 3 ke 4: Nambahin isTrashed biar app lu ga force close pas update
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "binot_database"
                )
                .addMigrations(MIGRATION_3_4) // Eksekusi migrasinya
                .fallbackToDestructiveMigration() // Aman jika migrasi gagal
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

