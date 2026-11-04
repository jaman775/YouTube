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
import com.drishyopot.app.ui.home.OpenLinkDialog
import com.drishyopot.app.ui.player.MiniPlayerHost
import com.drishyopot.app.ui.player.PlayerScreen
import com.drishyopot.app.browser.BrowserInput
import com.drishyopot.app.ui.settings.AccountScreen
import com.drishyopot.app.ui.settings.BackupScreen
import com.drishyopot.app.ui.settings.EngineScreen
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
    /** The app opens straight into YouTube; every other screen leads back to it. */
    const val BROWSER = "browser"
    const val ROOT = BROWSER
    const val SETTINGS = "settings"
    const val SETTINGS_DATA = "settings/data"
    const val SETTINGS_DOWNLOADS = "settings/downloads"
    const val SETTINGS_ACCOUNT = "settings/account"
    const val SETTINGS_ENGINE = "settings/engine"
    const val ABOUT = "about"
    const val PLAYER = "player"
    const val DOWNLOADS = "downloads"

    /** Screens other apps, shortcuts and notifications may ask for by name. */
    val EXTERNAL = setOf(SETTINGS, SETTINGS_DOWNLOADS, SETTINGS_ACCOUNT, SETTINGS_ENGINE, ABOUT, DOWNLOADS)

    fun parent(route: String): String = route.substringBeforeLast('/', missingDelimiterValue = ROOT)

    /** Maps an entry of the menu inside YouTube's "You" page to a screen, or null when it is not a screen. */
    fun forMenuId(id: String): String? = when (id) {
        "settings" -> SETTINGS
        "downloads" -> DOWNLOADS
        "account" -> SETTINGS_ACCOUNT
        "engine" -> SETTINGS_ENGINE
        "about" -> ABOUT
        else -> null
    }
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
    var showOpenLink by remember { mutableStateOf(false) }

    var route by rememberSaveable { mutableStateOf(Routes.ROOT) }
    var playerReturn by rememberSaveable { mutableStateOf(Routes.ROOT) }
    val context = LocalContext.current
    var lastBackAt by remember { mutableLongStateOf(0L) }

    fun up(from: String) = if (from == Routes.PLAYER) playerReturn else Routes.parent(from)
    fun goPlayer() { if (route != Routes.PLAYER) playerReturn = route; route = Routes.PLAYER }

    // Every screen goes up one level; leaving YouTube itself needs a second press.
    val exitOnSecondPress: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastBackAt < 2000) {
            (context as? ComponentActivity)?.finish()
        } else {
            lastBackAt = now
            Toast.makeText(context, R.string.back_again_to_exit, Toast.LENGTH_SHORT).show()
        }
    }
    BackHandler(enabled = route != Routes.ROOT) { route = up(route) }

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
            if (incomingRoute in Routes.EXTERNAL) route = incomingRoute
            onRouteHandled()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            route == Routes.PLAYER -> PlayerScreen(
                hub = hub, store = store, onBack = back,
                onSignIn = { browser.open(BrowserInput.SIGN_IN); route = Routes.BROWSER },
            )
            route == Routes.DOWNLOADS -> DownloadsScreen(
                center = downloads, store = store, onBack = back,
                onOpenSettings = { route = Routes.SETTINGS_DOWNLOADS },
                onPlayFile = { url, title -> hub.play(url, title); goPlayer() },
            )
            route == Routes.SETTINGS_ACCOUNT -> AccountScreen(
                store = store, onBack = back,
                onSignIn = { browser.open(BrowserInput.SIGN_IN); route = Routes.BROWSER },
            )
            route == Routes.SETTINGS_ENGINE -> EngineScreen(store = store, onBack = back)
            route == Routes.ABOUT -> AboutScreen(store = store, onBack = back)
            route == Routes.SETTINGS_DATA -> BackupScreen(store = store, onBack = back)
            route == Routes.SETTINGS_DOWNLOADS -> DownloadSettingsScreen(store = store, onBack = back)
            route == Routes.SETTINGS || route.startsWith("settings/") ->
                SettingsScreen(route = route, store = store, pipSupported = pipSupported, onNavigate = navigate, onBack = back)
            else -> BrowserScreen(
                state = browser, hub = hub, store = store,
                onExit = exitOnSecondPress,
                onOpenNative = { link -> hub.openLink(link); goPlayer() },
                onAppMenu = { id ->
                    if (id == "openlink") showOpenLink = true
                    else Routes.forMenuId(id)?.let { route = it }
                },
            )
        }
        downloadLink?.let { link -> DownloadFlow(link, downloads) { downloadLink = null } }
        if (showOpenLink) {
            OpenLinkDialog(
                onPlay = { handleUrl(it, null) },
                onQueue = {
                    val added = hub.enqueue(it)
                    Toast.makeText(context, if (added) R.string.queue_added else R.string.queue_already_added, Toast.LENGTH_SHORT).show()
                },
                onDownload = startDownload,
                onDismiss = { showOpenLink = false },
            )
        }
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
