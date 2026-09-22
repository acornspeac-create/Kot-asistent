package tj.kod.assistant

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("assistant_settings", Context.MODE_PRIVATE)

    fun serverUrl(): String = prefs.getString(KEY_SERVER_URL, "") ?: ""

    fun saveServerUrl(value: String) {
        prefs.edit().putString(KEY_SERVER_URL, value.trim()).apply()
    }

    private companion object {
        const val KEY_SERVER_URL = "server_url"
    }
}
