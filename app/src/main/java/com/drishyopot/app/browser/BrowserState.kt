package com.drishyopot.app.browser

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.drishyopot.app.R
import com.drishyopot.app.diagnostics.DiagnosticLog
import com.drishyopot.app.engine.EngineAssets
import com.drishyopot.app.settings.PrefKeys
import com.drishyopot.app.settings.SettingsStore
import com.drishyopot.app.yt.YoutubeUrls
import org.json.JSONArray
import java.io.ByteArrayInputStream

/** One browser tab. The WebView is created lazily and survives leaving the browser screen. */
class BrowserTab(val id: Long, initialUrl: String) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var canGoBack by mutableStateOf(false)
    /** Bumped when the WebView is recreated so the UI re-attaches it. */
    var generation by mutableIntStateOf(0)
    /** Set when the page itself could not be loaded (offline, DNS, TLS ...). */
    var error by mutableStateOf<String?>(null)
    internal var webView: WebView? = null
    internal var contextWrapper: MutableContextWrapper? = null
    internal var speech: WebSpeechBridge? = null
}

/** Events the browser raises to the app. */
interface BrowserHost {
    fun openNative(link: com.drishyopot.app.yt.YtLink)
    fun openExternal(url: String)
    fun showImage(url: String)
    /** An entry of the menu injected into YouTube's "You" page was tapped. */
    fun menu(id: String)
    /** Ask for the microphone (for YouTube's own voice search); [onResult] gets whether it was granted. */
    fun requestAudioPermission(onResult: (Boolean) -> Unit)
}

class BrowserState(private val app: Application, private val settings: SettingsStore) {
    private val prefs = app.getSharedPreferences("drishyopot_browser", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private var nextId = 1L

    val tabs = mutableStateListOf<BrowserTab>()
    var currentIndex by mutableIntStateOf(0)
        private set
    var host: BrowserHost? = null

    val current: BrowserTab get() = tabs[currentIndex]

    init { restore() }

    // ---- Tabs ------------------------------------------------------------------------------

    fun newTab(url: String = BrowserInput.HOME, select: Boolean = true) {
        tabs += BrowserTab(nextId++, url)
        if (select) currentIndex = tabs.lastIndex
        persist()
        applySettingsToPages()
    }

    fun select(index: Int) { if (index in tabs.indices) { currentIndex = index; persist(); applySettingsToPages() } }

    fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val tab = tabs.removeAt(index)
        tab.webView?.let { it.stopLoading(); it.destroy() }
        tab.webView = null
        tab.speech?.destroy(); tab.speech = null
        if (tabs.isEmpty()) newTab(select = true)
        currentIndex = currentIndex.coerceIn(0, tabs.lastIndex)
        persist()
        applySettingsToPages()
    }

    /** Loads [url] in the current tab. */
    fun open(url: String) {
        val tab = current
        tab.url = url
        tab.webView?.loadUrl(url)
        persist()
    }

    fun applySettingsToPages() {
        tabs.forEach { it.webView?.evaluateJavascript(configJs(), null) }
    }

    private fun persist() {
        val arr = JSONArray()
        tabs.forEach { arr.put(it.url) }
        prefs.edit().putString("tabs", arr.toString()).putInt("current", currentIndex).apply()
    }

    private fun restore() {
        try {
            val arr = JSONArray(prefs.getString("tabs", null) ?: "[]")
            for (i in 0 until arr.length()) tabs += BrowserTab(nextId++, arr.getString(i))
        } catch (_: Exception) { tabs.clear() }
        if (tabs.isEmpty()) tabs += BrowserTab(nextId++, BrowserInput.HOME)
        currentIndex = prefs.getInt("current", 0).coerceIn(0, tabs.lastIndex)
    }

    // ---- WebView ---------------------------------------------------------------------------

    /** Settings and the (localised) labels the page script needs; sent on every page start and change. */
    private fun configJs(): String {
        val labels = org.json.JSONObject()
            .put("title", app.getString(R.string.menu_title))
            .put("voice", app.getString(R.string.voice_search))
            .put("address", app.getString(R.string.browser_address))
            .put("openlink", app.getString(R.string.menu_open_link))
            .put("tabs", app.getString(R.string.browser_tabs))
            .put("newtab", app.getString(R.string.browser_new_tab))
            .put("reload", app.getString(R.string.browser_reload))
            .put("settings", app.getString(R.string.home_settings))
            .put("downloads", app.getString(R.string.downloads))
            .put("queue", app.getString(R.string.queue))
            .put("account", app.getString(R.string.settings_account))
            .put("engine", app.getString(R.string.settings_engine))
            .put("about", app.getString(R.string.about))
            .put("copy", app.getString(R.string.browser_copy_link))
            .put("share", app.getString(R.string.share))
            .put("external", app.getString(R.string.browser_open_external))
        val config = org.json.JSONObject()
            .put("hideShorts", settings.bool(PrefKeys.ENABLE_HIDE_SHORTS))
            .put("tabs", tabs.size)
            .put("labels", labels)
        return "window.__drishyopotConfig=$config;window.__drishyopotApply&&window.__drishyopotApply();"
    }

