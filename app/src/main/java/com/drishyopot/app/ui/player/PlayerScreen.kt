@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.drishyopot.app.ui.player

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.drishyopot.app.core.PipSupport
import com.drishyopot.app.player.PlaybackHub
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Typeface
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.drishyopot.app.R
import com.drishyopot.app.core.Haptics
import com.drishyopot.app.diagnostics.DiagnosticLog
import com.drishyopot.app.player.GestureMath
import com.drishyopot.app.player.PlaybackStats
import com.drishyopot.app.player.PlayerPrefs
import com.drishyopot.app.settings.PrefKeys
import com.drishyopot.app.player.TrackOptions
import com.drishyopot.app.settings.SettingsStore
import com.drishyopot.app.yt.YoutubeResolver
import com.drishyopot.app.ui.cast.CastDialog
import com.drishyopot.app.ui.download.DownloadFlow
import com.drishyopot.app.ui.cast.CastPanel
import kotlinx.coroutines.delay

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
fun PlayerScreen(hub: PlaybackHub, store: SettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    val player = hub.player
    val prefs = hub.prefs
    val stats = hub.stats
    val item = hub.currentItem
    val url = item?.mediaId ?: ""
    val title = item?.mediaMetadata?.title?.toString() ?: url
    val pipAvailable = remember { PipSupport.isSupported(context) && store.bool(PrefKeys.ENABLE_PIP) }
    val inPip = hub.inPip
    val ytId = hub.youtubeIdOf(item)
    val yt = ytId?.let { hub.resolvedVideos[it] }

    var playbackState by remember { mutableIntStateOf(player.playbackState) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var positionMs by remember { mutableLongStateOf(player.currentPosition) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isLive by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(player.playbackParameters.speed) }
    var showQueue by remember { mutableStateOf(false) }
    var showCast by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    val downloads = remember { (context.applicationContext as com.drishyopot.app.DrishyopotApp).downloads }
    val cast = remember { (context.applicationContext as com.drishyopot.app.DrishyopotApp).cast }
    val castState = cast.state

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Screen visibility drives PiP auto-enter and the mini player.
    DisposableEffect(Unit) {
        hub.miniVisible = false
        hub.playerScreenVisible = true
        onDispose {
            hub.playerScreenVisible = false
            if (hub.player.currentMediaItem != null && hub.player.playbackState != Player.STATE_IDLE && !hub.inPip) {
                if (store.bool(PrefKeys.ENABLE_IN_APP_MINI_PLAYER)) hub.miniVisible = true
                else if (!store.bool(PrefKeys.ENABLE_BACKGROUND_PLAY)) hub.player.pause()
            }
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<PlayerDialog?>(null) }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    var holding2x by remember { mutableStateOf(false) }
    var speedBeforeHold by remember { mutableStateOf(1f) }
    var resizeMode by remember {
        mutableIntStateOf(if (store.bool(PrefKeys.REMEMBER_RESIZE_MODE)) prefs.resizeMode else AspectRatioFrameLayout.RESIZE_MODE_FIT)
    }
    var subStyle by remember { mutableStateOf(prefs.subtitleStyle) }
    var qualityHeight by remember { mutableIntStateOf(if (store.bool(PrefKeys.REMEMBER_QUALITY)) prefs.qualityHeight else 0) }

    fun showHud(text: String) { hud = text; hudTick++ }
    val fullscreenState = rememberUpdatedState(fullscreen)
    val lockedState = rememberUpdatedState(locked)

    // Player events
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) errorText = null
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onTracksChanged(t: Tracks) { tracks = t }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { errorText = null; tracks = player.currentTracks }
            override fun onPlayerError(error: PlaybackException) {
                DiagnosticLog.log("Player", "Playback error ${error.errorCodeName}", error)
                errorText = error.errorCodeName
            }
            override fun onPlaybackParametersChanged(p: androidx.media3.common.PlaybackParameters) { speed = p.speed }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Progress polling
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition
            durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            isLive = player.isCurrentMediaItemLive
            delay(250)
        }
    }

    // Auto-hide controls
    LaunchedEffect(controlsVisible, isPlaying, interactions, locked) {
        if (controlsVisible && isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }
    LaunchedEffect(hudTick) { delay(900); hud = null }
    val hubMessage = hub.message
    LaunchedEffect(hubMessage) { hubMessage?.let { hud = it; hudTick++ } }
    val skipEvent = hub.sponsor.skipEvent
    LaunchedEffect(skipEvent?.id) {
        skipEvent?.let { hud = context.getString(R.string.sb_skipped, context.getString(it.category.label)); hudTick++ }
    }
    LaunchedEffect(isPlaying, playbackState) { if (!isPlaying) controlsVisible = true }

    // Fullscreen: orientation + system bars. Rotating to landscape enters fullscreen.
    val orientation = LocalConfiguration.current.orientation
    LaunchedEffect(orientation) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE && !fullscreen) fullscreen = true
    }
    var firstFullscreenPass by remember { mutableStateOf(true) }
    LaunchedEffect(fullscreen) {
        val act = activity ?: return@LaunchedEffect
        if (!fullscreen && firstFullscreenPass) { firstFullscreenPass = false; return@LaunchedEffect }
        firstFullscreenPass = false
        val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (fullscreen) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            delay(1200)
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.let {
                WindowCompat.getInsetsController(it.window, it.window.decorView).show(WindowInsetsCompat.Type.systemBars())
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val lp = it.window.attributes
                lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.window.attributes = lp
            }
        }
    }
    BackHandler(enabled = fullscreen) { fullscreen = false }
    BackHandler(enabled = locked) { /* ignore while locked */ }

    fun togglePlay() {
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.isPlaying) player.pause() else player.play()
        Haptics.tick(context, store.int(PrefKeys.HAPTIC_STRENGTH))
    }

    val surface: @Composable (Modifier) -> Unit = { mod ->
        Box(mod.background(Color.Black)) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                onRelease = { it.player = null },
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = resizeMode
                    view.subtitleView?.apply {
                        setApplyEmbeddedStyles(false)
                        setApplyEmbeddedFontSizes(false)
                        setFractionalTextSize(subStyle.textSize)
                        setStyle(
                            CaptionStyleCompat(
                                subStyle.color, subStyle.backgroundColor(), android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK,
                                if (subStyle.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT,
                            ),
                        )
                        translationY = subStyle.offsetY * view.height
                    }
                },
            )

            // Gesture layer
            Box(
                Modifier.matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                val key = if (fullscreenState.value) PrefKeys.GESTURE_TAP_FULLSCREEN else PrefKeys.GESTURE_TAP_WINDOWED
                                if (lockedState.value || store.bool(key)) controlsVisible = !controlsVisible
                            },
                            onDoubleTap = { offset ->
                                if (lockedState.value) return@detectTapGestures
                                val key = if (fullscreenState.value) PrefKeys.GESTURE_DOUBLE_TAP_FULLSCREEN else PrefKeys.GESTURE_DOUBLE_TAP_WINDOWED
                                if (!store.bool(key)) return@detectTapGestures
                                when (GestureMath.doubleTapZone(offset.x, size.width.toFloat())) {
                                    -1 -> { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); showHud("−10 s") }
                                    1 -> { player.seekTo(player.currentPosition + 10_000); showHud("+10 s") }
                                    else -> togglePlay()
                                }
                            },
                            onLongPress = {
                                if (lockedState.value) return@detectTapGestures
                                val key = if (fullscreenState.value) PrefKeys.GESTURE_LONG_PRESS_FULLSCREEN else PrefKeys.GESTURE_LONG_PRESS_WINDOWED
                                if (store.bool(key) && player.isPlaying) {
                                    speedBeforeHold = player.playbackParameters.speed
                                    holding2x = true
                                    player.setPlaybackSpeed(2f)
                                    Haptics.tick(context, store.int(PrefKeys.HAPTIC_STRENGTH))
                                }
                            },
                            onPress = {
                                tryAwaitRelease()
                                if (holding2x) {
                                    holding2x = false
                                    player.setPlaybackSpeed(speedBeforeHold)
                                }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        var mode = GestureMath.Mode.NONE
                        var startX = 0f
                        var total = Offset.Zero
                        var startPos = 0L
                        var level = 0f
                        var seekTarget = 0L
                        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        fun enabled(m: GestureMath.Mode): Boolean {
                            val fs = fullscreenState.value
                            val key = when (m) {
                                GestureMath.Mode.SEEK -> if (fs) PrefKeys.GESTURE_SEEK_FULLSCREEN else PrefKeys.GESTURE_SEEK_WINDOWED
                                GestureMath.Mode.BRIGHTNESS -> if (fs) PrefKeys.GESTURE_BRIGHTNESS_FULLSCREEN else PrefKeys.GESTURE_BRIGHTNESS_WINDOWED
                                GestureMath.Mode.VOLUME -> if (fs) PrefKeys.GESTURE_VOLUME_FULLSCREEN else PrefKeys.GESTURE_VOLUME_WINDOWED
                                GestureMath.Mode.FULLSCREEN -> if (fs) PrefKeys.GESTURE_FULLSCREEN_FULLSCREEN else PrefKeys.GESTURE_FULLSCREEN_WINDOWED
                                GestureMath.Mode.NONE -> return false
                            }
                            return store.bool(key)
                        }
                        fun finish() {
                            if (mode == GestureMath.Mode.SEEK && enabled(mode)) player.seekTo(seekTarget)
                            if (mode == GestureMath.Mode.FULLSCREEN && enabled(mode)) {
                                val intent = GestureMath.fullscreenIntent(total.y, size.height.toFloat())
                                if (intent > 0) fullscreen = true else if (intent < 0) fullscreen = false
                            }
                            mode = GestureMath.Mode.NONE
                        }
                        detectDragGestures(
                            onDragStart = { o ->
                                startX = o.x; total = Offset.Zero; mode = GestureMath.Mode.NONE
                                startPos = player.currentPosition
                            },
                            onDragEnd = { finish() },
                            onDragCancel = { mode = GestureMath.Mode.NONE },
                            onDrag = { change, drag ->
                                if (lockedState.value) return@detectDragGestures
                                total += drag
                                if (mode == GestureMath.Mode.NONE) {
                                    mode = GestureMath.classify(total.x, total.y, startX, size.width.toFloat())
                                    when (mode) {
                                        GestureMath.Mode.BRIGHTNESS -> level = currentBrightness(activity)
                                        GestureMath.Mode.VOLUME -> {
                                            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                            level = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                                        }
                                        else -> Unit
                                    }
                                }
                                if (!enabled(mode)) return@detectDragGestures
                                change.consume()
                                when (mode) {
                                    GestureMath.Mode.SEEK -> {
                                        seekTarget = GestureMath.seekTarget(startPos, total.x, size.width.toFloat(), durationMs)
                                        showHud(formatTime(seekTarget))
                                    }
                                    GestureMath.Mode.BRIGHTNESS -> {
                                        level = GestureMath.adjustLevel(level, drag.y, size.height.toFloat())
                                        activity?.let {
                                            val lp = it.window.attributes
                                            lp.screenBrightness = level.coerceAtLeast(0.01f)
                                            it.window.attributes = lp
                                        }
                                        showHud("☀ ${(level * 100).toInt()}%")
                                    }
                                    GestureMath.Mode.VOLUME -> {
                                        level = GestureMath.adjustLevel(level, drag.y, size.height.toFloat())
                                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(level * max), 0)
                                        showHud("🔊 ${(level * 100).toInt()}%")
                                    }
                                    else -> Unit
                                }
                            },
                        )
                    },
            )

            // Overlays
            if (playbackState == Player.STATE_BUFFERING && errorText == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            hud?.let {
                Text(
                    it, color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                        .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.medium).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (holding2x) {
                Text(
                    stringResource(R.string.speed_hold_2x), color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.medium).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            errorText?.let {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.player_error), color = Color.White)
                    Text(it, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { errorText = null; stats.markPrepare(); player.prepare() }, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            hub.sponsor.pending?.let { pendingSkip ->
                if (!inPip) {
                    SkipChip(
                        pending = pendingSkip,
                        onSkip = { hub.sponsor.skipPendingNow() },
                        onCancel = { hub.sponsor.cancelPending() },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 72.dp),
                    )
                }
            }

            if (castState.casting || castState.connecting) {
                CastPanel(cast = cast, title = title, onBack = { if (fullscreen) fullscreen = false else onBack() })
            }

            AnimatedVisibility(visible = controlsVisible && !inPip && !castState.casting && !castState.connecting, enter = fadeIn(), exit = fadeOut()) {
                PlayerControls(
                    model = ControlsModel(
                        title = title,
                        isPlaying = isPlaying,
                        ended = playbackState == Player.STATE_ENDED,
                        isLive = isLive,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        bufferedMs = player.bufferedPosition,
                        fullscreen = fullscreen,
                        locked = locked,
                        subtitlesOn = !player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) &&
                            TrackOptions.text(tracks).any { it.selected },
                        hasSubtitles = TrackOptions.text(tracks).isNotEmpty(),
                        hasPrevious = player.hasPreviousMediaItem(),
                        hasNext = player.hasNextMediaItem(),
                        endMode = hub.endMode,
                        audioOnly = hub.audioOnly,
                        pipAvailable = pipAvailable,
                        segments = hub.sponsor.visibleSegments(),
                        castActive = castState.casting,
                    ),
                    onBack = { if (fullscreen) fullscreen = false else onBack() },
                    onPlayPause = { togglePlay(); interactions++ },
                    onSeekPreview = { positionMs = it },
                    onSeekCommit = { player.seekTo(it); interactions++ },
                    onToggleFullscreen = { fullscreen = !fullscreen },
                    onToggleLock = { locked = !locked; Haptics.tick(context, store.int(PrefKeys.HAPTIC_STRENGTH)) },
                    onToggleSubtitles = {
                        val opts = TrackOptions.text(tracks)
                        if (opts.any { it.selected }) TrackOptions.disableText(player)
                        else opts.firstOrNull()?.let { TrackOptions.selectTrack(player, it) }
                    },
                    onPrevious = { player.seekToPreviousMediaItem() },
                    onNext = { player.seekToNextMediaItem() },
                    onCycleEndMode = { showHud(context.getString(endModeLabel(hub.cycleEndMode()))) },
                    onOpenDialog = {
                        when (it) {
                            PlayerDialog.AUDIO_ONLY -> hub.updateAudioOnly(!hub.audioOnly)
                            PlayerDialog.PIP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                activity?.enterPictureInPictureMode(PipSupport.params(context, hub, false))
                            }
                            PlayerDialog.SHARE -> {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, title)
                                    putExtra(Intent.EXTRA_TEXT, url)
                                }
                                context.startActivity(Intent.createChooser(send, null))
                            }
                            PlayerDialog.CLOSE_PLAYER -> { hub.closePlayer(); onBack() }
                            PlayerDialog.QUEUE -> showQueue = true
                            PlayerDialog.CAST -> showCast = true
                            PlayerDialog.DOWNLOAD -> showDownload = true
                            else -> dialog = it
                        }
                    },
                    onInteract = { interactions++ },
                )
            }
        }
    }

    if (fullscreen || inPip) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { surface(Modifier.fillMaxSize()) }
    } else {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding()) {
                surface(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            }
            VideoDetails(
                title = title,
                url = url,
                videoId = ytId,
                video = ytId?.let { hub.resolvedVideos[it] },
                store = store,
            )
        }
    }

    // Dialogs
    when (dialog) {
        PlayerDialog.QUALITY -> {
            if (ytId != null && yt != null && !yt.isLive) {
                val current = hub.choiceFor(ytId).height
                ChoiceDialog(
                    stringResource(R.string.quality),
                    buildList {
                        add(ChoiceItem(stringResource(R.string.player_quality_auto), current == 0) { hub.setYoutubeQuality(ytId, 0) })
                        YoutubeResolver.availableHeights(yt).forEach { h ->
                            add(ChoiceItem("${h}p", current == h) { hub.setYoutubeQuality(ytId, h) })
                        }
                    },
                ) { dialog = null }
            } else {
                val heights = TrackOptions.videoHeights(tracks)
                ChoiceDialog(
                    stringResource(R.string.quality),
                    buildList {
                        add(ChoiceItem(stringResource(R.string.player_quality_auto), qualityHeight == 0) {
                            qualityHeight = 0; prefs.qualityHeight = 0; TrackOptions.applyQuality(player, 0)
                        })
                        heights.forEach { h ->
                            add(ChoiceItem("${h}p", qualityHeight == h) {
                                qualityHeight = h; prefs.qualityHeight = h; TrackOptions.applyQuality(player, h)
                            })
                        }
                    },
                ) { dialog = null }
            }
        }
        PlayerDialog.SPEED -> ChoiceDialog(
            stringResource(R.string.playback_speed),
            SPEEDS.map { s -> ChoiceItem("${s}×", kotlin.math.abs(speed - s) < 0.01f) { player.setPlaybackSpeed(s); prefs.speed = s } },
        ) { dialog = null }
        PlayerDialog.AUDIO -> {
            if (ytId != null && yt != null && !yt.isLive && YoutubeResolver.audioTracks(yt).size > 1) {
                val tracksYt = YoutubeResolver.audioTracks(yt)
                val selected = hub.choiceFor(ytId).audioTrackId ?: (tracksYt.first().trackId ?: "default")
                ChoiceDialog(
                    stringResource(R.string.audio_track),
                    tracksYt.map { a ->
                        val key = a.trackId ?: "default"
                        ChoiceItem("${a.label} · ${a.bitrate / 1000} kbps", key == selected) { hub.setYoutubeAudio(ytId, a.trackId) }
                    },
                ) { dialog = null }
            } else {
                val opts = TrackOptions.audio(tracks)
                ChoiceDialog(
                    stringResource(R.string.audio_track),
                    if (opts.isEmpty()) listOf(ChoiceItem(stringResource(R.string.player_audio_default), true) {})
                    else opts.map { o -> ChoiceItem(o.label, o.selected) { TrackOptions.selectTrack(player, o) } },
                ) { dialog = null }
            }
        }
        PlayerDialog.SUBTITLES -> {
            val opts = TrackOptions.text(tracks)
            val off = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) || opts.none { it.selected }
            ChoiceDialog(
                stringResource(R.string.subtitles),
                buildList {
                    add(ChoiceItem(stringResource(R.string.no_subtitles), off) { TrackOptions.disableText(player) })
                    opts.forEach { o -> add(ChoiceItem(o.label, o.selected && !off) { TrackOptions.selectTrack(player, o) }) }
                },
            ) { dialog = null }
        }
        PlayerDialog.RESIZE -> {
            val modes = listOf(
                AspectRatioFrameLayout.RESIZE_MODE_FIT to R.string.resize_fit,
                AspectRatioFrameLayout.RESIZE_MODE_FILL to R.string.resize_fill,
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM to R.string.resize_zoom,
                AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to R.string.resize_fixed_width,
                AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT to R.string.resize_fixed_height,
            )
            ChoiceDialog(
                stringResource(R.string.resize_mode),
                modes.map { (m, label) -> ChoiceItem(stringResource(label), resizeMode == m) { resizeMode = m; prefs.resizeMode = m } },
            ) { dialog = null }
        }
        PlayerDialog.SUBTITLE_STYLE -> SubtitleStyleDialog(
            initial = subStyle,
            onChange = { subStyle = it; prefs.subtitleStyle = it },
            onDismiss = { dialog = null },
        )
        PlayerDialog.SPONSOR_SEGMENTS -> SegmentsDialog(
            segments = hub.sponsor.visibleSegments(),
            status = hub.sponsor.status,
            onJumpToStart = { player.seekTo(it.startMs) },
            onSkip = { hub.sponsor.skipSegment(it) },
            onRetry = { hub.sponsor.retry() },
            onDismiss = { dialog = null },
        )
        PlayerDialog.INFO -> {
            val unavailable = stringResource(R.string.info_unavailable)
            val v = player.videoFormat
            val a = player.audioFormat
            val rows = listOf(
                stringResource(R.string.player_info_title) to title,
                stringResource(R.string.player_info_id) to url,
                stringResource(R.string.player_info_type) to stringResource(if (isLive) R.string.player_live else R.string.info_type_vod),
                stringResource(R.string.player_info_duration) to if (durationMs > 0) formatTime(durationMs) else unavailable,
                stringResource(R.string.info_resolution) to (v?.let { "${it.width}×${it.height}" } ?: unavailable),
                stringResource(R.string.info_video_codec) to (v?.codecs ?: v?.sampleMimeType ?: unavailable),
                stringResource(R.string.info_bitrate) to (v?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" } ?: unavailable),
                stringResource(R.string.info_nominal_fps) to (v?.frameRate?.takeIf { it > 0 }?.let { "%.2f".format(it) } ?: unavailable),
                stringResource(R.string.info_rendered_fps) to "%.1f".format(stats.renderedFps()),
                stringResource(R.string.info_video_decoder) to (stats.videoDecoder ?: unavailable),
                stringResource(R.string.info_audio_codec) to (a?.codecs ?: a?.sampleMimeType ?: unavailable),
                stringResource(R.string.info_audio_bitrate) to (a?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" } ?: unavailable),
                stringResource(R.string.info_sample_rate) to (a?.sampleRate?.takeIf { it > 0 }?.let { "$it Hz" } ?: unavailable),
                stringResource(R.string.info_channels) to (a?.channelCount?.takeIf { it > 0 }?.toString() ?: unavailable),
                stringResource(R.string.info_audio_decoder) to (stats.audioDecoder ?: unavailable),
                stringResource(R.string.info_dropped_frames) to "${stats.droppedFrames} / ${stats.totalFrames()}",
                stringResource(R.string.info_playback_speed) to "${speed}×",
                stringResource(R.string.info_buffer_state) to "$playbackState · " + stringResource(R.string.info_buffer_count, stats.buffersPerMinute()),
                stringResource(R.string.info_state_cast) to (castState.deviceName?.takeIf { castState.casting } ?: unavailable),
                stringResource(R.string.info_first_frame) to (stats.firstFrameMs.takeIf { it >= 0 }?.let { "$it ms" } ?: unavailable),
            )
            InfoDialog(rows) { dialog = null }
        }
        else -> Unit
    }
    if (showQueue) QueueSheet(hub) { showQueue = false }
    if (showCast) CastDialog(cast) { showCast = false }
    if (showDownload) {
        if (ytId != null) {
            DownloadFlow(com.drishyopot.app.yt.YtLink.Video(ytId), downloads) { showDownload = false }
        } else {
            LaunchedEffect(Unit) {
                android.widget.Toast.makeText(context, R.string.dl_only_youtube, android.widget.Toast.LENGTH_SHORT).show()
                showDownload = false
            }
        }
    }
}

private fun currentBrightness(activity: Activity?): Float {
    val act = activity ?: return 0.5f
    val b = act.window.attributes.screenBrightness
    if (b >= 0f) return b
    return try {
        Settings.System.getInt(act.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (_: Settings.SettingNotFoundException) {
        0.5f
    }
}
