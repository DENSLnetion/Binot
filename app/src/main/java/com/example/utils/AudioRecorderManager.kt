package com.example.utils

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlin.random.Random
import java.util.Locale

class AudioRecorderManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFilePath: String? = null
    
    private var currentRecordMode = 0 

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    @Volatile
    private var isPaused = false
    private var lastIsEmulator = false

    private var originalMusicVolume = -1
    private var originalSystemVolume = -1
    private var originalRingVolume = -1
    private var originalNotificationVolume = -1
    private var originalAlarmVolume = -1
    private var originalVoiceCallVolume = -1
    private var originalRingerMode = -1
    private var originalHapticFeedbackStatus = -1

    private fun forceMuteAllBeeps() {
        try {
            if (originalMusicVolume == -1) {
                originalMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                originalSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
                originalRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                originalNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                originalVoiceCallVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                originalRingerMode = audioManager.ringerMode
            }

            try { audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT } catch (e: Exception) {}

            try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) } catch (e: Exception) {}
            try { audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0) } catch (e: Exception) {}
            try { audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0) } catch (e: Exception) {}
            try { audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0) } catch (e: Exception) {}
            try { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0) } catch (e: Exception) {}
            try { audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0) } catch (e: Exception) {}

            try {
                originalHapticFeedbackStatus = Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1)
                Settings.System.putInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 0)
            } catch (e: Exception) {}

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreAllVolumes() {
        try {
            if (originalRingerMode != -1) {
                try { audioManager.ringerMode = originalRingerMode } catch (e: Exception) {}
                originalRingerMode = -1
            }

            if (originalMusicVolume != -1) {
                try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalMusicVolume, 0) } catch (e: Exception) {}
                originalMusicVolume = -1
            }
            if (originalSystemVolume != -1) {
                try { audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVolume, 0) } catch (e: Exception) {}
                originalSystemVolume = -1
            }
            if (originalRingVolume != -1) {
                try { audioManager.setStreamVolume(AudioManager.STREAM_RING, originalRingVolume, 0) } catch (e: Exception) {}
                originalRingVolume = -1
            }
            if (originalNotificationVolume != -1) {
                try { audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalNotificationVolume, 0) } catch (e: Exception) {}
                originalNotificationVolume = -1
            }
            if (originalAlarmVolume != -1) {
                try { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0) } catch (e: Exception) {}
                originalAlarmVolume = -1
            }
            if (originalVoiceCallVolume != -1) {
                try { audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, originalVoiceCallVolume, 0) } catch (e: Exception) {}
                originalVoiceCallVolume = -1
            }

            if (originalHapticFeedbackStatus != -1) {
                try {
                    Settings.System.putInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, originalHapticFeedbackStatus)
                } catch (e: Exception) {}
                originalHapticFeedbackStatus = -1
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startRecording(isEmulator: Boolean = false, mode: Int = 0) {
        if (_isRecording.value) return
        
        _isRecording.value = true
        _recognizedText.value = ""
        isPaused = false
        lastIsEmulator = isEmulator
        currentRecordMode = mode
        
        forceMuteAllBeeps()
        
        if (mode == 1) { 
            prepareMediaRecorder()
            startFakeAmplitude() 
        } else { 
            if (isEmulator || !SpeechRecognizer.isRecognitionAvailable(context)) {
                startSimulatedRecording()
            } else {
                initSpeechRecognizer()
            }
        }
    }
    
    private fun prepareMediaRecorder() {
        val dir = File(context.filesDir, "audio_records")
        if (!dir.exists()) dir.mkdirs()
        
        val file = File(dir, "RECORD_${System.currentTimeMillis()}.mp4")
        currentAudioFilePath = file.absolutePath

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currentAudioFilePath)
            
            try {
                prepare()
                start()
            } catch (e: IOException) {
                e.printStackTrace()
                currentAudioFilePath = null
            }
        }
    }

    fun pauseRecording() {
        if (!_isRecording.value || isPaused) return
        isPaused = true
        
        if (currentRecordMode == 1) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try { mediaRecorder?.pause() } catch (e: Exception) { e.printStackTrace() }
            }
        } else {
            speechRecognizer?.stopListening()
        }
        
        _amplitude.value = 0f
    }

    fun resumeRecording() {
        if (!_isRecording.value || !isPaused) return
        isPaused = false

        if (currentRecordMode == 1) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try { mediaRecorder?.resume() } catch (e: Exception) { e.printStackTrace() }
            }
            startFakeAmplitude()
        } else {
            if (lastIsEmulator || !SpeechRecognizer.isRecognitionAvailable(context)) {
                startSimulatedRecording()
            } else {
                initSpeechRecognizer()
            }
        }
    }

    private fun initSpeechRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        if (_isRecording.value && !isPaused) _amplitude.value = (rmsdB / 10f).coerceIn(0f, 1f)
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _amplitude.value = 0f
                    }
                    override fun onError(error: Int) {
                        if (_isRecording.value && !isPaused) {
                            initSpeechRecognizer()
                        } else if (!_isRecording.value) {
                            _amplitude.value = 0f
                            restoreAllVolumes()
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val current = _recognizedText.value
                            _recognizedText.value = current + (if (current.isNotEmpty()) "\n" else "") + matches[0]
                        }
                        if (_isRecording.value && !isPaused) {
                            initSpeechRecognizer()
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                
                startListening(intent)
            }
        } catch (e: Exception) {
            _isRecording.value = false
            restoreAllVolumes()
            e.printStackTrace()
        }
    }

    // Logic Murni: Perhitungan Amplitude MediaRecorder diubah ke skala Logaritmik (dB) dan Polling di-kencengin!
    private fun startFakeAmplitude() {
        coroutineScope.launch {
            while (_isRecording.value && !isPaused && currentRecordMode == 1) {
                _amplitude.value = if (mediaRecorder != null) {
                     try { 
                         val amp = mediaRecorder!!.maxAmplitude
                         if (amp > 0) {
                             // Konversi raw linear ke decibel biar sensitif nangkap suara pelan
                             val db = 20 * Math.log10(amp.toDouble() / 32767.0)
                             // Mapping rentang -45dB (silence) s/d 0dB ke range 0.02 s/d 1.0
                             ((db + 45) / 45).coerceIn(0.02, 1.0).toFloat()
                         } else {
                             0.02f
                         }
                     } catch (e: Exception) { 0.02f }
                } else {
                     0.02f 
                }
                delay(30) // 30ms delay = ~33 FPS! Dijamin ngacir dan lincah grafiknya.
            }
            _amplitude.value = 0f
        }
    }

    private fun startSimulatedRecording() {
        val thread = Thread {
            val phrases = listOf("Ini adalah simulasi.", "Binot merekam.")
            var phraseIndex = 0
            
            while (_isRecording.value && !isPaused) {
                _amplitude.value = Random.nextFloat()
                Thread.sleep(200)
                if (Random.nextInt(10) > 7 && phraseIndex < phrases.size) {
                    val current = _recognizedText.value
                    _recognizedText.value = current + (if (current.isNotEmpty()) " " else "") + phrases[phraseIndex]
                    phraseIndex++
                }
            }
            _amplitude.value = 0f
        }
        thread.start()
    }

    fun stopRecording(): String? {
        _isRecording.value = false
        isPaused = false
        
        if (currentRecordMode == 1) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (e: Exception) {
                e.printStackTrace()
                currentAudioFilePath = null
            }
            mediaRecorder = null
        } else {
            speechRecognizer?.stopListening()
            currentAudioFilePath = null 
        }
        
        _amplitude.value = 0f

        coroutineScope.launch {
            delay(600)
            restoreAllVolumes()
        }
        
        val finalPath = currentAudioFilePath
        currentAudioFilePath = null
        return finalPath
    }
}

