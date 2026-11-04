package com.drishyopot.app.engine

import android.app.Application
import android.os.Build
import android.os.SystemClock
import com.drishyopot.app.R
import com.drishyopot.app.diagnostics.DiagnosticLog
import com.drishyopot.app.settings.PrefKeys
import com.drishyopot.app.settings.SettingsStore
import com.drishyopot.engine.api.Engine
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

enum class UpdateStatus { UPDATED, UP_TO_DATE, NOT_CONFIGURED, UNSUPPORTED_DEVICE, INCOMPATIBLE, REJECTED, FAILED }

data class UpdateResult(val status: UpdateStatus, val detail: String = "")

/**
 * Downloads engine updates from the manifest URL the app was built with.
 *
 * Nothing is installed unless: the manifest's signature verifies against the public key built into the
 * app; the engine API matches; the sequence number is higher than what is installed; and every file's
 * SHA-256 matches the signed manifest. A new engine must also load and report a version before it
 * replaces the current one.
 */
class EngineUpdater(
    private val app: Application,
    private val settings: SettingsStore,
    private val store: EngineStore,
) {
    private val manifestUrl get() = app.getString(R.string.engine_manifest_url).trim()
    private val publicKey get() = app.getString(R.string.engine_public_key).trim()
    private var lastHealAt = 0L

    val configured: Boolean get() = manifestUrl.startsWith("https://") && publicKey.isNotEmpty()
    val supportedDevice: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    val lastCheckAt: Long get() = store.lastCheckAt
    val lastMessage: String get() = store.lastMessage

    /** Called on app start: checks at most every [AUTO_INTERVAL_MS] and only when the user allows it. */
    fun autoCheckIfDue() {
        if (!settings.bool(PrefKeys.AUTO_UPDATE_ENGINE) || !configured || !supportedDevice) return
        if (System.currentTimeMillis() - store.lastCheckAt < AUTO_INTERVAL_MS) return
        Thread({ checkBlocking() }, "engine-update").start()
    }

    /** Used when the engine fails: one try per 15 minutes. True when a newer engine is now active. */
    fun updateForHealing(): Boolean {
        if (!settings.bool(PrefKeys.AUTO_UPDATE_ENGINE) || !configured || !supportedDevice) return false
        val now = SystemClock.elapsedRealtime()
        if (lastHealAt != 0L && now - lastHealAt < HEAL_INTERVAL_MS) return false
        lastHealAt = now
        return checkBlocking().status == UpdateStatus.UPDATED
    }

    @Synchronized
    fun checkBlocking(): UpdateResult {
        val result = try { run() } catch (e: Exception) {
            DiagnosticLog.log("Engine", "Update check failed", e)
            UpdateResult(UpdateStatus.FAILED, e.message.orEmpty())
        }
        store.lastCheckAt = System.currentTimeMillis()
        store.lastMessage = "${result.status}${if (result.detail.isNotEmpty()) ": ${result.detail}" else ""}"
        DiagnosticLog.log("Engine", "Update check -> ${store.lastMessage}")
        return result
    }

    private fun run(): UpdateResult {
        if (!supportedDevice) return UpdateResult(UpdateStatus.UNSUPPORTED_DEVICE)
        if (!configured) return UpdateResult(UpdateStatus.NOT_CONFIGURED)

        val manifestBytes = download(manifestUrl, MAX_MANIFEST_BYTES)
        val signature = String(download("$manifestUrl.sig", MAX_MANIFEST_BYTES), Charsets.UTF_8)
        if (!OtaVerifier.verify(manifestBytes, signature, publicKey)) {
            return UpdateResult(UpdateStatus.REJECTED, "signature does not match")
        }
        val manifest = OtaManifestParser.parse(manifestBytes)
        if (manifest.engineApi != Engine.API_VERSION) {
            return UpdateResult(UpdateStatus.INCOMPATIBLE, "needs engine API ${manifest.engineApi}, app has ${Engine.API_VERSION}")
        }
        if (manifest.sequence <= store.installedSequence() || store.isBad(manifest.sequence)) {
            return UpdateResult(UpdateStatus.UP_TO_DATE)
        }

        val stage = store.stagingDir(manifest.sequence).also { it.deleteRecursively(); it.mkdirs() }
        try {
            val base = URI(manifestUrl)
            val jar = File(stage, "engine.jar")
            downloadVerified(base, manifest.engine, jar, MAX_ENGINE_BYTES)
            File(stage, "assets").mkdirs()
            manifest.assets.forEach { downloadVerified(base, it, File(File(stage, "assets"), it.name), MAX_ASSET_BYTES) }

            jar.setReadOnly() // Android refuses to load writable dex files
            // Vet the new engine before it replaces the working one.
            val candidate = EngineHost.instantiate(jar)
            check(candidate.version().isNotBlank()) { "Engine reported no version" }

            val finalDir = store.finalDir(manifest.sequence)
            finalDir.deleteRecursively()
            if (!stage.renameTo(finalDir)) throw IOException("Could not move the engine into place")
            store.activate(manifest.sequence, candidate.version())
            store.prune(keep = setOf(manifest.sequence))
            EngineHost.reload()
            return UpdateResult(UpdateStatus.UPDATED, candidate.version())
        } catch (e: Throwable) {
            stage.walkBottomUp().forEach { it.setWritable(true); it.delete() }
            store.markBad(manifest.sequence)
            throw e
        }
    }

    // ---- Downloads -----------------------------------------------------------------------------------

    private fun downloadVerified(base: URI, file: OtaFile, target: File, maxBytes: Long) {
        val url = base.resolve(file.file).toString()
        require(url.startsWith("https://")) { "Only https downloads are allowed" }
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = open(url)
        try {
            connection.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw IOException("${file.file} is larger than allowed")
                        digest.update(buffer, 0, n)
                        out.write(buffer, 0, n)
                    }
                }
            }
        } finally { connection.disconnect() }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != file.sha256) throw IOException("Checksum mismatch for ${file.file}")
    }

    private fun download(url: String, maxBytes: Long): ByteArray {
        require(url.startsWith("https://")) { "Only https downloads are allowed" }
        val connection = open(url)
        try {
            connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > maxBytes) throw IOException("Response too large")
                    out.write(buffer, 0, n)
                }
                return out.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Drishyopot")
        c.setRequestProperty("Cache-Control", "no-cache")
        if (c.responseCode != 200) { c.disconnect(); throw IOException("HTTP ${c.responseCode} for $url") }
        return c
    }

    companion object {
        private const val AUTO_INTERVAL_MS = 6L * 60 * 60 * 1000
        private const val HEAL_INTERVAL_MS = 15L * 60 * 1000
        private const val MAX_MANIFEST_BYTES = 256L * 1024
        private const val MAX_ENGINE_BYTES = 40L * 1024 * 1024
        private const val MAX_ASSET_BYTES = 2L * 1024 * 1024
    }
}
