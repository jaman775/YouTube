@file:OptIn(ExperimentalMaterial3Api::class)

package com.drishyopot.app.ui.browser

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drishyopot.app.R
import com.drishyopot.app.browser.BrowserHost
import com.drishyopot.app.browser.BrowserInput
import com.drishyopot.app.browser.BrowserState
import com.drishyopot.app.settings.SettingsStore
import com.drishyopot.app.yt.YtLink

@Composable
fun BrowserScreen(
    state: BrowserState,
    store: SettingsStore,
    onBack: () -> Unit,
    onOpenNative: (YtLink) -> Unit,
) {
    val context = LocalContext.current
    val tab = state.current
    var menuOpen by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var showAddress by remember { mutableStateOf(false) }
    var imageUrl by remember { mutableStateOf<String?>(null) }
    val revision by store.revision.collectAsStateWithLifecycle()

    // The browser raises events here while it is on screen.
    DisposableEffect(state) {
        val host = object : BrowserHost {
            override fun openNative(link: YtLink) = onOpenNative(link)
            override fun openExternal(url: String) {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: ActivityNotFoundException) { }
            }
            override fun showImage(url: String) { imageUrl = url }
        }
        state.host = host
        onDispose {
            if (state.host === host) state.host = null
            state.tabs.forEach { it.webView?.onPause(); state.release(it) }
        }
    }
    LaunchedEffect(revision) { state.applySettingsToPages() }

    BackHandler {
        val webView = tab.webView
        if (tab.canGoBack && webView != null) webView.goBack() else onBack()
    }

    // Voice search
    val voiceUnavailable = stringResource(R.string.voice_unavailable)
    val speakPrompt = stringResource(R.string.voice_prompt)
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) state.open(BrowserInput.searchUrl(text))
    }
    fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, speakPrompt)
        }
        try { voiceLauncher.launch(intent) }
        catch (_: ActivityNotFoundException) { Toast.makeText(context, voiceUnavailable, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            tab.title.ifBlank { tab.url },
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { showAddress = true },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            val webView = tab.webView
                            if (tab.canGoBack && webView != null) webView.goBack() else onBack()
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back)) }
                    },
                    actions = {
                        IconButton(onClick = { startVoiceSearch() }) { Icon(Icons.Filled.Mic, stringResource(R.string.voice_search)) }
                        IconButton(onClick = {
                            if (tab.progress < 100) tab.webView?.stopLoading() else tab.webView?.reload()
                        }) {
                            Icon(
                                if (tab.progress < 100) Icons.Filled.Close else Icons.Filled.Refresh,
                                stringResource(R.string.browser_reload),
                            )
                        }
                        IconButton(onClick = { showTabs = true }) {
                            BadgedBox(badge = { Badge { Text("${state.tabs.size}") } }) {
                                Icon(Icons.Filled.Tab, stringResource(R.string.browser_tabs))
                            }
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.browser_new_tab)) }, onClick = { menuOpen = false; state.newTab() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.browser_home)) }, onClick = { menuOpen = false; state.open(BrowserInput.HOME) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.browser_copy_link)) }, onClick = {
                                    menuOpen = false
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("url", tab.url))
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, onClick = {
                                    menuOpen = false
                                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, tab.url) }
                                    context.startActivity(Intent.createChooser(send, null))
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.browser_open_external)) }, onClick = {
                                    menuOpen = false
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tab.url))) } catch (_: ActivityNotFoundException) { }
                                })
                            }
                        }
                    },
                )
                if (tab.progress < 100) {
                    LinearProgressIndicator(progress = { tab.progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { ctx -> FrameLayout(ctx) },
            update = { frame ->
                val current = state.current
                current.generation // re-attach when the WebView was recreated
                val webView = state.webViewFor(current, context)
                webView.onResume()
                if (frame.childCount != 1 || frame.getChildAt(0) !== webView) {
                    frame.removeAllViews()
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    frame.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            },
            onRelease = { it.removeAllViews() },
        )
    }

    if (showTabs) {
        ModalBottomSheet(onDismissRequest = { showTabs = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.padding(bottom = 16.dp)) {
                state.tabs.forEachIndexed { index, t ->
                    ListItem(
                        modifier = Modifier.clickable { state.select(index); showTabs = false },
                        headlineContent = {
                            Text(
                                t.title.ifBlank { t.url }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (index == state.currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        supportingContent = { Text(t.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            IconButton(onClick = { state.closeTab(index) }) { Icon(Icons.Filled.Close, stringResource(R.string.browser_close_tab)) }
                        },
                    )
                }
                TextButton(onClick = { state.newTab(); showTabs = false }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Text(stringResource(R.string.browser_new_tab))
                }
            }
        }
    }

    if (showAddress) {
        var input by remember { mutableStateOf(tab.url) }
        AlertDialog(
            onDismissRequest = { showAddress = false },
            title = { Text(stringResource(R.string.browser_address)) },
            text = {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    label = { Text(stringResource(R.string.browser_address_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    BrowserInput.resolve(input)?.let { state.open(it) }
                    showAddress = false
                }) { Text(stringResource(R.string.browser_go)) }
            },
            dismissButton = { TextButton(onClick = { showAddress = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    imageUrl?.let { ImageViewer(it) { imageUrl = null } }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
