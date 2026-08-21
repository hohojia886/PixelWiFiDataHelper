package io.github.hohojia886.pixelwifidatahelper.utils

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.content.Context
import android.util.Log

class RemotePrefProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val callingUid = android.os.Binder.getCallingUid()
        val systemUiUid = 1000
        val selfUid = android.os.Process.myUid()

        if (callingUid != systemUiUid && callingUid != selfUid) {
            Log.w("RemotePrefProvider", "Unauthorized call from UID: $callingUid")
            return null
        }

        Log.d("RemotePrefProvider", "call: $method from UID: $callingUid")
        if (method == "get") {
            val res = Bundle()
            val ctx = context?.let {
                if (it.isDeviceProtectedStorage) it else it.createDeviceProtectedStorageContext()
            } ?: context
            val prefs = ctx?.getSharedPreferences("pixel_wifi_data_prefs", Context.MODE_PRIVATE)
            prefs?.all?.forEach { (k, v) ->
                when (v) {
                    is Boolean -> res.putBoolean(k, v)
                    is Int -> res.putInt(k, v)
                    is String -> res.putString(k, v)
                }
            }
            Log.d("RemotePrefProvider", "returning prefs: ${res.size()}")
            return res
        }
        return null
    }

    override fun query(uri: Uri, p1: Array<out String>?, p2: String?, p3: Array<out String>?, p4: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
