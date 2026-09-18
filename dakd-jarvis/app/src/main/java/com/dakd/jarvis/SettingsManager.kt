package com.dakd.jarvis

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class UserProfile(
    val name: String = "Danish",
    val assistantName: String = "DAKD JARVIS",
    val preferredLanguage: String = "Hindi / Roman Hindi / English",
    val voiceGender: String = "Male",
    val responseStyle: String = "Normal" // Short, Normal, Detailed
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("assistant_name", assistantName)
        put("preferred_language", preferredLanguage)
        put("voice_gender", voiceGender)
        put("response_style", responseStyle)
    }

    companion object {
        fun fromJson(json: JSONObject): UserProfile = UserProfile(
            name = json.optString("name", "Danish"),
            assistantName = json.optString("assistant_name", "DAKD JARVIS"),
            preferredLanguage = json.optString("preferred_language", "Hindi / Roman Hindi / English"),
            voiceGender = json.optString("voice_gender", "Male"),
            responseStyle = json.optString("response_style", "Normal")
        )
    }
}

enum class ConfirmationMode {
    SAFE,       // sensitive actions always ask confirmation
    BALANCED,   // only risky actions ask confirmation
    PERSONAL    // customizable by user
}

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dakd_jarvis_prefs", Context.MODE_PRIVATE)

    fun getProfile(): UserProfile {
        return UserProfile(
            name = prefs.getString("profile_name", "Danish") ?: "Danish",
            assistantName = prefs.getString("assistant_name", "DAKD JARVIS") ?: "DAKD JARVIS",
            preferredLanguage = prefs.getString("pref_lang", "Hindi / Roman Hindi / English") ?: "Hindi",
            voiceGender = prefs.getString("voice_gender", "Male") ?: "Male",
            responseStyle = prefs.getString("resp_style", "Normal") ?: "Normal"
        )
    }

    fun saveProfile(profile: UserProfile) {
        prefs.edit()
            .putString("profile_name", profile.name)
            .putString("assistant_name", profile.assistantName)
            .putString("pref_lang", profile.preferredLanguage)
            .putString("voice_gender", profile.voiceGender)
            .putString("resp_style", profile.responseStyle)
            .apply()
    }

    fun getConfirmationMode(): ConfirmationMode {
        val modeStr = prefs.getString("confirmation_mode", ConfirmationMode.BALANCED.name) ?: ConfirmationMode.BALANCED.name
        return try {
            ConfirmationMode.valueOf(modeStr)
        } catch (e: Exception) {
            ConfirmationMode.BALANCED
        }
    }

    fun setConfirmationMode(mode: ConfirmationMode) {
        prefs.edit().putString("confirmation_mode", mode.name).apply()
    }

    fun getAIConfig(): AIConfig {
        val providerStr = prefs.getString("ai_provider", AIProviderType.GEMINI.name) ?: AIProviderType.GEMINI.name
        val provider = try { AIProviderType.valueOf(providerStr) } catch (e: Exception) { AIProviderType.GEMINI }
        val endpoint = prefs.getString("ai_endpoint", "") ?: ""
        val model = prefs.getString("ai_model", "gemini-3.5-flash") ?: "gemini-3.5-flash"
        val customKey = prefs.getString("ai_custom_key", "") ?: ""
        return AIConfig(
            provider = provider,
            endpoint = endpoint,
            model = model,
            customApiKey = customKey
        )
    }

    fun saveAIConfig(config: AIConfig) {
        prefs.edit()
            .putString("ai_provider", config.provider.name)
            .putString("ai_endpoint", config.endpoint)
            .putString("ai_model", config.model)
            .putString("ai_custom_key", config.customApiKey)
            .apply()
    }

    fun isWakeWordEnabled(): Boolean = prefs.getBoolean("wake_word_enabled", true)
    fun setWakeWordEnabled(enabled: Boolean) = prefs.edit().putBoolean("wake_word_enabled", enabled).apply()

    fun getWakeWordPhrase(): String = prefs.getString("wake_word_phrase", "Ok Jaan") ?: "Ok Jaan"
    fun setWakeWordPhrase(phrase: String) = prefs.edit().putString("wake_word_phrase", phrase).apply()

    fun isForegroundNotificationEnabled(): Boolean = prefs.getBoolean("fg_notif_enabled", true)
    fun setForegroundNotificationEnabled(enabled: Boolean) = prefs.edit().putBoolean("fg_notif_enabled", enabled).apply()

    fun isTTSEnabled(): Boolean = prefs.getBoolean("tts_enabled", true)
    fun setTTSEnabled(enabled: Boolean) = prefs.edit().putBoolean("tts_enabled", enabled).apply()

    fun getTTSSpeed(): Float = prefs.getFloat("tts_speed", 1.0f)
    fun setTTSSpeed(speed: Float) = prefs.edit().putFloat("tts_speed", speed).apply()

    fun getCommandHistory(): List<String> {
        val raw = prefs.getString("command_history", "[]") ?: "[]"
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    fun addCommandHistory(command: String) {
        val list = getCommandHistory().toMutableList()
        list.add(0, command)
        if (list.size > 50) list.removeAt(list.size - 1)
        val arr = JSONArray(list)
        prefs.edit().putString("command_history", arr.toString()).apply()
    }

    fun clearHistory() {
        prefs.edit().remove("command_history").apply()
    }

    fun deleteAllData() {
        prefs.edit().clear().apply()
    }

    fun getAutomations(): String {
        return prefs.getString("automations_json", "[]") ?: "[]"
    }

    fun saveAutomations(jsonStr: String) {
        prefs.edit().putString("automations_json", jsonStr).apply()
    }
}
