package com.drishyopot.app.engine

import com.drishyopot.app.account.YoutubeAccount
import com.drishyopot.app.settings.PrefKeys
import com.drishyopot.app.settings.SettingsStore
import com.drishyopot.engine.api.HttpBridge
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The engine's network access. Adds the signed-in account's cookies for YouTube hosts. */
class HostHttpBridge(private val settings: SettingsStore) : HttpBridge {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    override fun execute(
        method: String,
        url: String,
        headers: Map<String, List<String>>?,
        body: ByteArray?,
        languageTag: String?,
    ): HttpBridge.HttpResult {
        val builder = Request.Builder().url(url)
            .method(method, body?.toRequestBody() ?: if (method == "POST" || method == "PUT") ByteArray(0).toRequestBody() else null)
            .header("User-Agent", USER_AGENT)

        val present = HashSet<String>()
        headers?.forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
            present += name.lowercase()
        }
        YoutubeAccount.authHeaders(url, settings.bool(PrefKeys.USE_ACCOUNT)).forEach { (name, value) ->
            // Never override what the extractor set for itself.
            if (name.lowercase() !in present) builder.header(name, value)
        }

        client.newCall(builder.build()).execute().use { response ->
            return HttpBridge.HttpResult(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string().orEmpty(),
                response.request.url.toString(),
            )
        }
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
