package com.drishyopot.app.engine

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.drishyopot.app.diagnostics.DiagnosticLog
import com.drishyopot.app.settings.PrefKeys
import com.drishyopot.app.settings.SettingsStore
import com.drishyopot.engine.api.PoTokenBridge
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Produces PO tokens by running YouTube's BotGuard challenge in a hidden WebView whose base URL is
 * youtube.com (see tools/potoken). Calls block the calling worker thread, never the main thread.
 */
class PoTokenService(private val app: Application, private val settings: SettingsStore) : PoTokenBridge {
    private val main = Handler(Looper.getMainLooper())
    private val callLock = Any()
    private val counter = AtomicInteger()
    private val waiters = ConcurrentHashMap<String, LinkedBlockingQueue<Array<String>>>()
    private var webView: WebView? = null
    @Volatile private var ready = CountDownLatch(1)
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")

    override fun webClientPoToken(videoId: String?): Array<String>? {
        if (!settings.bool(PrefKeys.USE_PO_TOKEN) || videoId == null || !idPattern.matches(videoId)) return null
        if (Looper.myLooper() == Looper.getMainLooper()) return null // never block the UI thread
        synchronized(callLock) {
            val id = counter.incrementAndGet().toString()
            val box = LinkedBlockingQueue<Array<String>>(1)
            waiters[id] = box
            try {
                main.post { ensureWebView() }
                if (!ready.await(READY_TIMEOUT_S, TimeUnit.SECONDS)) {
                    DiagnosticLog.log("PoToken", "Page was not ready in time")
                    resetWebView()
                    return null
                }
                main.post { webView?.evaluateJavascript("window.__po('$id','$videoId')", null) }
                val result = box.poll(RESULT_TIMEOUT_S, TimeUnit.SECONDS)
                if (result == null) DiagnosticLog.log("PoToken", "Timed out waiting for a token")
                return result?.takeIf { it.size == 3 && it[0].isNotEmpty() && it[1].isNotEmpty() }
            } catch (_: InterruptedException) {
                return null
            } finally {
                waiters.remove(id)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun ensureWebView() {
        if (webView != null) return
        ready = CountDownLatch(1)
        val view = WebView(app)
        with(view.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = userAgentString.replace("; wv", "")
        }
        view.addJavascriptInterface(Bridge(), "Android")
        view.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                DiagnosticLog.log("PoToken", "WebView process gone")
                resetWebView()
                return true
            }
        }
        webView = view
        val html = try { EngineAssets.text(app, "potoken.html") } catch (e: Exception) {
            DiagnosticLog.log("PoToken", "No page available", e)
            return
        }
        view.loadDataWithBaseURL("https://www.youtube.com/", html, "text/html", "utf-8", null)
    }

    private fun resetWebView() {
        main.post {
            webView?.let { it.stopLoading(); it.destroy() }
            webView = null
            ready = CountDownLatch(1)
        }
    }

    private inner class Bridge {
        @JavascriptInterface fun onReady() { ready.countDown() }

        @JavascriptInterface fun onResult(callId: String, json: String) {
            try {
                val o = JSONObject(json)
                waiters[callId]?.offer(
                    arrayOf(
                        o.optString("visitorData"),
                        o.optString("playerRequestPoToken"),
                        o.optString("streamingDataPoToken"),
                    ),
                )
            } catch (e: Exception) {
                DiagnosticLog.log("PoToken", "Bad result", e)
            }
        }

        @JavascriptInterface fun onError(callId: String, message: String) {
            DiagnosticLog.log("PoToken", "Generation failed: $message")
            waiters[callId]?.offer(arrayOf("", "", ""))
        }
    }

    companion object {
        private const val READY_TIMEOUT_S = 20L
        private const val RESULT_TIMEOUT_S = 40L
    }
}
