package ai.deepseek.dsh.android

import android.content.SharedPreferences

/** Persists validated service origins and the origin selected for the next launch. */
class ServiceStore(private val preferences: SharedPreferences) {
    /** Returns saved origins in deterministic display order. */
    fun all(): List<String> {
        val values = linkedSetOf<String>()
        preferences.getStringSet(SERVICES_KEY, null).orEmpty()
            .filter(String::isNotBlank)
            .forEach(values::add)
        preferences.getString(LEGACY_SERVICE_KEY, null)
            ?.takeIf(String::isNotBlank)
            ?.let(values::add)
        return values.toList().sorted()
    }

    /** Returns the last selected origin, or the first saved origin after migration. */
    fun last(): String? {
        val services = all()
        return preferences.getString(LAST_SERVICE_KEY, null)
            ?.takeIf(services::contains)
            ?: services.firstOrNull()
    }

    /** Adds an origin and makes it the origin selected on the next launch. */
    fun remember(url: String) {
        val values = all().toMutableSet().apply { add(url) }
        preferences.edit()
            .putStringSet(SERVICES_KEY, values)
            .putString(LAST_SERVICE_KEY, url)
            .putString(LEGACY_SERVICE_KEY, url)
            .apply()
    }

    /** Removes an origin and selects a remaining origin when necessary. */
    fun remove(url: String) {
        val remaining = all().filterNot { it == url }
        val selected = preferences.getString(LAST_SERVICE_KEY, null)
            ?.takeIf { it != url && remaining.contains(it) }
            ?: remaining.firstOrNull()
        val editor = preferences.edit()
        if (remaining.isEmpty()) {
            editor.remove(SERVICES_KEY)
        } else {
            editor.putStringSet(SERVICES_KEY, remaining.toSet())
        }
        if (selected == null) {
            editor.remove(LAST_SERVICE_KEY).remove(LEGACY_SERVICE_KEY)
        } else {
            editor.putString(LAST_SERVICE_KEY, selected).putString(LEGACY_SERVICE_KEY, selected)
        }
        editor.apply()
    }

    companion object {
        const val PREFERENCES = "dsh_android"
        private const val SERVICES_KEY = "service_urls"
        const val LAST_SERVICE_KEY = "last_service"
        const val LEGACY_SERVICE_KEY = "service_url"
    }
}
