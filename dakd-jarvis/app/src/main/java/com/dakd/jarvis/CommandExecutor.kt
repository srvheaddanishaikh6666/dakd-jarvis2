package com.dakd.jarvis

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import org.json.JSONObject
import java.util.Calendar

data class ExecutionResult(
    val success: Boolean,
    val reply: String,
    val actionExecuted: String,
    val requiresUserConfirmation: Boolean = false,
    val pendingAction: CommandPlan? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("success", success)
        put("reply", reply)
        put("action_executed", actionExecuted)
        put("requires_confirmation", requiresUserConfirmation)
        if (pendingAction != null) {
            put("pending_action", pendingAction.toJson())
        }
    }
}

class CommandExecutor(
    private val context: Context,
    private val appLauncher: AppLauncher,
    private val whatsAppManager: WhatsAppManager,
    private val contactManager: ContactManager,
    private val torchManager: TorchManager,
    private val fileManager: FileManager,
    private val permissionManager: PermissionManager,
    private val settingsManager: SettingsManager,
    private val ttsManager: TTSManager
) {

    fun execute(plan: CommandPlan, userConfirmed: Boolean = false): ExecutionResult {
        val confMode = settingsManager.getConfirmationMode()
        val isSensitive = plan.action in listOf("make_call", "whatsapp_message", "send_sms", "accessibility_action")

        val mustConfirm = when (confMode) {
            ConfirmationMode.SAFE -> plan.requires_confirmation || isSensitive
            ConfirmationMode.BALANCED -> plan.requires_confirmation
            ConfirmationMode.PERSONAL -> plan.requires_confirmation
        }

        if (mustConfirm && !userConfirmed) {
            return ExecutionResult(
                success = true,
                reply = plan.reply,
                actionExecuted = "pending_confirmation",
                requiresUserConfirmation = true,
                pendingAction = plan
            )
        }

        return when (plan.action) {
            "open_whatsapp" -> {
                val (ok, msg) = whatsAppManager.openWhatsApp()
                speakIfNeeded(msg)
                ExecutionResult(ok, msg, "open_whatsapp")
            }

            "whatsapp_message" -> {
                val contact = plan.parameters["contact"] ?: "Contact"
                val message = plan.parameters["message"] ?: "Hello"
                val (ok, msg) = whatsAppManager.prepareAndSendMessage(contact, message)
                speakIfNeeded(msg)
                ExecutionResult(ok, msg, "whatsapp_message")
            }

            "make_call" -> {
                val contact = plan.parameters["contact"] ?: plan.target
                var phone = contact.filter { it.isDigit() || it == '+' }
                if (phone.length < 5) {
                    val found = contactManager.findPhoneNumber(contact)
                    if (found != null) phone = found
                }
                if (phone.isNotBlank()) {
                    val ok = performCall(phone)
                    val msg = if (ok) "$contact ko call lagaya ja raha hai Sir." else "Call lagane mein error aaya."
                    speakIfNeeded(msg)
                    ExecutionResult(ok, msg, "make_call")
                } else {
                    // Open dialer
                    openDialer()
                    val msg = "Sir, $contact ka number nahi mila. Dialer open kar diya hai."
                    speakIfNeeded(msg)
                    ExecutionResult(true, msg, "make_call")
                }
            }

            "send_sms" -> {
                val contact = plan.parameters["contact"] ?: plan.target
                val message = plan.parameters["message"] ?: ""
                var phone = contact.filter { it.isDigit() || it == '+' }
                if (phone.length < 5 && contact.isNotBlank()) {
                    val found = contactManager.findPhoneNumber(contact)
                    if (found != null) phone = found
                }
                val ok = openSms(phone, message)
                val msg = if (ok) "SMS composer ready hai Sir." else "SMS open nahi ho saka."
                speakIfNeeded(msg)
                ExecutionResult(ok, msg, "send_sms")
            }

            "open_camera" -> {
                val ok = openCamera()
                val msg = if (ok) "Camera open kar diya hai Sir." else "Camera app nahi mila."
                speakIfNeeded(msg)
                ExecutionResult(ok, msg, "open_camera")
            }

            "unlock_phone" -> {
                MainActivity.currentInstance?.dismissKeyguardUnlock()
                val svc = JarvisAccessibilityService.instance
                val dismissed = svc?.dismissKeyguardOrWake() ?: false
                val msg = "Sir, phone unlock karne ka command execute kar diya hai."
                speakIfNeeded(msg)
                ExecutionResult(true, msg, "unlock_phone")
            }

            "lock_phone" -> {
                val svc = JarvisAccessibilityService.instance
                val ok = if (svc != null) {
                    svc.lockScreen()
                } else {
                    false
                }
                val msg = if (ok) "Phone lock kar diya hai Sir." else "Phone lock karne ke liye Accessibility Service enable honi chahiye Sir."
                speakIfNeeded(msg)
                ExecutionResult(ok, msg, "lock_phone")
            }

            "toggle_torch" -> {
                val state = plan.parameters["state"] ?: plan.target
                val (ok, msg) = if (state == "on") {
                    torchManager.setTorch(true)
                } else if (state == "off") {
                    torchManager.setTorch(false)
                } else {
                    torchManager.toggleTorch()
                }
                speakIfNeeded(msg)
                ExecutionResult(ok, msg, "toggle_torch")
            }

            "open_app" -> {
                val target = plan.target.lowercase()
                val pkg = plan.parameters["package"] ?: ""
                val ok: Boolean
                val msg: String

                when (target) {
                    "whatsapp" -> {
                        val res = whatsAppManager.openWhatsApp()
                        ok = res.first
                        msg = res.second
                    }
                    "instagram" -> {
                        ok = if (appLauncher.isAppInstalled("com.instagram.android")) {
                            appLauncher.launchPackage("com.instagram.android")
                        } else {
                            openWebUrl("https://www.instagram.com")
                        }
                        msg = if (ok) "Instagram open kar diya hai Sir." else "Instagram open nahi ho saka."
                    }
                    "facebook" -> {
                        ok = if (appLauncher.isAppInstalled("com.facebook.katana")) {
                            appLauncher.launchPackage("com.facebook.katana")
                        } else {
                            openWebUrl("https://www.facebook.com")
                        }
                        msg = if (ok) "Facebook open kar diya hai Sir." else "Facebook open nahi ho saka."
                    }
                    "camera" -> {
                        ok = openCamera()
                        msg = if (ok) "Camera open kar diya hai Sir." else "Camera nahi mila."
                    }
                    "gallery" -> {
                        val res = fileManager.openGallery()
                        ok = res.first
                        msg = res.second
                    }
                    "dialer" -> {
                        ok = openDialer()
                        msg = if (ok) "Phone dialer open kar diya hai Sir." else "Dialer nahi mila."
                    }
                    "contacts" -> {
                        ok = openContacts()
                        msg = if (ok) "Contacts open kar diya hai Sir." else "Contacts nahi mile."
                    }
                    "calculator" -> {
                        ok = openCalculator()
                        msg = if (ok) "Calculator open kar diya hai Sir." else "Calculator nahi mila."
                    }
                    "calendar" -> {
                        ok = openCalendar()
                        msg = if (ok) "Calendar open kar diya hai Sir." else "Calendar nahi mila."
                    }
                    "clock" -> {
                        ok = openClock()
                        msg = if (ok) "Clock open kar diya hai Sir." else "Clock nahi mila."
                    }
                    "maps" -> {
                        ok = openMaps("")
                        msg = if (ok) "Google Maps open kar diya hai Sir." else "Maps nahi mila."
                    }
                    "files" -> {
                        val res = fileManager.openFiles()
                        ok = res.first
                        msg = res.second
                    }
                    "play_store" -> {
                        ok = openPlayStore()
                        msg = if (ok) "Play Store open kar diya hai Sir." else "Play Store nahi mila."
                    }
                    else -> {
                        if (pkg.isNotBlank()) {
                            ok = appLauncher.launchPackage(pkg)
                            msg = if (ok) "$target open kar diya hai Sir." else "$target launch nahi ho saka."
                        } else {
                            val res = appLauncher.launchAppByName(target)
                            ok = res.first
                            msg = if (ok) "${res.second} launch kar diya hai Sir." else res.second
                        }
                    }
                }
                speakIfNeeded(msg)
                ExecutionResult(ok, msg, "open_app")
            }

            "launch_installed_app" -> {
                val appName = plan.parameters["app_name"] ?: plan.target
                val (ok, name) = appLauncher.launchAppByName(appName)
                val msg = if (ok) "$name launch kar diya hai Sir." else name
                speakIfNeeded(msg)
                ExecutionResult(ok, msg, "launch_installed_app")
            }

            "open_settings" -> {
                val target = plan.target.lowercase()
                val ok = openSettingsTarget(target)
                val msg = "Settings open kar diya hai Sir."
                speakIfNeeded(msg)
                ExecutionResult(ok, msg, "open_settings")
            }

            "open_browser" -> {
                val url = plan.parameters["url"] ?: "https://www.google.com"
                val ok = openWebUrl(url)
                val msg = "Browser open kar diya hai Sir."
                speakIfNeeded(msg)
                ExecutionResult(ok, msg, "open_browser")
            }

            "google_search" -> {
                val query = plan.parameters["query"] ?: plan.target
                val ok = openWebUrl("https://www.google.com/search?q=${Uri.encode(query)}")
                val msg = "Google par search kar raha hoon: $query"
                speakIfNeeded(msg)
                ExecutionResult(ok, msg, "google_search")
            }

            "accessibility_action" -> {
                val svc = JarvisAccessibilityService.instance
                if (svc == null) {
                    val msg = "Sir, UI automation ke liye Accessibility permission enable karni hogi."
                    speakIfNeeded(msg)
                    JarvisAccessibilityService.openAccessibilitySettings(context)
                    ExecutionResult(false, msg, "accessibility_action")
                } else {
                    val instruction = plan.parameters["instruction"] ?: plan.target
                    val targetBtn = plan.parameters["button"] ?: plan.parameters["ui_target"] ?: ""
                    var done = false
                    if (targetBtn.isNotBlank()) {
                        done = svc.clickNodeByText(targetBtn)
                    }
                    if (!done && instruction.isNotBlank()) {
                        // Extract target word from instruction
                        val words = instruction.split(" ")
                        val onIdx = words.indexOfFirst { it in listOf("par", "on", "pe") }
                        if (onIdx > 0) {
                            done = svc.clickNodeByText(words[onIdx - 1])
                        }
                    }
                    val msg = if (done) "Action successfully perform ho gaya Sir." else "Screen par target element nahi mila Sir."
                    speakIfNeeded(msg)
                    ExecutionResult(done, msg, "accessibility_action")
                }
            }

            "create_reminder" -> {
                val text = plan.parameters["text"] ?: "Reminder"
                val ok = scheduleReminder(text)
                val msg = if (ok) "Reminder set kar diya hai Sir." else "Reminder schedule nahi ho saka."
                speakIfNeeded(msg)
                ExecutionResult(ok, msg, "create_reminder")
            }

            else -> {
                // speak only / informational
                speakIfNeeded(plan.reply)
                ExecutionResult(true, plan.reply, "speak_only")
            }
        }
    }

    private fun speakIfNeeded(text: String) {
        ttsManager.speak(text)
    }

    private fun performCall(phoneNumber: String): Boolean {
        return try {
            if (permissionManager.hasCallPermission()) {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } else {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }
        } catch (e: Exception) {
            openDialer()
        }
    }

    private fun openDialer(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openSms(phoneNumber: String, body: String): Boolean {
        return try {
            val uri = if (phoneNumber.isNotBlank()) Uri.parse("smsto:$phoneNumber") else Uri.parse("smsto:")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openCamera(): Boolean {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (err: Exception) {
                false
            }
        }
    }

    private fun openContacts(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openCalculator(): Boolean {
        val calcPackages = listOf(
            "com.google.android.calculator",
            "com.android.calculator2",
            "com.sec.android.app.popupcalculator",
            "com.miui.calculator"
        )
        for (pkg in calcPackages) {
            if (appLauncher.isAppInstalled(pkg)) {
                return appLauncher.launchPackage(pkg)
            }
        }
        return appLauncher.launchAppByName("Calculator").first
    }

    private fun openCalendar(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            appLauncher.launchAppByName("Calendar").first
        }
    }

    private fun openClock(): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            appLauncher.launchAppByName("Clock").first
        }
    }

    private fun openMaps(query: String): Boolean {
        return try {
            val uri = if (query.isNotBlank()) {
                Uri.parse("geo:0,0?q=${Uri.encode(query)}")
            } else {
                Uri.parse("geo:0,0?q=")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            openWebUrl("https://maps.google.com")
        }
    }

    private fun openPlayStore(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            openWebUrl("https://play.google.com")
        }
    }

    private fun openWebUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openSettingsTarget(target: String): Boolean {
        val action = when (target) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply {
                if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun scheduleReminder(reminderText: String): Boolean {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("title", "Scheduled Reminder")
                putExtra("message", reminderText)
                putExtra("id", (System.currentTimeMillis() % 100000).toInt())
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (System.currentTimeMillis() % 100000).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Default 1 hour from now or next 8 AM
            val triggerTime = System.currentTimeMillis() + 3600 * 1000
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
