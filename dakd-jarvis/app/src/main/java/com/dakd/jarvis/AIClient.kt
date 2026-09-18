package com.dakd.jarvis

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class CommandPlan(
    val reply: String,
    val action: String, // "open_app", "whatsapp_message", "make_call", "send_sms", "toggle_torch", "open_settings", "open_browser", "google_search", "accessibility_action", "create_reminder", "speak_only", "none"
    val target: String = "",
    val parameters: Map<String, String> = emptyMap(),
    val requires_confirmation: Boolean = false,
    val providerUsed: String = "OFFLINE"
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("reply", reply)
        json.put("action", action)
        json.put("target", target)
        val paramsObj = JSONObject()
        parameters.forEach { (k, v) -> paramsObj.put(k, v) }
        json.put("parameters", paramsObj)
        json.put("requires_confirmation", requires_confirmation)
        json.put("provider_used", providerUsed)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject, fallbackProvider: String = "AI"): CommandPlan {
            val reply = json.optString("reply", "Ji Sir, command process ho rahi hai.")
            val action = json.optString("action", "none")
            val target = json.optString("target", "")
            val requiresConfirm = json.optBoolean("requires_confirmation", false) || json.optBoolean("confirmation_required", false)
            val params = mutableMapOf<String, String>()
            val paramsObj = json.optJSONObject("parameters")
            if (paramsObj != null) {
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    params[k] = paramsObj.optString(k, "")
                }
            } else {
                // Check flat fields
                if (json.has("contact")) params["contact"] = json.optString("contact")
                if (json.has("message")) params["message"] = json.optString("message")
                if (json.has("package")) params["package"] = json.optString("package")
                if (json.has("app")) params["app"] = json.optString("app")
            }
            return CommandPlan(
                reply = reply,
                action = action,
                target = target,
                parameters = params,
                requires_confirmation = requiresConfirm,
                providerUsed = fallbackProvider
            )
        }
    }
}

interface AIProvider {
    suspend fun parseCommand(userPrompt: String, contextInfo: String): CommandPlan
}

/**
 * Robust Offline NLP matcher for Hindi, Roman Hindi, English and Hinglish.
 */
