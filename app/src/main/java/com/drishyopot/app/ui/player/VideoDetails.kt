package com.drishyopot.app.ui.player

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.drishyopot.app.R
import com.drishyopot.app.settings.PrefKeys
import com.drishyopot.app.settings.SettingsStore
import com.drishyopot.app.yt.ExtraInfoApis
import com.drishyopot.app.yt.YtVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Information area under the player in portrait mode. */
@Composable
fun VideoDetails(
    title: String,
    url: String,
    videoId: String?,
    video: YtVideo?,
    store: SettingsStore,
) {
    val wantLikes = store.bool(PrefKeys.ENABLE_SHOW_LIKES)
    val wantDislikes = store.bool(PrefKeys.ENABLE_DISPLAY_DISLIKES)
    val wantOriginal = store.bool(PrefKeys.USE_ORIGINAL_TITLE)

    val votes by produceState<com.drishyopot.app.yt.RydVotes?>(null, videoId, wantLikes, wantDislikes) {
        value = if (videoId != null && (wantLikes || wantDislikes)) withContext(Dispatchers.IO) { ExtraInfoApis.votes(videoId) } else null
    }
    val originalTitle by produceState<String?>(null, videoId, wantOriginal) {
        value = if (videoId != null && wantOriginal) withContext(Dispatchers.IO) { ExtraInfoApis.originalTitle(videoId) } else null
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(originalTitle ?: title, style = MaterialTheme.typography.titleMedium)
        video?.uploader?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        votes?.let { v ->
            Row(Modifier.padding(top = 8.dp)) {
                if (wantLikes) Text("👍 ${ExtraInfoApis.compact(v.likes)}", Modifier.padding(end = 16.dp))
                if (wantDislikes) Text("👎 ${ExtraInfoApis.compact(v.dislikes)}", Modifier.padding(end = 16.dp))
                if (v.viewCount > 0) Text(stringResource(R.string.views_count, ExtraInfoApis.compact(v.viewCount)))
            }
        }
        if (videoId == null) {
            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (video?.isLive == true && videoId != null) {
            LiveChat(videoId)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LiveChat(videoId: String) {
    Text(stringResource(R.string.live_chat), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(360.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl("https://www.youtube.com/live_chat?is_popout=1&dark_theme=1&embed_domain=youtube.com&v=$videoId")
            }
        },
        onRelease = { it.stopLoading(); it.destroy() },
    )
}
