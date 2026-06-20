package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.NoteRepository
import com.example.data.SettingsRepository
import com.example.utils.AudioRecorderManager

class BinotApplication : Application() {
    
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(private val application: Application) {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(application, AppDatabase::class.java, "binot_db").build()
    }
    
    val noteRepository: NoteRepository by lazy {
        NoteRepository(database.noteDao())
    }
    
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(application)
    }
    
    val audioRecorderManager: AudioRecorderManager by lazy {
        AudioRecorderManager(application)
    }
}
