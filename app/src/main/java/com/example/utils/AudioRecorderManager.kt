package com.example.utils

import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
import kotlin.random.Random
import java.util.Locale

class AudioRecorderManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    // Variabel buat nyimpen status awal haptic feedback
    private var originalHapticFeedbackStatus = -1

    // ULTIMATE MUTE HACK V2: Bantai semua stream, termasuk panggilan suara, dan matikan haptic feedback
    private fun forceMuteAllBeeps() {
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE, 0)
            // Tambahan: Siapa tau Bip-nya nyusup lewat jalur telepon
            audioManager.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, AudioManager.ADJUST_MUTE, 0) 

            // Matikan Haptic Feedback (suara "tek" saat UI ditekan)
            originalHapticFeedbackStatus = Settings.System.getInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1)
            Settings.System.putInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 0)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Balikin normal
    private fun restoreAllVolumes() {
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_UNMUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, AudioManager.ADJUST_UNMUTE, 0)

            // Kembalikan status Haptic Feedback
            if (originalHapticFeedbackStatus != -1) {
                Settings.System.putInt(context.contentResolver, Settings.System.SOUND_EFFECTS_ENABLED, originalHapticFeedbackStatus)
                originalHapticFeedbackStatus = -1
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startRecording(isEmulator: Boolean = false) {
        if (_isRecording.value) return
        
        _isRecording.value = true
        _recognizedText.value = ""
        
        forceMuteAllBeeps()
        
        if (isEmulator || !SpeechRecognizer.isRecognitionAvailable(context)) {
            startSimulatedRecording()
            return
        }

        initSpeechRecognizer()
    }

    private fun initSpeechRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        if (_isRecording.value) _amplitude.value = (rmsdB / 10f).coerceIn(0f, 1f)
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _amplitude.value = 0f
                    }
                    override fun onError(error: Int) {
                        if (_isRecording.value) {
                            initSpeechRecognizer()
                        } else {
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
                        if (_isRecording.value) {
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

    private fun startSimulatedRecording() {
        val thread = Thread {
            val phrases = listOf("Ini adalah simulasi.", "Binot merekam.")
            var phraseIndex = 0
            
            while (_isRecording.value) {
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

    fun stopRecording() {
        _isRecording.value = false
        speechRecognizer?.stopListening()
        _amplitude.value = 0f
        
        coroutineScope.launch {
            delay(600)
            restoreAllVolumes()
        }
    }
}


