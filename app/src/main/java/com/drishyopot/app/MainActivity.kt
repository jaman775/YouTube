package com.drishyopot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.drishyopot.app.browser.BrowserState
import com.drishyopot.app.core.PipSupport
import com.drishyopot.app.ui.browser.BrowserScreen
import com.drishyopot.app.ui.download.DownloadFlow
import com.drishyopot.app.ui.download.DownloadsScreen
import com.drishyopot.app.download.DownloadNotifications
import com.drishyopot.app.yt.YoutubeUrls
import com.drishyopot.app.yt.YtLink
import androidx.compose.runtime.LaunchedEffect
import com.drishyopot.app.player.PlaybackHub
import com.drishyopot.app.settings.PrefKeys
import com.drishyopot.app.settings.SettingsStore
import com.drishyopot.app.ui.about.AboutScreen
import com.drishyopot.app.ui.home.HomeScreen
import com.drishyopot.app.ui.player.MiniPlayerHost
import com.drishyopot.app.ui.player.PlayerScreen
import com.drishyopot.app.ui.settings.BackupScreen
import com.drishyopot.app.ui.settings.DownloadSettingsScreen
import com.drishyopot.app.ui.settings.SettingsScreen
import com.drishyopot.app.ui.theme.DrishyopotTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as DrishyopotApp
    private val hub: PlaybackHub get() = app.hub
    private val store: SettingsStore get() = app.settings
    private var incoming by mutableStateOf<Incoming?>(null)
    private var incomingRoute by mutableStateOf<String?>(null)

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            PipSupport.handleAction(hub, intent.action)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pipSupported = PipSupport.isSupported(this)

        if (pipSupported) {
            val filter = IntentFilter().apply {
                addAction(PipSupport.ACTION_PREVIOUS)
                addAction(PipSupport.ACTION_PLAY_PAUSE)
                addAction(PipSupport.ACTION_NEXT)
            }
            ContextCompat.registerReceiver(this, pipReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            hub.onPipStateChanged = { updatePipParams() }
        }

        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            DrishyopotTheme {
                AppRoot(
                    store = store, hub = hub, browser = app.browser, pipSupported = pipSupported,
                    incoming = incoming, onIncomingHandled = { incoming = null },
                    incomingRoute = incomingRoute, onRouteHandled = { incomingRoute = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Links opened with, or shared to, Drishyopot; or a notification asking for a screen. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(DownloadNotifications.EXTRA_ROUTE)?.let { incomingRoute = it }
        val url = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> YoutubeUrls.firstUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            else -> null
        } ?: return
        incoming = Incoming(url, shared = intent.action == Intent.ACTION_SEND)
    }

    // ---- Picture-in-picture ------------------------------------------------------

    private fun canEnterPip(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            PipSupport.isSupported(this) &&
            store.bool(PrefKeys.ENABLE_PIP) &&
            hub.playerScreenVisible &&
            hub.player.playWhenReady

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !PipSupport.isSupported(this)) return
        try {
            setPictureInPictureParams(PipSupport.params(this, hub, autoEnter = canEnterPip()))
        } catch (_: IllegalStateException) {
            // Activity is not in a state that accepts PiP params.
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters PiP through setAutoEnterEnabled; older versions need this call.
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S && canEnterPip()) {
            enterPictureInPictureMode(PipSupport.params(this, hub, autoEnter = false))
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        hub.inPip = isInPictureInPictureMode
        // PiP window closed with the X: the activity goes to the background.
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
            if (!store.bool(PrefKeys.ENABLE_BACKGROUND_PLAY)) hub.player.pause()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode && !isChangingConfigurations && !store.bool(PrefKeys.ENABLE_BACKGROUND_PLAY)) {
            hub.player.pause()
        }
    }

    override fun onDestroy() {
        if (PipSupport.isSupported(this)) {
            hub.onPipStateChanged = null
            try { unregisterReceiver(pipReceiver) } catch (_: IllegalArgumentException) { }
        }
        super.onDestroy()
    }
}

/** A link that arrived from outside; [shared] means it came through the Share menu. */
data class Incoming(val url: String, val shared: Boolean)

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SETTINGS_DATA = "settings/data"
    const val SETTINGS_DOWNLOADS = "settings/downloads"
    const val ABOUT = "about"
    const val PLAYER = "player"
    const val BROWSER = "browser"
    const val DOWNLOADS = "downloads"

    fun parent(route: String): String = route.substringBeforeLast('/', missingDelimiterValue = HOME)
}