class OfflineRuleProvider : AIProvider {
    override suspend fun parseCommand(userPrompt: String, contextInfo: String): CommandPlan = withContext(Dispatchers.Default) {
        val lower = userPrompt.trim().lowercase()
        val cleaned = lower.replace("jarvis", "")
            .replace("hey jarvis", "")
            .replace("ok jarvis", "")
            .replace("dakd", "")
            .replace("ok jaan", "")
            .replace("jaan", "")
            .trim()

        // 1. Wake word / greetings / Jaan
        if (cleaned.isEmpty() || cleaned in listOf("hi", "hello", "namaste", "suno", "ready ho", "kaise ho", "are you there", "ok")) {
            return@withContext CommandPlan(
                reply = "Ji Sir! Main sun raha hoon, bataiye kya hukum hai?",
                action = "speak_only",
                target = "greeting",
                providerUsed = "OFFLINE"
            )
        }

        // 2. Lock / Unlock phone
        if (containsAny(cleaned, "lock kholo", "unlock", "screen kholo", "phone kholo", "lock hatao", "open lock")) {
            return@withContext CommandPlan(
                reply = "Ji Sir, aapke phone ka lock khol raha hoon.",
                action = "unlock_phone",
                target = "phone",
                providerUsed = "OFFLINE"
            )
        }
        if (containsAny(cleaned, "phone lock karo", "screen lock", "lock screen", "phone band karo", "lock kar do")) {
            return@withContext CommandPlan(
                reply = "Phone lock kar diya hai Sir.",
                action = "lock_phone",
                target = "phone",
                providerUsed = "OFFLINE"
            )
        }

        // 3. Torch / Flashlight
        if (containsAny(cleaned, "torch on", "torch chalu", "torch jalao", "flashlight on", "batti jalao", "light on")) {
            return@withContext CommandPlan(
                reply = "Torch on kar raha hoon Sir.",
                action = "toggle_torch",
                target = "on",
                parameters = mapOf("state" to "on"),
                providerUsed = "OFFLINE"
            )
        }
        if (containsAny(cleaned, "torch off", "torch band", "flashlight off", "batti bujhao", "light off")) {
            return@withContext CommandPlan(
                reply = "Torch off kar di hai Sir.",
                action = "toggle_torch",
                target = "off",
                parameters = mapOf("state" to "off"),
                providerUsed = "OFFLINE"
            )
        }

        // 4. WhatsApp
        if (containsAny(cleaned, "whatsapp", "whatsap", "watsap", "whatapp")) {
            // Check if sending a message: e.g. "whatsap khol kar danish ko msg karo" or "whatsapp par Danish ko message karo ghar kab aa rahe ho"
            var contact = ""
            var message = ""

            val words = cleaned.split(" ")
            val koIndex = words.indexOfFirst { it == "ko" || it == "to" }
            if (koIndex > 0) {
                contact = words[koIndex - 1]
            }

            // Find message content if specified
            val msgKeywords = listOf("msg", "message", "likho", "bhejo", "kaho", "bol")
            val msgIndex = words.indexOfFirst { it in msgKeywords }
            if (msgIndex != -1 && msgIndex + 1 < words.size) {
                val afterWords = words.subList(msgIndex + 1, words.size).filter { it !in listOf("karo", "do", "karna", "hai", "khol", "kar") }
                if (afterWords.isNotEmpty()) {
                    message = afterWords.joinToString(" ")
                }
            }

            if (contact.isNotBlank()) {
                val safeContact = contact.replaceFirstChar { it.uppercase() }
                if (message.isNotBlank()) {
                    return@withContext CommandPlan(
                        reply = "WhatsApp par $safeContact ko '$message' message bhejne ke liye confirmation chahiye.",
                        action = "whatsapp_message",
                        target = "whatsapp",
                        parameters = mapOf("contact" to safeContact, "message" to message),
                        requires_confirmation = true,
                        providerUsed = "OFFLINE"
                    )
                } else {
                    return@withContext CommandPlan(
                        reply = "WhatsApp par $safeContact ko kya message bhejna hai Sir?",
                        action = "whatsapp_message",
                        target = "whatsapp",
                        parameters = mapOf("contact" to safeContact, "message" to ""),
                        requires_confirmation = false,
                        providerUsed = "OFFLINE"
                    )
                }
            }

            return@withContext CommandPlan(
                reply = "Sure Sir, WhatsApp open kar raha hoon.",
                action = "open_app",
                target = "whatsapp",
                parameters = mapOf("package" to "com.whatsapp", "app" to "whatsapp"),
                providerUsed = "OFFLINE"
            )
        }

        // 4. Instagram
        if (containsAny(cleaned, "instagram", "insta")) {
            if (containsAny(cleaned, "search", "dhundo", "explore")) {
                return@withContext CommandPlan(
                    reply = "Instagram search open kar raha hoon Sir.",
                    action = "open_app",
                    target = "instagram_search",
                    parameters = mapOf("package" to "com.instagram.android", "action" to "search"),
                    providerUsed = "OFFLINE"
                )
            }
            if (containsAny(cleaned, "profile", "meri id", "account")) {
                return@withContext CommandPlan(
                    reply = "Instagram profile open kar raha hoon Sir.",
                    action = "open_app",
                    target = "instagram_profile",
                    parameters = mapOf("package" to "com.instagram.android", "action" to "profile"),
                    providerUsed = "OFFLINE"
                )
            }
            return@withContext CommandPlan(
                reply = "Instagram open kar raha hoon Sir.",
                action = "open_app",
                target = "instagram",
                parameters = mapOf("package" to "com.instagram.android", "app" to "instagram"),
                providerUsed = "OFFLINE"
            )
        }

        // 5. Facebook
        if (containsAny(cleaned, "facebook", "fb")) {
            return@withContext CommandPlan(
                reply = "Facebook open kar raha hoon Sir.",
                action = "open_app",
                target = "facebook",
                parameters = mapOf("package" to "com.facebook.katana", "app" to "facebook"),
                providerUsed = "OFFLINE"
            )
        }

        // 6. Camera & Photos
        if (containsAny(cleaned, "camera kholo", "camera open", "photo lene", "camera ready", "camera chalao")) {
            return@withContext CommandPlan(
                reply = "Camera open kar raha hoon Sir.",
                action = "open_camera",
                target = "camera",
                providerUsed = "OFFLINE"
            )
        }
        if (containsAny(cleaned, "gallery", "photos", "tasveere")) {
            return@withContext CommandPlan(
                reply = "Gallery open kar raha hoon Sir.",
                action = "open_app",
                target = "gallery",
                providerUsed = "OFFLINE"
            )
        }

        // 7. Phone Calls & Contacts
        if (containsAny(cleaned, "call karo", "call lagao", "phone karo", "call danish", "make a call")) {
            val words = cleaned.split(" ")
            val koIndex = words.indexOfFirst { it == "ko" }
            var contact = if (koIndex > 0) words[koIndex - 1] else ""
            if (contact.isBlank()) {
                val callIndex = words.indexOfFirst { it == "call" }
                if (callIndex != -1 && callIndex + 1 < words.size) {
                    contact = words[callIndex + 1]
                }
            }
            val targetContact = if (contact.isNotBlank()) contact.replaceFirstChar { it.uppercase() } else "Danish"
            return@withContext CommandPlan(
                reply = "$targetContact ko call karne ki permission hai?",
                action = "make_call",
                target = targetContact,
                parameters = mapOf("contact" to targetContact),
                requires_confirmation = true,
                providerUsed = "OFFLINE"
            )
        }

        if (containsAny(cleaned, "dialer", "dial pad", "phone kholo", "keypad")) {
            return@withContext CommandPlan(
                reply = "Phone dialer open kar raha hoon Sir.",
                action = "open_app",
                target = "dialer",
                providerUsed = "OFFLINE"
            )
        }

        if (containsAny(cleaned, "contacts", "contact list", "number")) {
            return@withContext CommandPlan(
                reply = "Contacts open kar raha hoon Sir.",
                action = "open_app",
                target = "contacts",
                providerUsed = "OFFLINE"
            )
        }

        // 8. SMS
        if (containsAny(cleaned, "sms", "message bhejo", "inbox")) {
            return@withContext CommandPlan(
                reply = "SMS composer open kar raha hoon Sir.",
                action = "send_sms",
                target = "sms",
                requires_confirmation = true,
                providerUsed = "OFFLINE"
            )
        }

        // 9. System Settings (WiFi, Bluetooth, Location, Notifications)
        if (containsAny(cleaned, "wifi", "wi-fi")) {
            return@withContext CommandPlan(
                reply = "WiFi settings open kar raha hoon Sir.",
                action = "open_settings",
                target = "wifi",
                providerUsed = "OFFLINE"
            )
        }
        if (containsAny(cleaned, "bluetooth")) {
            return@withContext CommandPlan(
                reply = "Bluetooth settings open kar raha hoon Sir.",
                action = "open_settings",
                target = "bluetooth",
                providerUsed = "OFFLINE"
            )
        }
        if (containsAny(cleaned, "location", "gps")) {
            return@withContext CommandPlan(
                reply = "Location settings open kar raha hoon Sir.",
                action = "open_settings",
                target = "location",
                providerUsed = "OFFLINE"
            )
        }
        if (containsAny(cleaned, "notification settings", "sound settings")) {
            return@withContext CommandPlan(
                reply = "Notification settings open kar raha hoon Sir.",
                action = "open_settings",
                target = "notifications",
                providerUsed = "OFFLINE"
            )
        }
        if (containsAny(cleaned, "settings kholo", "settings open", "setting kholo")) {
            return@withContext CommandPlan(
                reply = "Settings open kar raha hoon Sir.",
                action = "open_settings",
                target = "general",
                providerUsed = "OFFLINE"
            )
        }

        // 10. Tools: Calculator, Calendar, Clock, Maps, Browser, Files, Play Store, YouTube
        if (containsAny(cleaned, "calculator", "hisab")) {
            return@withContext CommandPlan(
                reply = "Calculator open kar raha hoon Sir.",
                action = "open_app",
                target = "calculator",
                providerUsed = "OFFLINE"
            )
        }
        if (containsAny(cleaned, "calendar", "taarikh")) {
            return@withContext CommandPlan(
                reply = "Calendar open kar raha hoon Sir.",
                action = "open_app",
                target = "calendar",
                providerUsed = "OFFLINE"
            )
        }
        if (containsAny(cleaned, "clock", "alarm", "ghadi")) {
            return@withContext CommandPlan(
                reply = "Clock open kar raha hoon Sir.",
                action = "open_app",
                target = "clock",
                providerUsed = "OFFLINE"
            )
        }
        if (containsAny(cleaned, "maps", "rasta", "navigation")) {
            return@withContext CommandPlan(
                reply = "Google Maps open kar raha hoon Sir.",
                action = "open_app",
                target = "maps",
                providerUsed = "OFFLINE"
            )
        }
        if (containsAny(cleaned, "browser", "chrome", "internet")) {
            return@withContext CommandPlan(
                reply = "Browser open kar raha hoon Sir.",
                action = "open_browser",
                target = "browser",
                providerUsed = "OFFLINE"
            )
        }
        if (containsAny(cleaned, "google search", "search karo", "dhundo")) {
            val query = cleaned.replace("google search karo", "")
                .replace("google search", "")
                .replace("search karo", "")
                .trim()
            return@withContext CommandPlan(
                reply = "Google search kar raha hoon Sir: $query",
                action = "google_search",
                target = "google",
                parameters = mapOf("query" to query),
                providerUsed = "OFFLINE"
            )
        }
        if (containsAny(cleaned, "files", "file manager", "documents", "downloads")) {
            return@withContext CommandPlan(
                reply = "Files open kar raha hoon Sir.",
                action = "open_app",
                target = "files",
                providerUsed = "OFFLINE"
            )
        }
        if (containsAny(cleaned, "play store", "playstore", "apps download")) {
            return@withContext CommandPlan(
                reply = "Play Store open kar raha hoon Sir.",
                action = "open_app",
                target = "play_store",
                providerUsed = "OFFLINE"
            )
        }
        if (containsAny(cleaned, "youtube")) {
            return@withContext CommandPlan(
                reply = "YouTube open kar raha hoon Sir.",
                action = "open_app",
                target = "youtube",
                parameters = mapOf("package" to "com.google.android.youtube", "app" to "youtube"),
                providerUsed = "OFFLINE"
            )
        }

        // 11. Reminders / Automations
        if (containsAny(cleaned, "reminder", "yaad dilao", "alarm set karo")) {
            return@withContext CommandPlan(
                reply = "Reminder create kar raha hoon Sir.",
                action = "create_reminder",
                target = "reminder",
                parameters = mapOf("text" to cleaned),
                providerUsed = "OFFLINE"
            )
        }

        // 12. Accessibility Automation commands: "Instagram kholo aur search button par click karo"
        if (containsAny(cleaned, "click karo", "button dabao", "press", "tap on")) {
            return@withContext CommandPlan(
                reply = "Accessibility service ke zariye screen automation execute kar raha hoon.",
                action = "accessibility_action",
                target = "click",
                parameters = mapOf("instruction" to cleaned),
                requires_confirmation = true,
                providerUsed = "OFFLINE"
            )
        }

        // 13. Generic "kholo" / "open" for any named app
        if (containsAny(cleaned, "kholo", "open", "chalao", "start")) {
            val appName = cleaned.replace("kholo", "")
                .replace("khol do", "")
                .replace("open karo", "")
                .replace("open", "")
                .replace("chalao", "")
                .replace("chala do", "")
                .replace("start", "")
                .trim()
            if (appName.isNotBlank()) {
                return@withContext CommandPlan(
                    reply = "$appName launch kar raha hoon Sir.",
                    action = "launch_installed_app",
                    target = appName,
                    parameters = mapOf("app_name" to appName),
                    providerUsed = "OFFLINE"
                )
            }
        }

        // Default conversational answer
        return@withContext CommandPlan(
            reply = "Sir, maine aapki command receive ki hai: '$cleaned'. Ise perform karne ke liye details specify kijiye.",
            action = "speak_only",
            target = "chat",
            parameters = mapOf("query" to cleaned),
            providerUsed = "OFFLINE"
        )
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it, ignoreCase = true) }
    }
}

