package com.dakd.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri

class WhatsAppManager(
    private val context: Context,
    private val appLauncher: AppLauncher,
    private val contactManager: ContactManager
) {

    fun isWhatsAppInstalled(): Boolean {
        return appLauncher.isAppInstalled("com.whatsapp") || appLauncher.isAppInstalled("com.whatsapp.w4b")
    }

    fun openWhatsApp(): Pair<Boolean, String> {
        if (appLauncher.isAppInstalled("com.whatsapp")) {
            val ok = appLauncher.launchPackage("com.whatsapp")
            return if (ok) Pair(true, "WhatsApp open kar diya hai Sir.") else Pair(false, "WhatsApp open nahi ho saka.")
        }
        if (appLauncher.isAppInstalled("com.whatsapp.w4b")) {
            val ok = appLauncher.launchPackage("com.whatsapp.w4b")
            return if (ok) Pair(true, "WhatsApp Business open kar diya hai Sir.") else Pair(false, "WhatsApp Business open nahi ho saka.")
        }
        return Pair(false, "Sir, WhatsApp is phone mein installed nahi hai.")
    }

    fun prepareAndSendMessage(contactQuery: String, messageText: String): Pair<Boolean, String> {
        if (!isWhatsAppInstalled()) {
            return Pair(false, "Sir, WhatsApp is phone mein installed nahi hai.")
        }

        // Check if contactQuery is already a phone number
        val digitsOnly = contactQuery.filter { it.isDigit() || it == '+' }
        var targetNumber = if (digitsOnly.length >= 10) digitsOnly else ""

        // If not a raw number, search in Contacts
        if (targetNumber.isEmpty() && contactQuery.isNotBlank()) {
            val foundNumber = contactManager.findPhoneNumber(contactQuery)
            if (foundNumber != null) {
                targetNumber = foundNumber
            }
        }

        return try {
            if (targetNumber.isNotEmpty()) {
                // Direct WhatsApp URL to specific number
                val cleanPhone = targetNumber.replace("+", "").replace(" ", "").replace("-", "")
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(messageText)}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage("com.whatsapp")
                }
                context.startActivity(intent)
                Pair(true, "WhatsApp message $contactQuery ke liye composer mein ready kar diya hai.")
            } else {
                // Share text intent directed to WhatsApp
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, messageText)
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Pair(true, "WhatsApp composer open ho gaya hai. Contact select karke send kijiye.")
            }
        } catch (e: Exception) {
            // Fallback generic send
            try {
                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, messageText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(fallbackIntent, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Pair(true, "Message ready kar diya hai Sir.")
            } catch (err: Exception) {
                Pair(false, "Message send karne mein error: ${err.localizedMessage}")
            }
        }
    }
}