@Composable
private fun AppRoot(
    store: SettingsStore,
    hub: PlaybackHub,
    browser: BrowserState,
    pipSupported: Boolean,
    incoming: Incoming?,
    onIncomingHandled: () -> Unit,
    incomingRoute: String?,
    onRouteHandled: () -> Unit,
) {
    val downloads = (LocalContext.current.applicationContext as DrishyopotApp).downloads
    var downloadLink by remember { mutableStateOf<YtLink?>(null) }
    var shareChoice by remember { mutableStateOf<String?>(null) }
    var route by rememberSaveable { mutableStateOf(Routes.HOME) }
    var playerReturn by rememberSaveable { mutableStateOf(Routes.HOME) }
    val context = LocalContext.current
    var lastBackAt by remember { mutableLongStateOf(0L) }

    fun up(from: String) = if (from == Routes.PLAYER) playerReturn else Routes.parent(from)
    fun goPlayer() { if (route != Routes.PLAYER) playerReturn = route; route = Routes.PLAYER }

    // Sub-screens go up one level; Home needs a double press to exit.
    BackHandler(enabled = route != Routes.HOME) { route = up(route) }
    BackHandler(enabled = route == Routes.HOME) {
        val now = System.currentTimeMillis()
        if (now - lastBackAt < 2000) {
            (context as? ComponentActivity)?.finish()
        } else {
            lastBackAt = now
            Toast.makeText(context, R.string.back_again_to_exit, Toast.LENGTH_SHORT).show()
        }
    }

    val navigate: (String) -> Unit = { route = it }
    val back: () -> Unit = { route = up(route) }

    /** Sends any link to the right place: native player, browser, or a direct media URL. */
    val handleUrl: (String, String?) -> Unit = { url, videoId ->
        when (val link = YoutubeUrls.parse(url)) {
            is YtLink.Video, is YtLink.Playlist -> { hub.openLink(link); goPlayer() }
            is YtLink.Page -> { browser.open(link.url); route = Routes.BROWSER }
            null -> { hub.play(url, videoId = videoId); goPlayer() }
        }
    }

    /** Starts the download dialog for a link, or tells the user it only works for YouTube. */
    val startDownload: (String) -> Unit = { url ->
        when (val link = YoutubeUrls.parse(url)) {
            is YtLink.Video, is YtLink.Playlist -> downloadLink = link
            else -> Toast.makeText(context, R.string.dl_only_youtube, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(incoming) {
        val item = incoming
        if (item != null) {
            // Shared YouTube links ask what to do; opened links just play.
            if (item.shared && YoutubeUrls.parse(item.url).let { it is YtLink.Video || it is YtLink.Playlist }) shareChoice = item.url
            else handleUrl(item.url, null)
            onIncomingHandled()
        }
    }
    LaunchedEffect(incomingRoute) {
        if (incomingRoute != null) {
            if (incomingRoute == Routes.DOWNLOADS) route = Routes.DOWNLOADS
            onRouteHandled()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            route == Routes.HOME -> HomeScreen(hub = hub, onNavigate = navigate, onPlay = handleUrl, onDownload = startDownload)
            route == Routes.PLAYER -> PlayerScreen(hub = hub, store = store, onBack = back)
            route == Routes.BROWSER -> BrowserScreen(
                state = browser, store = store, onBack = back,
                onOpenNative = { link -> hub.openLink(link); goPlayer() },
            )
            route == Routes.DOWNLOADS -> DownloadsScreen(
                center = downloads, store = store, onBack = back,
                onOpenSettings = { route = Routes.SETTINGS_DOWNLOADS },
                onPlayFile = { url, title -> hub.play(url, title); goPlayer() },
            )
            route == Routes.ABOUT -> AboutScreen(store = store, onBack = back)
            route == Routes.SETTINGS_DATA -> BackupScreen(store = store, onBack = back)
            route == Routes.SETTINGS_DOWNLOADS -> DownloadSettingsScreen(store = store, onBack = back)
            route == Routes.SETTINGS || route.startsWith("settings/") ->
                SettingsScreen(route = route, store = store, pipSupported = pipSupported, onNavigate = navigate, onBack = back)
            else -> HomeScreen(hub = hub, onNavigate = navigate, onPlay = handleUrl, onDownload = startDownload)
        }
        downloadLink?.let { link -> DownloadFlow(link, downloads) { downloadLink = null } }
        shareChoice?.let { url ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { shareChoice = null },
                title = { androidx.compose.material3.Text(stringResource(R.string.share_choice_title)) },
                text = { androidx.compose.material3.Text(url, maxLines = 2) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { shareChoice = null; handleUrl(url, null) }) {
                        androidx.compose.material3.Text(stringResource(R.string.open_url_play))
                    }
                },
                dismissButton = {
                    androidx.compose.foundation.layout.Row {
                        androidx.compose.material3.TextButton(onClick = { shareChoice = null; startDownload(url) }) {
                            androidx.compose.material3.Text(stringResource(R.string.dl_download))
                        }
                        androidx.compose.material3.TextButton(onClick = { shareChoice = null }) {
                            androidx.compose.material3.Text(stringResource(R.string.cancel))
                        }
                    }
                },
            )
        }
        if (route != Routes.PLAYER) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                MiniPlayerHost(hub = hub, onRestore = { goPlayer() })
            }
        }
    }
}
