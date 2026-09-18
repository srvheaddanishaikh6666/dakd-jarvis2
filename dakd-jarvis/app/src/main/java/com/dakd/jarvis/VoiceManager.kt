package com.dakd.jarvis

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val permissionManager: PermissionManager
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    var isListening: Boolean = false
        private set

    var isHandsFreeMode: Boolean = true
    private var isPausedForTTS: Boolean = false
    private var isUserExplicitlyStopped: Boolean = false
    private var currentLanguage: String = "hi-IN"
    private var isRestartPending: Boolean = false

    var onListeningStarted: (() -> Unit)? = null
    var onListeningEnded: (() -> Unit)? = null
    var onRmsChanged: ((Float) -> Unit)? = null
    var onSpeechResult: ((String) -> Unit)? = null
    var onSpeechError: ((String) -> Unit)? = null

    init {
        mainHandler.post {
            ensureRecognizer()
        }
    }

    private fun ensureRecognizer() {
        if (speechRecognizer == null && SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }
            } catch (e: Exception) {
                Log.e("VoiceManager", "Error creating speech recognizer: ${e.message}")
            }
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            onListeningStarted?.invoke()
        }

        override fun onBeginningOfSpeech() {
            isListening = true
        }

        override fun onRmsChanged(rmsdB: Float) {
            onRmsChanged?.invoke(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
            onListeningEnded?.invoke()
        }

        override fun onError(error: Int) {
            isListening = false
            onListeningEnded?.invoke()

            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Normal silence / timeout when nobody spoke.
                    // In hands-free mode, restart silently without annoying error messages.
                    if (isHandsFreeMode && !isPausedForTTS && !isUserExplicitlyStopped) {
                        scheduleRestart(250)
                    }
                }
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    // Recreate recognizer if internal state is locked
                    recreateRecognizer()
                    if (isHandsFreeMode && !isPausedForTTS && !isUserExplicitlyStopped) {
                        scheduleRestart(500)
                    }
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    onSpeechError?.invoke("Microphone permission required")
                }
                SpeechRecognizer.ERROR_AUDIO,
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> {
                    if (isHandsFreeMode && !isPausedForTTS && !isUserExplicitlyStopped) {
                        scheduleRestart(1000)
                    } else {
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            else -> "Speech recognition error ($error)"
                        }
                        onSpeechError?.invoke(msg)
                    }
                }
                else -> {
                    if (isHandsFreeMode && !isPausedForTTS && !isUserExplicitlyStopped) {
                        scheduleRestart(500)
                    }
                }
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            onListeningEnded?.invoke()
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull() ?: ""
            if (recognizedText.isNotBlank()) {
                onSpeechResult?.invoke(recognizedText)
            } else {
                if (isHandsFreeMode && !isPausedForTTS && !isUserExplicitlyStopped) {
                    scheduleRestart(300)
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun recreateRecognizer() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                // ignore
            }
            speechRecognizer = null
            ensureRecognizer()
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!isHandsFreeMode || isPausedForTTS || isUserExplicitlyStopped) return
        if (isRestartPending) return
        isRestartPending = true
        mainHandler.postDelayed({
            isRestartPending = false
            if (isHandsFreeMode && !isPausedForTTS && !isUserExplicitlyStopped) {
                startListening(currentLanguage)
            }
        }, delayMs)
    }

    fun startListening(languageHint: String = "hi-IN") {
        currentLanguage = languageHint
        isUserExplicitlyStopped = false

        if (!permissionManager.hasAudioPermission()) {
            onSpeechError?.invoke("Microphone permission required hai Sir.")
            return
        }

        mainHandler.post {
            if (isPausedForTTS) return@post
            ensureRecognizer()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageHint)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageHint)
                putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("hi-IN", "en-IN", "en-US"))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                isListening = false
                onListeningEnded?.invoke()
                if (isHandsFreeMode && !isPausedForTTS && !isUserExplicitlyStopped) {
                    recreateRecognizer()
                    scheduleRestart(600)
                } else {
                    onSpeechError?.invoke("Microphone start nahi ho saka: ${e.message}")
                }
            }
        }
    }

    fun stopListening() {
        isUserExplicitlyStopped = true
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                // ignore
            }
            isListening = false
            onListeningEnded?.invoke()
        }
    }

    fun pauseForTTS() {
        isPausedForTTS = true
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                // ignore
            }
            isListening = false
            onListeningEnded?.invoke()
        }
    }

    fun resumeFromTTS() {
        isPausedForTTS = false
        if (isHandsFreeMode && !isUserExplicitlyStopped) {
            scheduleRestart(350)
        }
    }

    fun toggleHandsFree(): Boolean {
        isHandsFreeMode = !isHandsFreeMode
        if (isHandsFreeMode) {
            isUserExplicitlyStopped = false
            startListening(currentLanguage)
        } else {
            stopListening()
        }
        return isHandsFreeMode
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
