package com.dakd.jarvis

import android.content.Context
import com.example.BuildConfig

/**
 * AI Provider types supported by DAKD JARVIS
 */
enum class AIProviderType {
    GEMINI,
    OPENAI,
    CLAUDE,
    CUSTOM,
    OFFLINE
}

/**
 * AI Configuration holder
 */
data class AIConfig(
    val provider: AIProviderType = AIProviderType.GEMINI,
    val endpoint: String = "",
    val model: String = "gemini-3.5-flash",
    val customApiKey: String = "",
    val fallbackProvider: AIProviderType = AIProviderType.OFFLINE
) {
    fun getEffectiveApiKey(): String {
        if (customApiKey.isNotBlank()) return customApiKey.trim()
        return when (provider) {
            AIProviderType.GEMINI -> {
                // Read from BuildConfig injected via .env / secrets
                try {
                    val key = BuildConfig.GEMINI_API_KEY
                    if (key.isNotBlank() && key != "MY_GEMINI_API_KEY") {
                        key.trim()
                    } else {
                        "AIzaSyAmUBpcBPZVdbAIqdcqSUpYA2yZ7cQkoyc"
                    }
                } catch (e: Throwable) {
                    "AIzaSyAmUBpcBPZVdbAIqdcqSUpYA2yZ7cQkoyc"
                }
            }
            else -> ""
        }
    }
}
