package com.drishyopot.app.ui.player

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.PixelCopy
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * "Ambient mode": a soft glow behind the video taken from the picture itself.
 * Samples a tiny copy of the current frame twice a second and fades towards its average colour.
 */
@Composable
fun AmbientGlow(videoView: () -> View?, active: Boolean, base: Color, modifier: Modifier = Modifier) {
    var sampled by remember { mutableStateOf(Color.Transparent) }
    LaunchedEffect(active) {
        if (!active) { sampled = Color.Transparent; return@LaunchedEffect }
        while (true) {
            val view = videoView()
            if (view != null && view.width > 0 && view.height > 0) {
                averageColor(view)?.let { sampled = it }
            }
            delay(500)
        }
    }
    val glow by animateColorAsState(sampled, tween(700), label = "ambient")
    Box(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to glow.copy(alpha = if (active) 0.55f else 0f),
                1f to base,
            ),
        ),
    )
}

/** Average colour of a 16x9 copy of what the video view currently shows, brightened a little. */
private suspend fun averageColor(view: View): Color? {
    val bmp = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
    val ok = when {
        view is TextureView -> view.getBitmap(bmp) != null
        view is SurfaceView && Build.VERSION.SDK_INT >= 26 -> suspendCancellableCoroutine { cont ->
            try {
                PixelCopy.request(view, bmp, { result -> if (cont.isActive) cont.resume(result == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
            } catch (_: Throwable) {
                if (cont.isActive) cont.resume(false)
            }
        }
        else -> false
    }
    if (!ok) { bmp.recycle(); return null }
    var r = 0L; var g = 0L; var b = 0L
    val pixels = IntArray(16 * 9)
    bmp.getPixels(pixels, 0, 16, 0, 0, 16, 9)
    bmp.recycle()
    for (p in pixels) { r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF }
    val n = pixels.size
    if (r + g + b == 0L) return null // a black frame carries no colour: keep the last glow
    fun lift(sum: Long) = ((sum / n) * 1.15f).coerceAtMost(255f) / 255f
    return Color(lift(r), lift(g), lift(b))
}
