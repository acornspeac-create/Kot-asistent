package tj.kod.assistant

import android.content.Context
import org.json.JSONObject
import java.io.File

class BackupManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val profiles: ProfileStore,
) {
    fun createBackup(): File {
        val dir = File(context.filesDir, "backups")
        dir.mkdirs()

        val file = File(
            dir,
            "kot-backup-" + System.currentTimeMillis() + ".json",
        )

        val json = JSONObject()
            .put("version", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("settings", settings.exportJson())
            .put("profiles", profiles.exportJson())

        file.writeText(json.toString(2))
        return file
    }
}
