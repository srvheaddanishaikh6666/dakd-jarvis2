package com.dakd.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

class FileManager(private val context: Context) {

    fun openFiles(): Pair<Boolean, String> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://media/external/file"), "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Files open kar diya hai Sir.")
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                Pair(true, "File selector open kar diya hai Sir.")
            } catch (err: Exception) {
                Pair(false, "Files open nahi ho saka: ${err.message}")
            }
        }
    }

    fun openGallery(): Pair<Boolean, String> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Gallery open kar diya hai Sir.")
        } catch (e: Exception) {
            Pair(false, "Gallery open nahi ho saka: ${e.message}")
        }
    }
}
