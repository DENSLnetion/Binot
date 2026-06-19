package com.example.viewmodel

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.NoteEntity
import com.example.data.NoteRepository
import com.example.data.RetrofitClient
import com.example.data.SettingsRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

enum class UpdateState { Idle, Checking, Available, Downloading, Downloaded, Error }

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val noteRepository: NoteRepository 
) : ViewModel() {

    val userName: StateFlow<String> = settingsRepository.userNameFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val apiKey: StateFlow<String> = settingsRepository.geminiApiKeyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )
    
    val themeMode: StateFlow<Int> = settingsRepository.themeModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private val _updateState = MutableStateFlow(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _latestVersionStr = MutableStateFlow("")
    val latestVersionStr: StateFlow<String> = _latestVersionStr.asStateFlow()

    private var apkDownloadUrl: String? = null
    private var downloadedApkUri: Uri? = null

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val type = Types.newParameterizedType(List::class.java, NoteEntity::class.java)
    private val adapter = moshi.adapter<List<NoteEntity>>(type)

    fun saveUserName(name: String) {
        viewModelScope.launch { settingsRepository.saveUserName(name) }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch { settingsRepository.saveGeminiApiKey(key) }
    }

    fun saveThemeMode(mode: Int) {
        viewModelScope.launch { settingsRepository.saveThemeMode(mode) }
    }

    fun exportBackup(context: Context, uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val notes = noteRepository.getAllNotesSync()
                val jsonStr = adapter.toJson(notes)
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonStr.toByteArray())
                }
                onResult("Backup successful!")
            } catch (e: Exception) {
                onResult("Backup failed: ${e.message}")
            }
        }
    }

    fun importBackup(context: Context, uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val stringBuilder = StringBuilder()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            stringBuilder.append(line)
                            line = reader.readLine()
                        }
                    }
                }
                
                val jsonStr = stringBuilder.toString()
                val notes = adapter.fromJson(jsonStr)
                
                if (notes != null) {
                    noteRepository.insertNotes(notes) 
                    onResult("Restore successful!")
                } else {
                    onResult("Invalid backup file.")
                }
            } catch (e: Exception) {
                onResult("Restore failed: ${e.message}")
            }
        }
    }

    fun checkForUpdate(currentVersion: String) {
        _updateState.value = UpdateState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val release = RetrofitClient.githubService.getLatestRelease()
                
                if (isVersionGreater(release.tag_name, currentVersion)) {
                    _latestVersionStr.value = release.tag_name
                    apkDownloadUrl = release.assets?.firstOrNull()?.browser_download_url
                    
                    if (apkDownloadUrl != null) {
                        _updateState.value = UpdateState.Available
                    } else {
                        _updateState.value = UpdateState.Error
                    }
                } else {
                    delay(500)
                    _updateState.value = UpdateState.Idle // Berarti udah Up To Date
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _updateState.value = UpdateState.Error
            }
        }
    }

    private fun isVersionGreater(latest: String, current: String): Boolean {
        val l = latest.replace("v", "").replace("V", "").split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.replace("v", "").replace("V", "").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lVal = l.getOrNull(i) ?: 0
            val cVal = c.getOrNull(i) ?: 0
            if (lVal > cVal) return true
            if (lVal < cVal) return false
        }
        return false
    }

    fun startDownload(context: Context) {
        val url = apkDownloadUrl ?: return
        _updateState.value = UpdateState.Downloading
        _downloadProgress.value = 0

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Binot Update ${_latestVersionStr.value}")
            .setDescription("Downloading latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Binot_${_latestVersionStr.value}.apk")
            .setMimeType("application/vnd.android.package-archive")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        viewModelScope.launch(Dispatchers.IO) {
            var isDownloading = true
            while (isDownloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    
                    if (bytesDownloadedIndex != -1 && bytesTotalIndex != -1 && statusIndex != -1) {
                        val bytesDownloaded = cursor.getInt(bytesDownloadedIndex)
                        val bytesTotal = cursor.getInt(bytesTotalIndex)
                        val status = cursor.getInt(statusIndex)
                        
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            _downloadProgress.value = 100
                            downloadedApkUri = downloadManager.getUriForDownloadedFile(downloadId)
                            _updateState.value = UpdateState.Downloaded
                            isDownloading = false
                            downloadedApkUri?.let { uri -> promptInstall(context, uri) }
                            
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            _updateState.value = UpdateState.Error
                            isDownloading = false
                        } else {
                            if (bytesTotal > 0) {
                                _downloadProgress.value = ((bytesDownloaded * 100L) / bytesTotal).toInt()
                            }
                        }
                    }
                    cursor.close()
                }
                delay(500)
            }
        }
    }

    fun promptInstall(context: Context, uri: Uri? = downloadedApkUri) {
        if (uri == null) return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            _updateState.value = UpdateState.Error
        }
    }

    companion object {
        fun provideFactory(
            settingsRepository: SettingsRepository,
            noteRepository: NoteRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(settingsRepository, noteRepository) as T
                }
            }
    }
}

