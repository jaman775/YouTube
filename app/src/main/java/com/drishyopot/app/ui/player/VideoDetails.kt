package com.drishyopot.app.ui.player

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.drishyopot.app.account.YoutubeAccount
import com.drishyopot.app.ui.components.RemoteImage
import com.drishyopot.app.yt.YoutubeActions
import com.drishyopot.app.yt.YoutubeResolver
import com.drishyopot.app.yt.YtComments
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.Icons
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

/** Information area under the player in portrait mode, laid out like the YouTube app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoDetails(
    title: String,
    url: String,
    videoId: String?,
    video: YtVideo?,
    store: SettingsStore,
    onDownload: () -> Unit = {},
    onPip: (() -> Unit)? = null,
    onSignIn: () -> Unit = {},
    onQueue: () -> Unit = {},
    onOpenVideo: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wantDislikes = store.bool(PrefKeys.ENABLE_DISPLAY_DISLIKES)
    val wantOriginal = store.bool(PrefKeys.USE_ORIGINAL_TITLE)

    val votes by produceState<com.drishyopot.app.yt.RydVotes?>(null, videoId, wantDislikes) {
        value = if (videoId != null && wantDislikes) withContext(Dispatchers.IO) { ExtraInfoApis.votes(videoId) } else null
    }
    val originalTitle by produceState<String?>(null, videoId, wantOriginal) {
        value = if (videoId != null && wantOriginal) withContext(Dispatchers.IO) { ExtraInfoApis.originalTitle(videoId) } else null
    }
    val comments by produceState<YtComments?>(null, videoId) {
        value = if (videoId != null && video?.isLive != true) withContext(Dispatchers.IO) { YoutubeResolver.comments(videoId) } else null
    }

    // Account state (like / dislike / subscribe). Unknown until YouTube answers; stays unknown when signed out.
    var liked by remember(videoId) { mutableStateOf(false) }
    var disliked by remember(videoId) { mutableStateOf(false) }
    var subscribed by remember(videoId) { mutableStateOf(false) }
    LaunchedEffect(videoId) {
        if (videoId != null && YoutubeAccount.isSignedIn()) {
            val st = withContext(Dispatchers.IO) { YoutubeActions.state(videoId) }
            st.liked?.let { liked = it }
            st.disliked?.let { disliked = it }
            st.subscribed?.let { subscribed = it }
        }
    }

    var showDescription by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var showSignIn by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    fun needSignIn(): Boolean {
        if (YoutubeAccount.isSignedIn()) return false
        showSignIn = true
        return true
    }
    fun failed() = Toast.makeText(context, context.getString(R.string.yt_action_failed), Toast.LENGTH_SHORT).show()

    val likes = video?.likeCount?.takeIf { it >= 0 } ?: votes?.likes
    val views = video?.viewCount?.takeIf { it >= 0 } ?: votes?.viewCount?.takeIf { it > 0 }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(originalTitle ?: title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // "@Channel  960K likes  62M views  3d ago  Dubbed  more" — tap to read the description.
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 6.dp).clickable { showDescription = true },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            video?.uploader?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
            likes?.let { Text(stringResource(R.string.yt_likes_count, ExtraInfoApis.compact(it)), style = MaterialTheme.typography.bodyMedium, color = dim) }
            if (wantDislikes) votes?.let { Text(stringResource(R.string.yt_dislikes_count, ExtraInfoApis.compact(it.dislikes)), style = MaterialTheme.typography.bodyMedium, color = dim) }
            views?.let { Text(stringResource(R.string.views_count, ExtraInfoApis.compact(it)), style = MaterialTheme.typography.bodyMedium, color = dim) }
            video?.uploadedAtMs?.takeIf { it > 0 }?.let { Text(timeAgo(context, it), style = MaterialTheme.typography.bodyMedium, color = dim) }
            if (video?.hasDubbedAudio == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.RecordVoiceOver, null, modifier = Modifier.size(16.dp), tint = dim)
                    Text(stringResource(R.string.yt_dubbed), style = MaterialTheme.typography.bodyMedium, color = dim, modifier = Modifier.padding(start = 4.dp))
                }
            }
            Text(stringResource(R.string.yt_more), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }

        // Channel row: avatar, Subscribe, then the action icons (scrolls sideways when it does not fit).
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RemoteImage(video?.uploaderAvatar, Modifier.size(40.dp).clip(CircleShape))
            Spacer(Modifier.width(8.dp))
            val channelId = video?.channelId
            Box(
                Modifier.clip(CircleShape)
                    .background(if (subscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onBackground)
                    .clickable {
                        if (channelId == null) { failed(); return@clickable }
                        if (needSignIn()) return@clickable
                        val target = !subscribed
                        scope.launch {
                            val done = withContext(Dispatchers.IO) { if (target) YoutubeActions.subscribe(channelId) else YoutubeActions.unsubscribe(channelId) }
                            if (done) subscribed = target else failed()
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    stringResource(if (subscribed) R.string.yt_subscribed else R.string.yt_subscribe),
                    color = if (subscribed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.background,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                if (videoId == null || needSignIn()) return@IconButton
                val target = !liked
                scope.launch {
                    val done = withContext(Dispatchers.IO) { if (target) YoutubeActions.like(videoId) else YoutubeActions.removeLike(videoId) }
                    if (done) { liked = target; if (target) disliked = false } else failed()
                }
            }) { Icon(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, stringResource(R.string.yt_like)) }
            IconButton(onClick = {
                if (videoId == null || needSignIn()) return@IconButton
                val target = !disliked
                scope.launch {
                    val done = withContext(Dispatchers.IO) { if (target) YoutubeActions.dislike(videoId) else YoutubeActions.removeLike(videoId) }
                    if (done) { disliked = target; if (target) liked = false } else failed()
                }
            }) { Icon(if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, stringResource(R.string.yt_dislike)) }
            IconButton(onClick = {
                val link = if (videoId != null) "https://youtu.be/$videoId" else url
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Icon(Icons.AutoMirrored.Outlined.Reply, stringResource(R.string.share), modifier = Modifier.graphicsLayer(scaleX = -1f)) }
            onPip?.let {
                IconButton(onClick = it) { Icon(Icons.Filled.PictureInPictureAlt, stringResource(R.string.pip_enter)) }
            }
            IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, stringResource(R.string.dl_download)) }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreHoriz, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.queue)) }, onClick = { showMenu = false; onQueue() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.yt_copy_link)) }, onClick = {
                        showMenu = false
                        val link = if (videoId != null) "https://youtu.be/$videoId" else url
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("link", link))
                        Toast.makeText(context, context.getString(R.string.yt_link_copied), Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }

        // Comments card with the top comment, like the YouTube app.
        comments?.let { c ->
            Column(
                Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).clickable(enabled = !c.disabled) { showComments = true }
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.yt_comments), fontWeight = FontWeight.Bold)
                    if (c.count >= 0) Text(ExtraInfoApis.compact(c.count), color = dim, modifier = Modifier.padding(start = 8.dp))
                }
                if (c.disabled) {
                    Text(stringResource(R.string.yt_comments_off), color = dim, modifier = Modifier.padding(top = 8.dp))
                } else c.items.firstOrNull()?.let { top ->
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RemoteImage(top.avatar, Modifier.size(26.dp).clip(CircleShape))
                        Text(top.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }

        // Recommended videos, as big cards like the YouTube app.
        video?.related?.forEach { r ->
            Column(Modifier.fillMaxWidth().padding(top = 18.dp).clickable { onOpenVideo(r.id) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
                    RemoteImage(r.thumbnail, Modifier.fillMaxSize())
                    if (r.durationSec > 0) {
                        Text(
                            formatTime(r.durationSec * 1000L), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                val meta = listOfNotNull(
                    r.uploader.takeIf { it.isNotBlank() },
                    r.views.takeIf { it >= 0 }?.let { stringResource(R.string.views_count, ExtraInfoApis.compact(it)) },
                    r.date.takeIf { it.isNotBlank() },
                ).joinToString("  ·  ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (videoId == null) {
            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        }
        if (video?.isLive == true && videoId != null) {
            LiveChat(videoId)
        }
    }

    if (showDescription) DescriptionSheet(originalTitle ?: title, video, likes, views) { showDescription = false }
    if (showComments) comments?.let { CommentsSheet(it) { showComments = false } }
    if (showSignIn) {
        AlertDialog(
            onDismissRequest = { showSignIn = false },
            title = { Text(stringResource(R.string.yt_sign_in_title)) },
            text = { Text(stringResource(R.string.yt_sign_in_message)) },
            confirmButton = { TextButton(onClick = { showSignIn = false; onSignIn() }) { Text(stringResource(R.string.yt_sign_in)) } },
            dismissButton = { TextButton(onClick = { showSignIn = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun timeAgo(context: Context, thenMs: Long): String {
    val minutes = ((System.currentTimeMillis() - thenMs) / 60_000L).coerceAtLeast(0)
    val r = context.resources
    return when {
        minutes < 60 -> r.getString(R.string.yt_ago_minutes, minutes.coerceAtLeast(1))
        minutes < 60 * 24 -> r.getString(R.string.yt_ago_hours, minutes / 60)
        minutes < 60 * 24 * 30 -> r.getString(R.string.yt_ago_days, minutes / (60 * 24))
        minutes < 60 * 24 * 365 -> r.getString(R.string.yt_ago_months, minutes / (60 * 24 * 30))
        else -> r.getString(R.string.yt_ago_years, minutes / (60 * 24 * 365))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DescriptionSheet(title: String, video: YtVideo?, likes: Long?, views: Long?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.yt_description), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                likes?.let { DescStat(ExtraInfoApis.compact(it), stringResource(R.string.yt_likes)) }
                views?.let { DescStat(ExtraInfoApis.compact(it), stringResource(R.string.yt_views)) }
                video?.uploadedAtMs?.takeIf { it > 0 }?.let {
                    DescStat(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(it)), timeAgo(context, it))
                }
            }
            video?.let { v ->
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RemoteImage(v.uploaderAvatar, Modifier.size(36.dp).clip(CircleShape))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(v.uploader, fontWeight = FontWeight.Medium)
                        if (v.subscribers >= 0) Text(stringResource(R.string.yt_subscribers, ExtraInfoApis.compact(v.subscribers)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(v.description.ifBlank { stringResource(R.string.yt_no_description) }, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun DescStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(comments: YtComments, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            stringResource(R.string.yt_comments) + if (comments.count >= 0) "  " + ExtraInfoApis.compact(comments.count) else "",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.8f).dp)) {
            items(comments.items) { c ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    RemoteImage(c.avatar, Modifier.size(36.dp).clip(CircleShape))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            (if (c.pinned) "\uD83D\uDCCC " else "") + c.author + (if (c.date.isNotBlank()) "  ·  " + c.date else ""),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(c.text, modifier = Modifier.padding(top = 2.dp))
                        if (c.likes > 0) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ThumbUp, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(ExtraInfoApis.compact(c.likes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
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
