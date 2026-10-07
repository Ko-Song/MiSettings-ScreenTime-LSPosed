package com.kosong.misettings

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ModuleLog(private val context: Context) {
    private val lock = Any()
    private val privateDir = File(context.filesDir, "logs").apply { mkdirs() }

    fun append(message: String) {
        if (message.isBlank()) return
        synchronized(lock) {
            val line = "${timestamp()} $message\n"
            val uri = context.getSharedPreferences("screen_time_config", Context.MODE_PRIVATE)
                .getString("logTreeUri", "")
            if (!uri.isNullOrBlank() && appendTree(Uri.parse(uri), line)) return
            File(privateDir, "module-${date()}.log").appendText(line)
            cleanupPrivate()
        }
    }

    fun cleanup() {
        synchronized(lock) {
            privateDir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 7L * 86_400_000L }
                ?.forEach { it.delete() }
        }
    }

    private fun appendTree(tree: Uri, text: String): Boolean = runCatching {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return@runCatching false
        val dir = root.findFile("MiSettingsScreenTime") ?: root.createDirectory("MiSettingsScreenTime") ?: return@runCatching false
        val fileName = "module-${date()}.log"
        val file = dir.findFile(fileName) ?: dir.createFile("text/plain", fileName) ?: return@runCatching false
        context.contentResolver.openOutputStream(file.uri, "wa")?.bufferedWriter()?.use { it.append(text) }
        true
    }.getOrDefault(false)

    private fun cleanupPrivate() {
        privateDir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 7L * 86_400_000L }
            ?.forEach { it.delete() }
    }

    private fun timestamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
    private fun date() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
