package com.drishyopot.app.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Small rotating file logger plus ZIP export for bug reports. */
object DiagnosticLog {
    private const val MAX_BYTES = 512 * 1024L
    private val lock = Any()
    private var dir: File? = null
    private val stamp get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    fun init(context: Context) {
        dir = File(context.filesDir, "logs").also { it.mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            log("CRASH", "Uncaught exception on ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
        log("App", "Logger started")
    }

    fun log(tag: String, message: String, error: Throwable? = null) {
        val base = dir ?: return
        synchronized(lock) {
            try {
                val file = File(base, "drishyopot.log")
                if (file.length() > MAX_BYTES) {
                    val old = File(base, "drishyopot.log.1")
                    old.delete()
                    file.renameTo(old)
                }
                file.appendText("$stamp [$tag] $message\n")
                if (error != null) file.appendText(error.stackTraceToString() + "\n")
            } catch (_: Exception) {
                // Logging must never crash the app.
            }
        }
    }

    fun writeArchive(context: Context, settingsJson: String, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.putText("device.txt", deviceInfo(context))
            zip.putText("settings.json", settingsJson)
            val base = dir
            if (base != null) {
                listOf("drishyopot.log", "drishyopot.log.1").forEach { name ->
                    val file = File(base, name)
                    if (file.exists()) {
                        zip.putNextEntry(ZipEntry(name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun deviceInfo(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return buildString {
            appendLine("App: ${context.packageName} ${info.versionName} ($code)")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Locale: ${Locale.getDefault()}")
            appendLine("Generated: $stamp")
        }
    }
}