    /** Returns the tab's WebView, creating it on first use, bound to [activityContext] while shown. */
    fun webViewFor(tab: BrowserTab, activityContext: Context): WebView {
        tab.contextWrapper?.baseContext = activityContext
        tab.webView?.let { return it }
        val wrapper = MutableContextWrapper(activityContext)
        tab.contextWrapper = wrapper
        val view = createWebView(wrapper, tab)
        tab.webView = view
        view.loadUrl(tab.url)
        return view
    }

    /** Detach from the activity so it is not leaked while the browser is not on screen. */
    fun release(tab: BrowserTab) { tab.contextWrapper?.baseContext = app }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun createWebView(context: Context, tab: BrowserTab): WebView {
        // The speech polyfill goes first so a problem in the page script can never take voice search down.
        val script = EngineAssets.text(app, "speech.js") + "\n;\n" + EngineAssets.text(app, "inject.js")
        val webView = WebView(context)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Present as plain Chrome so Google sign-in does not reject the embedded view.
            userAgentString = userAgentString.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+ "), "")
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun openVideo(url: String) {
                main.post {
                    val link = YoutubeUrls.parse(url) ?: return@post
                    host?.openNative(link)
                }
            }

            @JavascriptInterface
            fun menu(id: String) {
                main.post { host?.menu(id) }
            }
        }, "Drishyopot")

        val speech = WebSpeechBridge(app, { tab.webView }) { onResult ->
            val h = host
            if (h != null) h.requestAudioPermission(onResult) else onResult(false)
        }
        tab.speech?.destroy()
        tab.speech = speech
        webView.addJavascriptInterface(speech, "DrishyopotSpeech")

        val documentStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (documentStart) {
            try {
                WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("https://*.youtube.com"))
            } catch (e: Exception) {
                DiagnosticLog.log("Browser", "Document-start script unavailable", e)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                return when (val decision = UrlPolicy.decide(request.url.toString())) {
                    is NavDecision.Native -> { host?.openNative(decision.link); true }
                    NavDecision.External -> { host?.openExternal(request.url.toString()); true }
                    NavDecision.Allow -> false
                }
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (AdBlockRules.shouldBlock(request.url.toString())) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                tab.url = url
                tab.error = null
                if (!documentStart && url.contains("youtube.com")) view.evaluateJavascript(script, null)
                view.evaluateJavascript(configJs(), null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                tab.url = url
                tab.canGoBack = view.canGoBack()
                view.evaluateJavascript(configJs(), null)
                persist()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                if (request.isForMainFrame) {
                    tab.error = "${error.description} (${error.errorCode})"
                    DiagnosticLog.log("Browser", "Page failed: ${error.description} (${error.errorCode}) ${request.url.host}")
                }
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                tab.canGoBack = view.canGoBack()
            }

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                DiagnosticLog.log("Browser", "WebView render process gone (crash=${detail.didCrash()})")
                tab.webView = null
                view.destroy()
                tab.generation++
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { tab.progress = newProgress }
            override fun onReceivedTitle(view: WebView, title: String?) { tab.title = title.orEmpty() }
            override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                // Only the microphone, only for YouTube pages.
                val host = request.origin.host.orEmpty()
                val youtube = host == "youtube.com" || host.endsWith(".youtube.com")
                val micOnly = request.resources.isNotEmpty() &&
                    request.resources.all { it == android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                val h = this@BrowserState.host
                if (youtube && micOnly && h != null) {
                    h.requestAudioPermission { ok -> if (ok) request.grant(request.resources) else request.deny() }
                } else request.deny()
            }
        }

        webView.setOnLongClickListener {
            val hit = webView.hitTestResult
            val isImage = hit.type == WebView.HitTestResult.IMAGE_TYPE ||
                hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            val src = hit.extra
            if (isImage && !src.isNullOrBlank() && src.startsWith("http")) {
                host?.showImage(src)
                true
            } else false
        }
        return webView
    }
}
