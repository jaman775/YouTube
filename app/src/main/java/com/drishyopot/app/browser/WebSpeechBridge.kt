package com.drishyopot.app.browser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.ContextCompat
import com.drishyopot.app.diagnostics.DiagnosticLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backs the page's SpeechRecognition (see assets/browser/speech.js) with Android's SpeechRecognizer.
 * Exposed to pages as `DrishyopotSpeech`; one instance per WebView.
 */
class WebSpeechBridge(
    private val app: Context,
    private val webView: () -> WebView?,
    /** Asks for the microphone permission; calls back with the result on the main thread. */
    private val requestPermission: (onResult: (Boolean) -> Unit) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var interim = false
    private var continuous = false
    private var maxAlternatives = 1
    private var lang = "en-US"
    private var gotSound = false
    private var gotSpeech = false
    private var running = false

    @JavascriptInterface
    fun start(lang: String, interimResults: Boolean, continuous: Boolean, maxAlternatives: Int) {
        main.post {
            this.lang = lang; this.interim = interimResults; this.continuous = continuous; this.maxAlternatives = maxAlternatives
            if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
            else requestPermission { granted -> if (granted) begin() else fail("not-allowed") }
        }
    }

    @JavascriptInterface fun stop() { main.post { recognizer?.stopListening() } }
    @JavascriptInterface fun abort() { main.post { recognizer?.cancel(); if (running) finish() } }

    fun destroy() { main.post { recognizer?.destroy(); recognizer = null; running = false } }

    private fun begin() {
        if (!SpeechRecognizer.isRecognitionAvailable(app)) { fail("service-not-allowed"); return }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(app)
        recognizer = r
        gotSound = false; gotSpeech = false; running = true
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, interim)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, maxAlternatives.coerceIn(1, 5))
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
        }
        try { r.startListening(intent) } catch (e: Exception) {
            DiagnosticLog.log("Speech", "startListening failed", e)
            fail("audio-capture")
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { emit("start"); emit("audiostart") }
        override fun onBeginningOfSpeech() {
            if (!gotSound) { gotSound = true; emit("soundstart") }
            if (!gotSpeech) { gotSpeech = true; emit("speechstart") }
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            if (gotSpeech) emit("speechend")
            if (gotSound) emit("soundend")
            emit("audioend")
        }
        override fun onError(error: Int) {
            val code = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
                SpeechRecognizer.ERROR_AUDIO -> "audio-capture"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> "network"
                SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "aborted"
                else -> "aborted"
            }
            DiagnosticLog.log("Speech", "Recognizer error $error -> $code")
            fail(code)
        }
        override fun onResults(results: Bundle?) {
            sendResult(results, final = true)
            if (continuous && running) {
                // The platform recogniser stops after each phrase; restart to keep "continuous" semantics.
                main.post { if (running) begin() }
            } else finish()
        }
        override fun onPartialResults(partialResults: Bundle?) { if (interim) sendResult(partialResults, final = false) }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun sendResult(bundle: Bundle?, final: Boolean) {
        val texts = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        if (texts.isEmpty()) return
        val scores = bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        val alts = JSONArray()
        texts.take(maxAlternatives.coerceAtLeast(1)).forEachIndexed { i, t ->
            alts.put(JSONObject().put("t", t).put("c", (scores?.getOrNull(i)?.takeIf { it >= 0f }) ?: if (final) 0.9 else 0.5))
        }
        emit("result", JSONObject().put("alternatives", alts).put("isFinal", final))
    }

    private fun fail(code: String) {
        emit("error", JSONObject().put("error", code))
        finish()
    }

    private fun finish() {
        running = false
        emit("end")
        recognizer?.destroy()
        recognizer = null
    }

    private fun emit(type: String, extra: JSONObject = JSONObject()) {
        val json = extra.put("type", type).toString()
        webView()?.evaluateJavascript("window.__dpSpeech&&window.__dpSpeech.onEvent($json)", null)
    }
}
