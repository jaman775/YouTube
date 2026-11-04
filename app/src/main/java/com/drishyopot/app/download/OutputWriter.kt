package com.drishyopot.app.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.drishyopot.app.diagnostics.DiagnosticLog
import java.io.File

/** Moves finished files from the private work folder into the user's visible storage. */
class OutputWriter(private val context: Context) {
    data class Published(val uri: String, val displayName: String)

    /** Video (or audio) result. Goes to Movies/Drishyopot or Music/Drishyopot. */
    fun publishMedia(source: File, displayName: String, mime: String, audio: Boolean): Published {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val folder = (if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/Drishyopot"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(collection, values) ?: throw java.io.IOException("Could not create media entry")
            try {
                context.contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                    ?: throw java.io.IOException("Could not open output")
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            }
            return Published(uri.toString(), displayName)
        }
        val dir = legacyDir(if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES)
        val target = uniqueFile(dir, displayName)
        source.copyTo(target, overwrite = false)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        return Published(target.absolutePath, target.name)
    }

    /** Subtitles and cover images. Goes to Download/Drishyopot. Failures are logged, not fatal. */
    fun publishAttachment(bytes: ByteArray, displayName: String, mime: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Drishyopot")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.also { context.contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) } }?.toString()
        } else {
            val target = uniqueFile(legacyDir(Environment.DIRECTORY_DOWNLOADS), displayName)
            target.writeBytes(bytes)
            target.absolutePath
        }
    } catch (e: Exception) {
        DiagnosticLog.log("Downloads", "Attachment $displayName failed", e)
        null
    }

    fun delete(uriOrPath: String?) {
        if (uriOrPath.isNullOrBlank()) return
        try {
            if (uriOrPath.startsWith("content://")) context.contentResolver.delete(Uri.parse(uriOrPath), null, null)
            else File(uriOrPath).delete()
        } catch (e: Exception) {
            DiagnosticLog.log("Downloads", "Delete failed", e)
        }
    }

    fun exists(uriOrPath: String?): Boolean {
        if (uriOrPath.isNullOrBlank()) return false
        return try {
            if (uriOrPath.startsWith("content://")) {
                context.contentResolver.query(Uri.parse(uriOrPath), arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.moveToFirst() } == true
            } else File(uriOrPath).exists()
        } catch (_: Exception) { false }
    }

    /** A Uri another app can read. */
    fun shareUri(uriOrPath: String): Uri =
        if (uriOrPath.startsWith("content://")) Uri.parse(uriOrPath)
        else FileProvider.getUriForFile(context, "${context.packageName}.files", File(uriOrPath))

    /** A Uri this app's player can open. */
    fun playUri(uriOrPath: String): String =
        if (uriOrPath.startsWith("content://")) uriOrPath else Uri.fromFile(File(uriOrPath)).toString()

    private fun legacyDir(type: String): File =
        (context.getExternalFilesDir(type) ?: File(context.filesDir, type)).also { it.mkdirs() }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var n = 1
        while (file.exists()) {
            file = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext")
            n++
        }
        return file
    }
}
