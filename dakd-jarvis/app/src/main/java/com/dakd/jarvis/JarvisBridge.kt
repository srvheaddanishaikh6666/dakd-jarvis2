package com.dakd.jarvis

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class JarvisBridge(
    private val activity: Activity,
    private val webView: WebView,
    private val aiClient: AIClient,
    private val commandExecutor: CommandExecutor,
    private val voiceManager: VoiceManager,
    private val ttsManager: TTSManager,
    private val torchManager: TorchManager,
    private val appLauncher: AppLauncher,
    private val settingsManager: SettingsManager,
    private val permissionManager: PermissionManager
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bridgeScope = CoroutineScope(Dispatchers.Main)

    init {
        setupVoiceCallbacks()
        setupTTSCallbacks()
    }

    private fun setupVoiceCallbacks() {
        voiceManager.onListeningStarted = {
            sendEvent("speech_start", JSONObject().apply { put("listening", true) })
        }
        voiceManager.onListeningEnded = {
            sendEvent("speech_end", JSONObject().apply { put("listening", false) })
        }
        voiceManager.onRmsChanged = { rms ->
            sendEvent("speech_rms", JSONObject().apply { put("rms", rms) })
        }
        voiceManager.onSpeechResult = { text ->
            sendEvent("speech_result", JSONObject().apply { put("text", text) })
            // Automatically process the recognized text!
            processCommandInternal(text)
        }
        voiceManager.onSpeechError = { error ->
            sendEvent("speech_error", JSONObject().apply { put("error", error) })
        }
    }

    private fun setupTTSCallbacks() {
        ttsManager.onSpeechStateChanged = { isSpeaking ->
            sendEvent("tts_state", JSONObject().apply { put("speaking", isSpeaking) })
            if (isSpeaking) {
                voiceManager.pauseForTTS()
            } else {
                voiceManager.resumeFromTTS()
            }
        }
    }

    fun sendEvent(event: String, payload: JSONObject) {
        mainHandler.post {
            val jsonStr = payload.toString()
            val js = "window.onJarvisEvent && window.onJarvisEvent('$event', $jsonStr);"
            webView.evaluateJavascript(js, null)
        }
    }

    @JavascriptInterface
    fun getInitialState(): String {
        val root = JSONObject()
        root.put("profile", settingsManager.getProfile().toJson())
        root.put("confirmation_mode", settingsManager.getConfirmationMode().name)

        val aiCfg = settingsManager.getAIConfig()
        val aiObj = JSONObject().apply {
            put("provider", aiCfg.provider.name)
            put("endpoint", aiCfg.endpoint)
            put("model", aiCfg.model)
            put("custom_api_key", aiCfg.customApiKey)
            put("effective_key", aiCfg.getEffectiveApiKey())
            put("has_custom_key", aiCfg.customApiKey.isNotBlank())
        }
        root.put("ai_config", aiObj)
        root.put("hands_free_enabled", voiceManager.isHandsFreeMode)

        root.put("wake_word_enabled", settingsManager.isWakeWordEnabled())
        root.put("wake_word_phrase", settingsManager.getWakeWordPhrase())
        root.put("fg_notif_enabled", settingsManager.isForegroundNotificationEnabled())
        root.put("tts_enabled", settingsManager.isTTSEnabled())
        root.put("tts_speed", settingsManager.getTTSSpeed())
        root.put("torch_on", torchManager.isStateOn())
        root.put("torch_supported", torchManager.isSupported())
        root.put("accessibility_enabled", JarvisAccessibilityService.isServiceRunning())
        root.put("permissions", permissionManager.getPermissionsStatus())
        root.put("history", JSONArray(settingsManager.getCommandHistory()))
        root.put("automations", JSONArray(settingsManager.getAutomations()))

        return root.toString()
    }

    @JavascriptInterface
    fun startVoiceRecognition(lang: String) {
        val language = if (lang.isNotBlank()) lang else "hi-IN"
        voiceManager.startListening(language)
    }

    @JavascriptInterface
    fun stopVoiceRecognition() {
        voiceManager.stopListening()
    }

    @JavascriptInterface
    fun processCommand(userQuery: String) {
        processCommandInternal(userQuery)
    }

    private fun processCommandInternal(userQuery: String) {
        if (userQuery.isBlank()) return
        settingsManager.addCommandHistory(userQuery)

        bridgeScope.launch {
            try {
                sendEvent("command_processing", JSONObject().apply { put("query", userQuery) })

                // Generate context info
                val profile = settingsManager.getProfile()
                val contextInfo = "User Name: ${profile.name}, Language: ${profile.preferredLanguage}, Torch: ${torchManager.isStateOn()}"

                // 1. AI planning
                val plan = aiClient.processCommand(userQuery, contextInfo)

                // 2. Execution
                val result = commandExecutor.execute(plan, userConfirmed = false)

                // 3. Dispatch result to web frontend
                sendEvent("command_result", result.toJson())
                if (!ttsManager.isCurrentlySpeaking()) {
                    voiceManager.resumeFromTTS()
                }
            } catch (e: Exception) {
                val errObj = JSONObject().apply {
                    put("success", false)
                    put("reply", "Sir, command execute karne mein error aaya: ${e.message}")
                    put("action_executed", "error")
                    put("requires_confirmation", false)
                }
                sendEvent("command_result", errObj)
                ttsManager.speak("Sir, execution mein error aaya.")
                if (!ttsManager.isCurrentlySpeaking()) {
                    voiceManager.resumeFromTTS()
                }
            }
        }
    }

    @JavascriptInterface
    fun executeConfirmedAction(actionJsonStr: String) {
        bridgeScope.launch {
            try {
                val json = JSONObject(actionJsonStr)
                val plan = CommandPlan.fromJson(json)
                val result = commandExecutor.execute(plan, userConfirmed = true)
                sendEvent("command_result", result.toJson())
            } catch (e: Exception) {
                val errObj = JSONObject().apply {
                    put("success", false)
                    put("reply", "Confirmation execution error: ${e.message}")
                    put("action_executed", "error")
                    put("requires_confirmation", false)
                }
                sendEvent("command_result", errObj)
            }
        }
    }

    @JavascriptInterface
    fun speakText(text: String) {
        ttsManager.speak(text)
    }

    @JavascriptInterface
    fun stopSpeaking() {
        ttsManager.stop()
    }

    @JavascriptInterface
    fun toggleTorch(stateStr: String): String {
        val (ok, msg) = if (stateStr == "on") {
            torchManager.setTorch(true)
        } else if (stateStr == "off") {
            torchManager.setTorch(false)
        } else {
            torchManager.toggleTorch()
        }
        sendEvent("torch_state", JSONObject().apply { put("on", torchManager.isStateOn()) })
        return JSONObject().apply {
            put("success", ok)
            put("message", msg)
            put("is_on", torchManager.isStateOn())
        }.toString()
    }

    @JavascriptInterface
    fun getTorchState(): Boolean = torchManager.isStateOn()

    @JavascriptInterface
    fun launchApp(pkgOrName: String): String {
        val (ok, msg) = if (appLauncher.isAppInstalled(pkgOrName)) {
            val launched = appLauncher.launchPackage(pkgOrName)
            Pair(launched, if (launched) "Launched $pkgOrName" else "Failed to launch")
        } else {
            appLauncher.launchAppByName(pkgOrName)
        }
        return JSONObject().apply {
            put("success", ok)
            put("message", msg)
        }.toString()
    }

    @JavascriptInterface
    fun getInstalledApps(): String = appLauncher.getInstalledAppsJson()

    @JavascriptInterface
    fun saveSettings(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            if (json.has("confirmation_mode")) {
                val mode = ConfirmationMode.valueOf(json.getString("confirmation_mode"))
                settingsManager.setConfirmationMode(mode)
            }
            if (json.has("ai_config")) {
                val aiObj = json.getJSONObject("ai_config")
                val provider = AIProviderType.valueOf(aiObj.optString("provider", "GEMINI"))
                val existingCfg = settingsManager.getAIConfig()
                val newKey = aiObj.optString("custom_api_key", "").trim()
                val finalKey = if (newKey.isBlank() && !aiObj.optBoolean("clear_custom_key", false)) {
                    existingCfg.customApiKey
                } else {
                    newKey
                }
                val cfg = AIConfig(
                    provider = provider,
                    endpoint = aiObj.optString("endpoint", ""),
                    model = aiObj.optString("model", "gemini-3.5-flash"),
                    customApiKey = finalKey
                )
                settingsManager.saveAIConfig(cfg)
            }
            if (json.has("hands_free_enabled")) {
                val hf = json.getBoolean("hands_free_enabled")
                voiceManager.isHandsFreeMode = hf
                if (hf) voiceManager.startListening() else voiceManager.stopListening()
            }
            if (json.has("wake_word_enabled")) {
                val enabled = json.getBoolean("wake_word_enabled")
                settingsManager.setWakeWordEnabled(enabled)
                if (enabled) JarvisForegroundService.start(activity) else JarvisForegroundService.stop(activity)
            }
            if (json.has("wake_word_phrase")) {
                settingsManager.setWakeWordPhrase(json.getString("wake_word_phrase"))
            }
            if (json.has("fg_notif_enabled")) {
                val fg = json.getBoolean("fg_notif_enabled")
                settingsManager.setForegroundNotificationEnabled(fg)
                if (fg) JarvisForegroundService.start(activity) else JarvisForegroundService.stop(activity)
            }
            if (json.has("tts_enabled")) {
                settingsManager.setTTSEnabled(json.getBoolean("tts_enabled"))
            }
            if (json.has("tts_speed")) {
                settingsManager.setTTSSpeed(json.getDouble("tts_speed").toFloat())
            }
            JSONObject().apply { put("success", true) }.toString()
        } catch (e: Exception) {
            JSONObject().apply { put("success", false); put("error", e.message) }.toString()
        }
    }

    @JavascriptInterface
    fun toggleHandsFree(): Boolean {
        val newState = voiceManager.toggleHandsFree()
        sendEvent("hands_free_update", JSONObject().apply { put("enabled", newState) })
        return newState
    }

    @JavascriptInterface
    fun setHandsFree(enabled: Boolean) {
        voiceManager.isHandsFreeMode = enabled
        if (enabled) voiceManager.startListening() else voiceManager.stopListening()
        sendEvent("hands_free_update", JSONObject().apply { put("enabled", enabled) })
    }

    @JavascriptInterface
    fun testAIConnection(customKey: String, model: String): String {
        return try {
            val targetKey = if (customKey.isNotBlank()) customKey.trim() else settingsManager.getAIConfig().getEffectiveApiKey()
            if (targetKey.isBlank() || targetKey == "MY_GEMINI_API_KEY") {
                return JSONObject().apply {
                    put("success", false)
                    put("message", "API Key blank ya placeholder hai. Kripya valid key enter karein.")
                }.toString()
            }
            val targetModel = if (model.isNotBlank()) model.trim() else "gemini-3.5-flash"
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$targetKey"
            val pingJson = JSONObject().apply {
                val parts = JSONArray().put(JSONObject().apply { put("text", "Ping") })
                val contents = JSONArray().put(JSONObject().apply { put("parts", parts) })
                put("contents", contents)
            }
            val body = pingJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(endpoint)
                .addHeader("x-goog-api-key", targetKey)
                .post(body)
                .build()
            val resp = client.newCall(req).execute()
            val respCode = resp.code
            val respBody = resp.body?.string() ?: ""

            if (resp.isSuccessful) {
                JSONObject().apply {
                    put("success", true)
                    put("message", "✅ AI Server se connect ho gaya! (HTTP 200 OK - Gemini Live Active)")
                }.toString()
            } else {
                val errorMsg = if (respCode == 403 || respBody.contains("denied access", ignoreCase = true)) {
                    "❌ 403 Permission Denied: Is project me Generative Language API denied access hai. https://aistudio.google.com/app/apikey par new project me fresh API key create karein."
                } else if (respCode == 400 && respBody.contains("API key not valid", ignoreCase = true)) {
                    "❌ 400 Invalid Key: Gemini API Key galat hai."
                } else if (respCode == 404) {
                    "❌ 404: Model $targetModel is key par available nahi hai."
                } else {
                    "❌ Server error HTTP $respCode: ${respBody.take(120)}"
                }
                JSONObject().apply {
                    put("success", false)
                    put("message", errorMsg)
                }.toString()
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("message", "❌ Connection error: ${e.localizedMessage ?: e.message}")
            }.toString()
        }
    }

    @JavascriptInterface
    fun saveProfile(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            val profile = UserProfile.fromJson(json)
            settingsManager.saveProfile(profile)
            JSONObject().apply { put("success", true) }.toString()
        } catch (e: Exception) {
            JSONObject().apply { put("success", false); put("error", e.message) }.toString()
        }
    }

    @JavascriptInterface
    fun clearHistory(): String {
        settingsManager.clearHistory()
        return JSONObject().apply { put("success", true) }.toString()
    }

    @JavascriptInterface
    fun deleteAllData(): String {
        settingsManager.deleteAllData()
        return JSONObject().apply { put("success", true) }.toString()
    }

    @JavascriptInterface
    fun requestPermission(permType: String) {
        activity.runOnUiThread {
            when (permType) {
                "microphone" -> activity.requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 201)
                "call" -> activity.requestPermissions(arrayOf(android.Manifest.permission.CALL_PHONE), 202)
                "contacts" -> activity.requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), 203)
                "camera" -> activity.requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 204)
                "notifications" -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        activity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 205)
                    }
                }
            }
        }
    }

    @JavascriptInterface
    fun openAccessibilitySettings() {
        JarvisAccessibilityService.openAccessibilitySettings(activity)
    }

    @JavascriptInterface
    fun openAppSettings() {
        permissionManager.openAppSettings()
    }

    @JavascriptInterface
    fun setAutomation(timeStr: String, commandStr: String): String {
        return try {
            val current = JSONArray(settingsManager.getAutomations())
            val item = JSONObject().apply {
                put("id", System.currentTimeMillis().toString())
                put("time", timeStr)
                put("command", commandStr)
            }
            current.put(item)
            settingsManager.saveAutomations(current.toString())
            JSONObject().apply { put("success", true) }.toString()
        } catch (e: Exception) {
            JSONObject().apply { put("success", false); put("error", e.message) }.toString()
        }
    }

    @JavascriptInterface
    fun deleteAutomation(id: String): String {
        return try {
            val current = JSONArray(settingsManager.getAutomations())
            val updated = JSONArray()
            for (i in 0 until current.length()) {
                val item = current.getJSONObject(i)
                if (item.optString("id") != id) {
                    updated.put(item)
                }
            }
            settingsManager.saveAutomations(updated.toString())
            JSONObject().apply { put("success", true) }.toString()
        } catch (e: Exception) {
            JSONObject().apply { put("success", false); put("error", e.message) }.toString()
        }
    }
}
