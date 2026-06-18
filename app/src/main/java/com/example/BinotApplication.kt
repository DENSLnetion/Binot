package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.NoteRepository
import com.example.data.SettingsRepository
import com.example.utils.AudioRecorderManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
        NoteRepository(database.noteDao()).also { repo ->
            kotlinx.coroutines.GlobalScope.launch {
                val flow = repo.allNotes
                if (repo.getNoteById(1) == null) {
                    repo.insert(com.example.data.NoteEntity(
                        title = "Ide Proyek Android",
                        rawText = "Kita butuh aplikasi yang menggunakan AI untuk merangkum catatan. Sangat Expressive dan memukau secara visual, menggunakan animasi terbaru dari jetpack compose.",
                        summary = "# Struktur Aplikasi Android\n\nAplikasi harus mengutamakan **estetika**. Berikut fiturnya:\n- Animasi morphing pada tombol\n- Tema terang/gelap/amoled dinamis\n- Integrasi Room database\n\n_Pastikan berjalan super lancar dalam 60fps!_"
                    ))
                    repo.insert(com.example.data.NoteEntity(
                        title = "Rencana Liburan",
                        rawText = "Saya ingin pergi ke pantai akhir pekan ini, jangan lupa bawa tabir surya dan kacamata hitam. Kita juga bisa mencoba restoran seafood baru yang ada di dekat sana.",
                        summary = null
                    ))
                     repo.insert(com.example.data.NoteEntity(
                        title = "Desain Material 3 Expressive",
                        rawText = "Material 3 Expressive mengedepankan bentuk-bentuk dinamis, corner radius membulat ekstrem pada M3, pergerakan berbasis spring yang responsif ke gestur jari, serta tata letak tipografi yang tebal.",
                        summary = "# Material You: Expressive\n\nSangat menawan, fokus pada UX:\n- **Animasi Spring** dengan damping rendah, stiffness yang reaktif terhadap touch duration.\n- **Warna Dinamis** yang kontras.\n\nSangat elegan dan terpadu untuk ekosistem Google."
                    ))
                }
            }
        }
    }
    
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(application)
    }
    
    val audioRecorderManager: AudioRecorderManager by lazy {
        AudioRecorderManager(application)
    }
}
