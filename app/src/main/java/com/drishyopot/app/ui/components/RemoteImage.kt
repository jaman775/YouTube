package com.drishyopot.app.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private object ImageCache {
    val cache = LruCache<String, ImageBitmap>(80)

    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection().apply { connectTimeout = 6_000; readTimeout = 8_000 }
                conn.getInputStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap()?.also { cache.put(url, it) }
            } catch (_: Exception) { null }
        }
    }
}

/** A small network image (channel avatars, comment authors). Shows a plain block until it has loaded. */
@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, url) { value = if (url == null) null else ImageCache.load(url) }
    val loaded = bitmap
    if (loaded != null) {
        Image(loaded, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
