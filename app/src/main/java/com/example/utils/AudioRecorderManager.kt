package com.example.utils

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
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
import kotlin.math.abs
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

    // Fungsi brutal buat ngebungkam Bip Android dari 2 jalur stream sekaligus
    private fun muteSystemBeep() {
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fungsi buat ngebalikin volume normal
    private fun unmuteSystemBeep() {
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
                        // TAHAN 400ms! Biar Bip-nya bener-bener beres bunyi di latar belakang, baru di-unmute.
                        coroutineScope.launch {
                            delay(400)
                            unmuteSystemBeep()
                        }
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
                        unmuteSystemBeep() // Jaga-jaga error tetep di-unmute
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
                
                // Bungkam sistem SEBELUM rekaman nyala
                muteSystemBeep()
                startListening(intent)
            }
        } catch (e: Exception) {
            _isRecording.value = false
            unmuteSystemBeep()
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
        unmuteSystemBeep() // Pastikan volume balik normal pas user berhenti ngerekam
        speechRecognizer?.stopListening()
        _amplitude.value = 0f
    }
}

