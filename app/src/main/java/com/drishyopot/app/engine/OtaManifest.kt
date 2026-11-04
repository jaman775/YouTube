package com.drishyopot.app.engine

import android.annotation.SuppressLint
import org.json.JSONException
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class OtaFile(val name: String, val file: String, val sha256: String)

data class OtaManifest(
    val sequence: Int,
    val engineApi: Int,
    val engineVersion: String,
    val engine: OtaFile,
    val assets: List<OtaFile>,
    val notes: String,
)

class ManifestException(message: String) : Exception(message)

object OtaManifestParser {
    /** Only these files may be replaced by an update. */
    val ALLOWED_ASSETS = setOf("potoken.html", "inject.js")

    private val safeFile = Regex("^[A-Za-z0-9._-]{1,100}$")
    private val sha256 = Regex("^[0-9a-f]{64}$")

    fun parse(bytes: ByteArray): OtaManifest {
        try {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            if (o.optInt("schema") != 1) throw ManifestException("Unsupported manifest schema")
            val engine = o.getJSONObject("engine")
            val assets = o.optJSONArray("assets")
            return OtaManifest(
                sequence = o.getInt("sequence").also { if (it <= 0) throw ManifestException("Bad sequence") },
                engineApi = o.getInt("engineApi"),
                engineVersion = engine.optString("version"),
                engine = file(engine, engine.getString("file")),
                assets = (0 until (assets?.length() ?: 0)).map { i ->
                    val a = assets!!.getJSONObject(i)
                    val name = a.getString("name")
                    if (name !in ALLOWED_ASSETS) throw ManifestException("Asset not allowed: $name")
                    file(a, a.getString("file"), name)
                },
                notes = o.optString("notes"),
            )
        } catch (e: JSONException) {
            throw ManifestException("Malformed manifest: ${e.message}")
        }
    }

    private fun file(o: JSONObject, file: String, name: String = "engine"): OtaFile {
        if (!safeFile.matches(file)) throw ManifestException("Unsafe file name: $file")
        val hash = o.getString("sha256").lowercase()
        if (!sha256.matches(hash)) throw ManifestException("Bad checksum for $file")
        return OtaFile(name, file, hash)
    }
}

/** ECDSA P-256 / SHA-256 signature check over the exact manifest bytes. */
@SuppressLint("NewApi") // engine updates are only used on Android 8+ (see EngineHost)
object OtaVerifier {
    fun verify(manifest: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean = try {
        val decoder = Base64.getMimeDecoder()
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(decoder.decode(publicKeyBase64.trim())))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(manifest)
            verify(decoder.decode(signatureBase64.trim()))
        }
    } catch (_: Exception) {
        false
    }
}
