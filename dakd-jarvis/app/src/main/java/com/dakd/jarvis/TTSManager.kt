package com.dakd.jarvis

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TTSManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isSpeaking = false
    var onSpeechStateChanged: ((Boolean) -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e("TTSManager", "Failed to init TTS: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            isInitialized = true

            val langCode = settingsManager.getProfile().preferredLanguage.lowercase()
            val targetLocale = if (langCode.contains("hindi")) {
                val hiResult = tts?.setLanguage(Locale("hi", "IN"))
                if (hiResult == TextToSpeech.LANG_MISSING_DATA || hiResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Locale.US
                } else {
                    Locale("hi", "IN")
                }
            } else {
                Locale.US
            }
            tts?.language = targetLocale
            tts?.setSpeechRate(settingsManager.getTTSSpeed())
            tts?.setPitch(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    onSpeechStateChanged?.invoke(true)
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    onSpeechStateChanged?.invoke(false)
                }

                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    onSpeechStateChanged?.invoke(false)
                }
            })
        } else {
            Log.e("TTSManager", "TTS initialization error code: $status")
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!settingsManager.isTTSEnabled() || !isInitialized || tts == null) {
            onDone?.invoke()
            return
        }

        val cleanText = text.replace(Regex("[*#_`~]"), "")
            .replace("●", "")
            .trim()
        if (cleanText.isBlank()) {
            onDone?.invoke()
            return
        }

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "DAKD_JARVIS_${System.currentTimeMillis()}")
        }

        tts?.setSpeechRate(settingsManager.getTTSSpeed())
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, "DAKD_JARVIS_UTT")
    }

    fun stop() {
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
            onSpeechStateChanged?.invoke(false)
        }
    }

    fun isCurrentlySpeaking(): Boolean = isSpeaking

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // ignore
        }
    }
}
