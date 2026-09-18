package com.dakd.jarvis

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import com.example.R

class MainActivity : ComponentActivity() {

    companion object {
        var currentInstance: MainActivity? = null
            private set
    }

    private lateinit var webView: WebView
    private lateinit var settingsManager: SettingsManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var appLauncher: AppLauncher
    private lateinit var contactManager: ContactManager
    private lateinit var whatsAppManager: WhatsAppManager
    private lateinit var torchManager: TorchManager
    private lateinit var fileManager: FileManager
    private lateinit var ttsManager: TTSManager
    private lateinit var voiceManager: VoiceManager
    private lateinit var aiClient: AIClient
    private lateinit var commandExecutor: CommandExecutor
    private lateinit var bridge: JarvisBridge

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this

        // Enable show over lockscreen and turn screen on
        configureLockScreenFlags()

        // Initialize managers
        settingsManager = SettingsManager(this)
        permissionManager = PermissionManager(this)
        appLauncher = AppLauncher(this)
        contactManager = ContactManager(this)
        whatsAppManager = WhatsAppManager(this, appLauncher, contactManager)
        torchManager = TorchManager(this)
        fileManager = FileManager(this)
        ttsManager = TTSManager(this, settingsManager)
        voiceManager = VoiceManager(this, permissionManager)
        aiClient = AIClient(settingsManager)
        commandExecutor = CommandExecutor(
            this,
            appLauncher,
            whatsAppManager,
            contactManager,
            torchManager,
            fileManager,
            permissionManager,
            settingsManager,
            ttsManager
        )

        // Setup WebView
        webView = WebView(this)
        setContentView(webView)

        setupWebView()

        // Handle Back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("window.onAndroidBack ? window.onAndroidBack() : false") { value ->
                    if (value != "true") {
                        if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            finish()
                        }
                    }
                }
            }
        })

        // Start optional foreground service
        if (settingsManager.isForegroundNotificationEnabled()) {
            try {
                JarvisForegroundService.start(this)
            } catch (e: Exception) {
                // ignore
            }
        }

        // Proactively check audio permission
        if (!permissionManager.hasAudioPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                101
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        bridge = JarvisBridge(
            this,
            webView,
            aiClient,
            commandExecutor,
            voiceManager,
            ttsManager,
            torchManager,
            appLauncher,
            settingsManager,
            permissionManager
        )

        webView.addJavascriptInterface(bridge, "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("DAKD_JARVIS_WEB", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Notify frontend that bridge is connected
                bridge.sendEvent("bridge_ready", org.json.JSONObject().apply {
                    put("status", "connected")
                })
                if (::voiceManager.isInitialized && permissionManager.hasAudioPermission() && voiceManager.isHandsFreeMode) {
                    voiceManager.startListening()
                }
            }
        }

        // Load asset index.html
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permStatus = permissionManager.getPermissionsStatus()
        bridge.sendEvent("permissions_update", permStatus)
        if (requestCode == 101 && permissionManager.hasAudioPermission()) {
            if (::voiceManager.isInitialized && voiceManager.isHandsFreeMode) {
                voiceManager.startListening()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::bridge.isInitialized) {
            bridge.sendEvent("accessibility_update", org.json.JSONObject().apply {
                put("enabled", JarvisAccessibilityService.isServiceRunning())
            })
            bridge.sendEvent("permissions_update", permissionManager.getPermissionsStatus())
        }
        if (::voiceManager.isInitialized && permissionManager.hasAudioPermission() && voiceManager.isHandsFreeMode) {
            voiceManager.startListening()
        }
    }

    private fun configureLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    fun dismissKeyguardUnlock(callback: ((Boolean) -> Unit)? = null) {
        runOnUiThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                if (keyguardManager?.isKeyguardLocked == true) {
                    keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() {
                            callback?.invoke(true)
                        }
                        override fun onDismissError() {
                            callback?.invoke(false)
                        }
                        override fun onDismissCancelled() {
                            callback?.invoke(false)
                        }
                    })
                } else {
                    callback?.invoke(true)
                }
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
                callback?.invoke(true)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureLockScreenFlags()
        val action = intent.getStringExtra("action")
        if (action == "voice_listen" && ::voiceManager.isInitialized) {
            voiceManager.startListening()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentInstance == this) {
            currentInstance = null
        }
        ttsManager.shutdown()
        voiceManager.destroy()
    }
}