/**
 * Gemini Provider using REST API
 */
class GeminiProvider(private val config: AIConfig) : AIProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun parseCommand(userPrompt: String, contextInfo: String): CommandPlan = withContext(Dispatchers.IO) {
        val apiKey = config.getEffectiveApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("No valid Gemini API key configured.")
        }

        val endpoint = if (config.endpoint.isNotBlank()) {
            config.endpoint
        } else {
            "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=$apiKey"
        }

        val systemInstruction = """
            You are DAKD JARVIS, an advanced personal AI Android assistant.
            You must understand natural Hindi, Roman Hindi, English, and Hinglish.
            You must ALWAYS return valid JSON matching this exact structure:
            {
              "reply": "Conversational reply in Hindi/Hinglish/English respecting user tone, e.g. Sure Sir, WhatsApp open kar raha hoon.",
              "action": "open_app | open_whatsapp | whatsapp_message | make_call | send_sms | open_camera | toggle_torch | open_settings | open_browser | google_search | launch_installed_app | accessibility_action | unlock_phone | lock_phone | create_reminder | speak_only | none",
              "target": "Specific target like whatsapp, instagram, camera, torch, dialer, phone, or app name",
              "parameters": {
                 "contact": "Contact name if applicable",
                 "message": "Message text if applicable",
                 "package": "Package name if known",
                 "app_name": "App name if launching",
                 "query": "Search query if applicable",
                 "state": "on or off if torch",
                 "time": "Time string if reminder"
              },
              "requires_confirmation": false
            }
            Requires confirmation must be true for: sending messages, making calls, financial actions, deleting data, public posts.
            Context: $contextInfo
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            val contentsArray = JSONArray()
            val userContent = JSONObject().apply {
                val partsArray = JSONArray()
                partsArray.put(JSONObject().apply {
                    put("text", userPrompt)
                })
                put("parts", partsArray)
            }
            contentsArray.put(userContent)
            put("contents", contentsArray)

            put("systemInstruction", JSONObject().apply {
                val parts = JSONArray()
                parts.put(JSONObject().apply { put("text", systemInstruction) })
                put("parts", parts)
            })

            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: "HTTP ${response.code}"
            val friendlyMsg = try {
                val errJson = JSONObject(errBody).optJSONObject("error")
                val msg = errJson?.optString("message") ?: ""
                val status = errJson?.optString("status") ?: ""
                if (response.code == 403 || status == "PERMISSION_DENIED" || msg.contains("denied access", ignoreCase = true)) {
                    "Google Cloud project me Generative Language API access restricted (403 Permission Denied) hai. Kripya aistudio.google.com/app/apikey par 'Create API key in new project' se fresh key generate karein."
                } else if (response.code == 400 && msg.contains("API key not valid", ignoreCase = true)) {
                    "Gemini API key invalid hai. Kripya correct key dalein."
                } else if (response.code == 404) {
                    "Gemini model ${config.model} is API key par available nahi hai."
                } else {
                    msg.ifBlank { "HTTP ${response.code}" }
                }
            } catch (ex: Exception) {
                if (errBody.contains("denied access", ignoreCase = true)) {
                    "Google Cloud project me Generative Language API access restricted (403) hai."
                } else {
                    "HTTP ${response.code} error"
                }
            }
            throw RuntimeException(friendlyMsg)
        }

        val respStr = response.body?.string() ?: throw RuntimeException("Empty response from Gemini")
        val respJson = JSONObject(respStr)
        val text = respJson.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text", "") ?: ""

        val parsedJson = JSONObject(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        CommandPlan.fromJson(parsedJson, fallbackProvider = "GEMINI")
    }
}

/**
 * OpenAI-compatible Provider
 */
class OpenAIProvider(private val config: AIConfig) : AIProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun parseCommand(userPrompt: String, contextInfo: String): CommandPlan = withContext(Dispatchers.IO) {
        val apiKey = config.getEffectiveApiKey()
        val endpoint = if (config.endpoint.isNotBlank()) config.endpoint else "https://api.openai.com/v1/chat/completions"

        val jsonBody = JSONObject().apply {
            put("model", if (config.model.isNotBlank()) config.model else "gpt-4o-mini")
            put("response_format", JSONObject().apply { put("type", "json_object") })
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are DAKD JARVIS, personal Android assistant. Return JSON with keys: reply, action, target, parameters (object), requires_confirmation (bool). Context: $contextInfo")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }
            put("messages", messages)
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: throw RuntimeException("Empty response")
        if (!response.isSuccessful) throw RuntimeException("OpenAI error: $bodyStr")

        val json = JSONObject(bodyStr)
        val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        CommandPlan.fromJson(JSONObject(content), fallbackProvider = "OPENAI")
    }
}

/**
 * Custom / Claude Provider
 */
class CustomProvider(private val config: AIConfig) : AIProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun parseCommand(userPrompt: String, contextInfo: String): CommandPlan = withContext(Dispatchers.IO) {
        val endpoint = config.endpoint
        if (endpoint.isBlank()) throw IllegalStateException("Custom endpoint is not configured")

        val payload = JSONObject().apply {
            put("prompt", userPrompt)
            put("context", contextInfo)
            put("model", config.model)
        }

        val builder = Request.Builder().url(endpoint)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))

        val apiKey = config.getEffectiveApiKey()
        if (apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $apiKey")
        }

        val response = client.newCall(builder.build()).execute()
        val body = response.body?.string() ?: throw RuntimeException("Empty response")
        if (!response.isSuccessful) throw RuntimeException("Custom API error: $body")

        CommandPlan.fromJson(JSONObject(body), fallbackProvider = "CUSTOM")
    }
}

/**
 * Central AI Client that dispatches to configured provider and handles fallback
 */
class AIClient(private val settingsManager: SettingsManager) {
    private val offlineProvider = OfflineRuleProvider()

    suspend fun processCommand(userPrompt: String, contextInfo: String = ""): CommandPlan {
        val config = settingsManager.getAIConfig()

        if (config.provider == AIProviderType.OFFLINE) {
            return offlineProvider.parseCommand(userPrompt, contextInfo)
        }

        val activeProvider: AIProvider = when (config.provider) {
            AIProviderType.GEMINI -> GeminiProvider(config)
            AIProviderType.OPENAI -> OpenAIProvider(config)
            AIProviderType.CUSTOM, AIProviderType.CLAUDE -> CustomProvider(config)
            AIProviderType.OFFLINE -> offlineProvider
        }

        return try {
            activeProvider.parseCommand(userPrompt, contextInfo)
        } catch (e: Throwable) {
            Log.e("AIClient", "Provider ${config.provider} failed: ${e.message}. Falling back to offline provider.", e)
            val fallbackPlan = offlineProvider.parseCommand(userPrompt, contextInfo)
            val cleanErr = e.localizedMessage ?: "AI connection issue"
            val prefix = if (fallbackPlan.action != "none" && fallbackPlan.action != "speak_only") {
                "(Offline Engine) "
            } else {
                "Sir, online AI server se connect nahi ho saka ($cleanErr).\n\n"
            }
            fallbackPlan.copy(
                reply = prefix + fallbackPlan.reply,
                providerUsed = "OFFLINE_FALLBACK"
            )
        }
    }
}
