package com.example.utils

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.random.Random
import java.util.Locale

class AudioRecorderManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    
    // AudioManager untuk mematikan suara "Beep" sistem Android
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    fun startRecording(isEmulator: Boolean = false) {
        if (_isRecording.value) return
        
        _isRecording.value = true
        _recognizedText.value = ""
        
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
                    override fun onReadyForSpeech(params: Bundle?) {
                        // Unmute sistem setelah mesin siap biar fitur lain ga ikut bisu
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        if (_isRecording.value) _amplitude.value = (rmsdB / 10f).coerceIn(0f, 1f)
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _amplitude.value = 0f
                    }
                    override fun onError(error: Int) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                        if (_isRecording.value) {
                            initSpeechRecognizer()
                        } else {
                            _amplitude.value = 0f
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
                
                // HACK MUTE: Matikan stream music sebelum startListening biar gak ada suara Bip
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                startListening(intent)
            }
        } catch (e: Exception) {
            _isRecording.value = false
            // Jaga-jaga error, unmute lagi biar HP user ga rusak settingan suaranya
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            e.printStackTrace()
        }
    }

    private fun startSimulatedRecording() {
        val thread = Thread {
            val phrases = listOf(
                "Ini adalah simulasi rekaman suara pada emulator.",
                "Binot sedang merekam apa yang Anda pikirkan.",
                "Gagasan cemerlang datang saat Anda merenungkan kehidupan.",
                "Aplikasi akan merangkum semua ucapan dengan teknologi AI dari Gemini."
            )
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
    }
}

