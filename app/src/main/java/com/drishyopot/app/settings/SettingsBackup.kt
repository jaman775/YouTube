package com.drishyopot.app.settings

import org.json.JSONException
import org.json.JSONObject

/** JSON export/import of the settings snapshot. */
object SettingsBackup {
    private const val APP_ID = "drishyopot"
    private const val FORMAT_VERSION = 1

    fun export(store: SettingsStore): String = exportMap(store.snapshot())

    fun exportMap(values: Map<String, Any>): String {
        val settings = JSONObject()
        values.forEach { (k, v) -> settings.put(k, v) }
        return JSONObject()
            .put("app", APP_ID)
            .put("format", FORMAT_VERSION)
            .put("settings", settings)
            .toString(2)
    }

    /** Returns validated values, or null when the file is not a valid backup. */
    fun parse(text: String): Map<String, Any>? {
        return try {
            val root = JSONObject(text)
            if (root.optString("app") != APP_ID) return null
            val settings = root.getJSONObject("settings")
            val out = LinkedHashMap<String, Any>()
            for (key in PrefKeys.BOOLEAN_DEFAULTS.keys) {
                if (!settings.has(key)) continue
                val v = settings.get(key)
                if (v is Boolean) out[key] = v else return null
            }
            for ((key, spec) in PrefKeys.INT_SPECS) {
                if (!settings.has(key)) continue
                val v = settings.get(key)
                if (v is Int) out[key] = v.coerceIn(spec.min, spec.max) else return null
            }
            out
        } catch (e: JSONException) {
            null
        }
    }
}
