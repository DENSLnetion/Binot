package com.example.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.NoteEntity
import com.example.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ImportExportHelper {

    // Logika ZIP dengan metode STORED (Nol Kompresi). Aman dari OOM dan hemat baterai.
    suspend fun exportNoteToBinot(context: Context, note: NoteEntity): Uri? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "shared_notes").apply { mkdirs() }
            val safeTitle = note.title.ifBlank { "Catatan_Binot" }.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val fileName = "${safeTitle}.binot"
            val outFile = File(cacheDir, fileName)

            ZipOutputStream(FileOutputStream(outFile)).use { zos ->
                // 1. Tulis data.json
                val json = JSONObject().apply {
                    put("version", 1)
                    put("title", note.title)
                    put("rawText", note.rawText)
                    put("summary", note.summary)
                    put("highlightsInfo", note.highlightsInfo)
                    put("label", note.label)
                    put("hasAudio", note.audioPath != null)
                }
                val jsonBytes = json.toString().toByteArray(Charsets.UTF_8)
                val jsonEntry = ZipEntry("data.json").apply {
                    method = ZipEntry.STORED
                    size = jsonBytes.size.toLong()
                    compressedSize = jsonBytes.size.toLong()
                    val crc = CRC32().apply { update(jsonBytes) }
                    this.crc = crc.value
                }
                zos.putNextEntry(jsonEntry)
                zos.write(jsonBytes)
                zos.closeEntry()

                // 2. Tulis audio.mp4 (kalau ada)
                if (note.audioPath != null) {
                    val audioFile = File(note.audioPath)
                    if (audioFile.exists()) {
                        // Untuk metode STORED, kita WAJIB ngitung ukuran & CRC32 sebelum write.
                        val crc = CRC32()
                        var size = 0L
                        audioFile.inputStream().use { fis ->
                            val buf = ByteArray(8192)
                            var len: Int
                            while (fis.read(buf).also { len = it } > 0) {
                                crc.update(buf, 0, len)
                                size += len
                            }
                        }
                        val audioEntry = ZipEntry("audio.mp4").apply {
                            method = ZipEntry.STORED
                            this.size = size
                            compressedSize = size
                            this.crc = crc.value
                        }
                        zos.putNextEntry(audioEntry)
                        audioFile.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            
            // Generate URI aman via FileProvider
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Logika Import Universal (Terima Audio MP4 biasa, atau .binot ZIP archive)
    suspend fun importFile(context: Context, uri: Uri, repository: NoteRepository): Int? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri)
            val isZip = mimeType?.contains("zip") == true || uri.toString().endsWith(".binot") || uri.toString().endsWith(".zip")

            // Jika yang di-import murni Audio biasa (Legacy behavior)
            if (!isZip) {
                val audioDir = File(context.filesDir, "audio_records").apply { mkdirs() }
                val newAudioFile = File(audioDir, "RECORD_${System.currentTimeMillis()}.mp4")
                contentResolver.openInputStream(uri)?.use { input ->
                    newAudioFile.outputStream().use { output -> input.copyTo(output) }
                }
                val newNote = NoteEntity(title = "", rawText = "Pending Transcription", summary = null, audioPath = newAudioFile.absolutePath)
                return@withContext repository.insert(newNote).toInt()
            }

            // Jika yang di-import adalah file .binot (ZIP Archive)
            var jsonData = ""
            var audioTempFile: File? = null

            contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            "data.json" -> {
                                jsonData = zis.bufferedReader(Charsets.UTF_8).readText()
                            }
                            "audio.mp4" -> {
                                val tempAudio = File(context.cacheDir, "temp_import_audio.mp4")
                                tempAudio.outputStream().use { output -> zis.copyTo(output) }
                                audioTempFile = tempAudio
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            if (jsonData.isNotEmpty()) {
                val json = JSONObject(jsonData)
                var finalAudioPath: String? = null

                if (json.optBoolean("hasAudio") && audioTempFile != null && audioTempFile!!.exists()) {
                    val audioDir = File(context.filesDir, "audio_records").apply { mkdirs() }
                    val newAudioFile = File(audioDir, "RECORD_${System.currentTimeMillis()}.mp4")
                    audioTempFile!!.copyTo(newAudioFile, overwrite = true)
                    audioTempFile!!.delete() // Bersihkan cache
                    finalAudioPath = newAudioFile.absolutePath
                }

                // Injeksi database (Aman dari proses AI otomatis karena summary tidak kosong)
                val newNote = NoteEntity(
                    title = json.optString("title", ""),
                    rawText = json.optString("rawText", ""),
                    summary = if (json.isNull("summary")) null else json.optString("summary"),
                    highlightsInfo = if (json.isNull("highlightsInfo")) null else json.optString("highlightsInfo"),
                    label = if (json.isNull("label")) null else json.optString("label"),
                    audioPath = finalAudioPath
                )
                return@withContext repository.insert(newNote).toInt()
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
