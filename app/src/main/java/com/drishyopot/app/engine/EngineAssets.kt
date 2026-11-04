package com.drishyopot.app.engine

import android.content.Context

/** Page scripts that can be replaced by an engine update (falling back to the copies in the APK). */
object EngineAssets {
    private val bundledPath = mapOf(
        "potoken.html" to "engine/potoken.html",
        "inject.js" to "browser/inject.js",
        "speech.js" to "browser/speech.js",
    )

    fun text(context: Context, name: String): String {
        EngineHost.updatedAsset(name)?.let { file ->
            try { return file.readText() } catch (_: Exception) { /* use the bundled copy */ }
        }
        val path = bundledPath[name] ?: error("Unknown asset $name")
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }
}
