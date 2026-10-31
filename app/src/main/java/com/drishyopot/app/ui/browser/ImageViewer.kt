package com.drishyopot.app.ui.browser

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.drishyopot.app.R
import com.drishyopot.app.diagnostics.DiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private class LoadedImage(val bytes: ByteArray, val bitmap: Bitmap, val mime: String)

private fun fetchImage(url: String): LoadedImage? {
    val conn = URL(url).openConnection() as HttpURLConnection
    return try {
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        if (conn.responseCode != 200) return null
        val bytes = conn.inputStream.use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 2400 || bounds.outHeight / sample > 2400) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        LoadedImage(bytes, bitmap, bounds.outMimeType ?: "image/jpeg")
    } catch (e: Exception) {
        DiagnosticLog.log("ImageViewer", "Load failed", e)
        null
    } finally { conn.disconnect() }
}

private fun extensionFor(mime: String) = when {
    mime.contains("png") -> "png"
    mime.contains("webp") -> "webp"
    mime.contains("gif") -> "gif"
    else -> "jpg"
}

/** Saves the original bytes to the Pictures folder. Returns true on success. */
private fun saveToGallery(context: Context, image: LoadedImage): Boolean {
    val name = "drishyopot-${System.currentTimeMillis()}.${extensionFor(image.mime)}"
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, image.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Drishyopot")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { it.write(image.bytes) } ?: return false
            true
        } else {
            // Before Android 10 the app-specific folder avoids needing a storage permission.
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return false
            File(dir, name).writeBytes(image.bytes)
            true
        }
    } catch (e: Exception) {
        DiagnosticLog.log("ImageViewer", "Save failed", e)
        false
    }
}

private fun prepareShare(context: Context, image: LoadedImage): android.net.Uri {
    val dir = File(context.cacheDir, "images").also { it.mkdirs() }
    val file = File(dir, "shared.${extensionFor(image.mime)}")
    file.writeBytes(image.bytes)
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

private fun shareImage(context: Context, uri: android.net.Uri, mime: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

/** Full-screen image viewer with pinch-zoom, pan, save and share. */
@Composable
fun ImageViewer(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var image by remember { mutableStateOf<LoadedImage?>(null) }
    var failed by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val savedText = stringResource(R.string.image_saved)
    val saveFailedText = stringResource(R.string.image_save_failed)
    val shareTitle = stringResource(R.string.image_share)

    LaunchedEffect(url) {
        val loaded = withContext(Dispatchers.IO) { fetchImage(url) }
        image = loaded
        failed = loaded == null
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val loaded = image
            when {
                loaded != null -> Image(
                    bitmap = loaded.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                if (scale > 1f) { offsetX += pan.x; offsetY += pan.y } else { offsetX = 0f; offsetY = 0f }
                            }
                        }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
                )
                failed -> Text(stringResource(R.string.image_load_failed), color = Color.White, modifier = Modifier.align(Alignment.Center))
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().align(Alignment.TopCenter)) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close), tint = Color.White) }
                Box(Modifier.weight(1f))
                IconButton(
                    enabled = loaded != null,
                    onClick = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { saveToGallery(context, loaded!!) }
                            status = if (ok) savedText else saveFailedText
                        }
                    },
                ) { Icon(Icons.Filled.Save, stringResource(R.string.image_save), tint = Color.White) }
                IconButton(
                    enabled = loaded != null,
                    onClick = {
                        scope.launch {
                            val prepared = withContext(Dispatchers.IO) { runCatching { prepareShare(context, loaded!!) }.getOrNull() }
                            if (prepared != null) shareImage(context, prepared, loaded!!.mime, shareTitle)
                        }
                    },
                ) { Icon(Icons.Filled.Share, shareTitle, tint = Color.White) }
            }
            status?.let {
                Text(
                    it, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).statusBarsPadding().background(Color.Black.copy(alpha = 0.7f)),
                )
            }
        }
    }
}
