package com.kosong.misettings

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

class ModuleConfigProvider : ContentProvider() {
    private lateinit var store: ConfigStore
    private lateinit var logs: ModuleLog

    override fun onCreate(): Boolean {
        val c = context ?: return false
        store = ConfigStore(c)
        logs = ModuleLog(c)
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!isAllowedCaller()) return Bundle().apply { putBoolean("ok", false) }
        return when (method) {
            ConfigContract.METHOD_GET -> store.load().toBundle().apply { putBoolean("ok", true) }
            ConfigContract.METHOD_LOG -> {
                val cfg = store.load()
                if (cfg.logEnabled) logs.append(extras?.getString("message") ?: "")
                Bundle().apply { putBoolean("ok", true) }
            }
            else -> Bundle().apply { putBoolean("ok", false) }
        }
    }

    private fun isAllowedCaller(): Boolean {
        val callingPackage = callingPackage
        if (callingPackage == context?.packageName || callingPackage == ConfigContract.EXPECTED_PACKAGE) return true
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return true
        val packages = context?.packageManager?.getPackagesForUid(uid).orEmpty()
        return packages.size == 1 && packages[0] == ConfigContract.EXPECTED_PACKAGE
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
