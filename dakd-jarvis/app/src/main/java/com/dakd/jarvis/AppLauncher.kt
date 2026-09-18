package com.dakd.jarvis

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class InstalledApp(
    val name: String,
    val packageName: String
)

class AppLauncher(private val context: Context) {

    private val commonAliases = mapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "instagram" to listOf("com.instagram.android"),
        "facebook" to listOf("com.facebook.katana", "com.facebook.lite"),
        "chrome" to listOf("com.android.chrome"),
        "youtube" to listOf("com.google.android.youtube"),
        "maps" to listOf("com.google.android.apps.maps"),
        "gmail" to listOf("com.google.android.gm"),
        "play store" to listOf("com.android.vending"),
        "playstore" to listOf("com.android.vending"),
        "camera" to listOf("com.android.camera", "com.google.android.GoogleCamera")
    )

    fun getInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val apps = mutableListOf<InstalledApp>()
        try {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            for (info in resolveInfos) {
                val label = info.loadLabel(pm).toString()
                val pkg = info.activityInfo.packageName
                if (pkg != context.packageName) {
                    apps.add(InstalledApp(name = label, packageName = pkg))
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return apps.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
    }

    fun getInstalledAppsJson(): String {
        val arr = JSONArray()
        for (app in getInstalledApps()) {
            val obj = JSONObject()
            obj.put("name", app.name)
            obj.put("package", app.packageName)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun launchPackage(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun launchAppByName(name: String): Pair<Boolean, String> {
        val lower = name.trim().lowercase()

        // 1. Check known aliases
        for ((alias, pkgs) in commonAliases) {
            if (lower.contains(alias)) {
                for (pkg in pkgs) {
                    if (isAppInstalled(pkg)) {
                        val success = launchPackage(pkg)
                        if (success) return Pair(true, alias)
                    }
                }
                return Pair(false, "$alias is phone mein installed nahi hai.")
            }
        }

        // 2. Dynamic search among installed apps
        val apps = getInstalledApps()
        val match = apps.firstOrNull { it.name.lowercase() == lower }
            ?: apps.firstOrNull { it.name.lowercase().contains(lower) }
            ?: apps.firstOrNull { lower.contains(it.name.lowercase()) }

        if (match != null) {
            val success = launchPackage(match.packageName)
            return if (success) Pair(true, match.name) else Pair(false, "${match.name} launch nahi ho saka.")
        }

        return Pair(false, "Sir, '$name' application nahi mili.")
    }
}
