package com.drishyopot.app.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * SharedPreferences-backed settings. [revision] increments on every change so
 * Compose can re-read values by collecting it.
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("drishyopot_settings", Context.MODE_PRIVATE)

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun bool(key: String): Boolean =
        prefs.getBoolean(key, PrefKeys.BOOLEAN_DEFAULTS[key] ?: false)

    fun setBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        bump()
    }

    fun int(key: String): Int {
        val spec = PrefKeys.INT_SPECS[key]
        val value = prefs.getInt(key, spec?.default ?: 0)
        return if (spec != null) value.coerceIn(spec.min, spec.max) else value
    }

    fun setInt(key: String, value: Int) {
        val spec = PrefKeys.INT_SPECS[key]
        val safe = if (spec != null) value.coerceIn(spec.min, spec.max) else value
        prefs.edit().putInt(key, safe).apply()
        bump()
    }

    fun resetAll() {
        prefs.edit().clear().apply()
        bump()
    }

    /** All known settings with their current effective values. */
    fun snapshot(): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        PrefKeys.BOOLEAN_DEFAULTS.keys.forEach { out[it] = bool(it) }
        PrefKeys.INT_SPECS.keys.forEach { out[it] = int(it) }
        return out
    }

    /** Replaces everything with [values]; keys not present fall back to defaults. */
    fun restore(values: Map<String, Any>) {
        val editor = prefs.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
            }
        }
        editor.apply()
        bump()
    }

    private fun bump() {
        _revision.update { it + 1 }
    }
}
