package tj.kod.assistant

import android.content.Context
import org.json.JSONObject

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("assistant_settings", Context.MODE_PRIVATE)

    fun serverUrl(): String = prefs.getString(KEY_SERVER_URL, "") ?: ""
    fun serverToken(): String = prefs.getString(KEY_SERVER_TOKEN, "") ?: ""
    fun flasherUrl(): String = prefs.getString(KEY_FLASHER_URL, "") ?: ""
    fun flasherToken(): String = prefs.getString(KEY_FLASHER_TOKEN, "") ?: ""

    fun taskMode(taskId: String): ConnectionMode {
        val raw = prefs.getString("task_mode_" + taskId, ConnectionMode.AUTO.name)
            ?: ConnectionMode.AUTO.name
        return runCatching { ConnectionMode.valueOf(raw) }
            .getOrDefault(ConnectionMode.AUTO)
    }

    fun saveTaskMode(taskId: String, mode: ConnectionMode) {
        prefs.edit()
            .putString("task_mode_" + taskId, mode.name)
            .apply()
    }

    fun personaMode(): PersonaMode {
        val raw = prefs.getString(KEY_PERSONA, PersonaMode.NORMAL.name)
            ?: PersonaMode.NORMAL.name
        return runCatching { PersonaMode.valueOf(raw) }
            .getOrDefault(PersonaMode.NORMAL)
    }

    fun savePersonaMode(mode: PersonaMode) {
        prefs.edit().putString(KEY_PERSONA, mode.name).apply()
    }

    fun accent(): String = prefs.getString(KEY_ACCENT, "Обычный") ?: "Обычный"

    fun saveAccent(value: String) {
        prefs.edit().putString(KEY_ACCENT, value.trim()).apply()
    }

    fun humorLevel(): Int = prefs.getInt(KEY_HUMOR_LEVEL, 2)

    fun saveHumorLevel(level: Int) {
        prefs.edit().putInt(KEY_HUMOR_LEVEL, level.coerceIn(0, 3)).apply()
    }

    fun offlineTextModelPath(): String =
        prefs.getString(KEY_OFFLINE_TEXT_MODEL, "") ?: ""

    fun offlineImageModelPath(): String =
        prefs.getString(KEY_OFFLINE_IMAGE_MODEL, "") ?: ""

    fun offlineVideoModelPath(): String =
        prefs.getString(KEY_OFFLINE_VIDEO_MODEL, "") ?: ""

    fun saveOfflineModelPaths(
        text: String,
        image: String,
        video: String,
    ) {
        prefs.edit()
            .putString(KEY_OFFLINE_TEXT_MODEL, text.trim())
            .putString(KEY_OFFLINE_IMAGE_MODEL, image.trim())
            .putString(KEY_OFFLINE_VIDEO_MODEL, video.trim())
            .apply()
    }

    fun saveServerConfig(
        url: String,
        token: String,
    ) {
        prefs.edit()
            .putString(KEY_SERVER_URL, url.trim())
            .putString(KEY_SERVER_TOKEN, token.trim())
            .apply()
    }

    fun saveFlasherConfig(
        url: String,
        token: String,
    ) {
        prefs.edit()
            .putString(KEY_FLASHER_URL, url.trim())
            .putString(KEY_FLASHER_TOKEN, token.trim())
            .apply()
    }

    fun exportJson(): JSONObject {
        val json = JSONObject()
            .put("serverUrl", serverUrl())
            .put("flasherUrl", flasherUrl())
            .put("persona", personaMode().name)
            .put("accent", accent())
            .put("humorLevel", humorLevel())
            .put("offlineTextModelPath", offlineTextModelPath())
            .put("offlineImageModelPath", offlineImageModelPath())
            .put("offlineVideoModelPath", offlineVideoModelPath())

        val taskModes = JSONObject()
        KotTasks.all.forEach { task ->
            taskModes.put(task.id, taskMode(task.id).name)
        }
        json.put("taskModes", taskModes)

        return json
    }

    private companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_SERVER_TOKEN = "server_token"
        const val KEY_FLASHER_URL = "flasher_url"
        const val KEY_FLASHER_TOKEN = "flasher_token"
        const val KEY_PERSONA = "persona_mode"
        const val KEY_ACCENT = "accent"
        const val KEY_HUMOR_LEVEL = "humor_level"
        const val KEY_OFFLINE_TEXT_MODEL = "offline_text_model"
        const val KEY_OFFLINE_IMAGE_MODEL = "offline_image_model"
        const val KEY_OFFLINE_VIDEO_MODEL = "offline_video_model"
    }
}
