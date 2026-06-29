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

    suspend fun exportNoteToBinot(context: Context, note: NoteEntity): Uri? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "shared_notes").apply { mkdirs() }
            val safeTitle = note.title.ifBlank { "Catatan_Binot" }.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val fileName = "${safeTitle}.binot"
            val outFile = File(cacheDir, fileName)

            ZipOutputStream(FileOutputStream(outFile)).use { zos ->
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

                if (note.audioPath != null) {
                    val audioFile = File(note.audioPath)
                    if (audioFile.exists()) {
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
            
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun importFile(context: Context, uri: Uri, repository: NoteRepository): Int? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            
            var isBinotArchive = false
            var jsonData = ""
            var audioTempFile: File? = null

            // LOGIKA BARU: Jangan percaya OS. Langsung bongkar filenya.
            // Kalau ketemu data.json, ini mutlak file .binot (ZIP).
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name == "data.json") {
                                isBinotArchive = true
                                jsonData = zis.bufferedReader(Charsets.UTF_8).readText()
                            } else if (entry.name == "audio.mp4") {
                                val tempAudio = File(context.cacheDir, "temp_import_audio.mp4")
                                tempAudio.outputStream().use { output -> zis.copyTo(output) }
                                audioTempFile = tempAudio
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            } catch (e: Exception) {
                // Biarkan lewat. Ini berarti murni file audio, bukan file ZIP/Binot.
            }

            if (isBinotArchive && jsonData.isNotEmpty()) {
                val json = JSONObject(jsonData)
                var finalAudioPath: String? = null

                if (json.optBoolean("hasAudio") && audioTempFile != null && audioTempFile!!.exists()) {
                    val audioDir = File(context.filesDir, "audio_records").apply { mkdirs() }
                    val newAudioFile = File(audioDir, "RECORD_${System.currentTimeMillis()}.mp4")
                    audioTempFile!!.copyTo(newAudioFile, overwrite = true)
                    audioTempFile!!.delete() 
                    finalAudioPath = newAudioFile.absolutePath
                }

                val newNote = NoteEntity(
                    title = json.optString("title", ""),
                    rawText = json.optString("rawText", ""),
                    summary = if (json.isNull("summary")) null else json.optString("summary"),
                    highlightsInfo = if (json.isNull("highlightsInfo")) null else json.optString("highlightsInfo"),
                    label = if (json.isNull("label")) null else json.optString("label"),
                    audioPath = finalAudioPath
                )
                return@withContext repository.insert(newNote).toInt()
                
            } else {
                // LOGIKA FALLBACK: Kalau nggak ada data.json, perlakukan sebagai file rekaman Audio biasa.
                val audioDir = File(context.filesDir, "audio_records").apply { mkdirs() }
                val newAudioFile = File(audioDir, "RECORD_${System.currentTimeMillis()}.mp4")
                
                contentResolver.openInputStream(uri)?.use { input ->
                    newAudioFile.outputStream().use { output -> input.copyTo(output) }
                }
                
                if (newAudioFile.exists() && newAudioFile.length() > 0) {
                    val newNote = NoteEntity(title = "", rawText = "Pending Transcription", summary = null, audioPath = newAudioFile.absolutePath)
                    return@withContext repository.insert(newNote).toInt()
                } else {
                    newAudioFile.delete()
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
