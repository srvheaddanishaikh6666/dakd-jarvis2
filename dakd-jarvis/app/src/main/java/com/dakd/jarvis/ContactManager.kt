package com.dakd.jarvis

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

class ContactManager(private val context: Context) {

    fun hasContactPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun findPhoneNumber(query: String): String? {
        if (!hasContactPermission() || query.isBlank()) return null

        val uri: Uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex != -1) {
                    return cursor.getString(numberIndex)
                }
            }
        } catch (e: Exception) {
            // Permission or querying error
        } finally {
            cursor?.close()
        }
        return null
    }

    fun getTopContacts(limit: Int = 10): List<Pair<String, String>> {
        if (!hasContactPermission()) return emptyList()
        val list = mutableListOf<Pair<String, String>>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")
            while (cursor != null && cursor.moveToNext() && list.size < limit) {
                val name = cursor.getString(0) ?: "Unknown"
                val number = cursor.getString(1) ?: ""
                list.add(Pair(name, number))
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            cursor?.close()
        }
        return list
    }
}
