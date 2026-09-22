package tj.kod.assistant

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class LocalModelStore(
    private val context: Context,
) {
    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val prefs = context.getSharedPreferences(
        "kot_local_model",
        Context.MODE_PRIVATE,
    )

    private val modelDir: File
        get() = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir,
            "models",
        )

    val modelFile: File
        get() = File(modelDir, LocalAiConfig.MODEL_FILE)

    private val verifiedMarker: File
        get() = File(modelDir, LocalAiConfig.MODEL_FILE + ".verified")

    suspend fun awaitModel(
        onStatus: suspend (String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        modelDir.mkdirs()

        if (modelFile.exists()) {
            if (verifiedMarker.exists() && modelFile.length() > MIN_MODEL_BYTES) {
                onStatus("Локальная модель найдена")
                return@withContext modelFile
            }

            onStatus("Локальный ИИ: проверяю модель…")
            if (verifySha256(modelFile)) {
                verifiedMarker.writeText(LocalAiConfig.MODEL_SHA256)
                return@withContext modelFile
            }

            modelFile.delete()
            verifiedMarker.delete()
            clearDownloadId()
        }

        var attempts = 0

        while (attempts < 3) {
            attempts += 1

            var downloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
            if (downloadId <= 0L || !downloadExists(downloadId)) {
                downloadId = enqueueDownload()
                prefs.edit().putLong(KEY_DOWNLOAD_ID, downloadId).apply()
            }

            try {
                waitForDownload(downloadId, onStatus)
            } catch (error: Throwable) {
                clearDownloadId()
                if (attempts >= 3) throw error
                onStatus("Локальный ИИ: повторяю загрузку модели…")
                delay(2_000)
                continue
            }

            onStatus("Локальный ИИ: проверяю целостность модели…")

            if (!modelFile.exists() || modelFile.length() < MIN_MODEL_BYTES) {
                modelFile.delete()
                clearDownloadId()
                if (attempts >= 3) {
                    error("Файл локальной модели загрузился не полностью")
                }
                continue
            }

            if (!verifySha256(modelFile)) {
                modelFile.delete()
                verifiedMarker.delete()
                clearDownloadId()
                if (attempts >= 3) {
                    error("Контрольная сумма локальной модели не совпала")
                }
                continue
            }

            verifiedMarker.writeText(LocalAiConfig.MODEL_SHA256)
            clearDownloadId()
            return@withContext modelFile
        }

        error("Не удалось подготовить локальную модель")
    }

    private fun enqueueDownload(): Long {
        modelDir.mkdirs()
        modelFile.delete()

        val request = DownloadManager.Request(Uri.parse(LocalAiConfig.MODEL_URL))
            .setTitle("KOT Assistant · локальный ИИ")
            .setDescription("Qwen3-1.7B · первая загрузка, затем работает офлайн")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "models/" + LocalAiConfig.MODEL_FILE,
            )

        return downloadManager.enqueue(request)
    }

    private fun downloadExists(id: Long): Boolean {
        downloadManager.query(
            DownloadManager.Query().setFilterById(id)
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    private suspend fun waitForDownload(
        id: Long,
        onStatus: suspend (String) -> Unit,
    ) {
        var lastPercent = -1

        while (true) {
            val snapshot = readSnapshot(id)
                ?: error("Загрузка локальной модели не найдена")

            when (snapshot.status) {
                DownloadManager.STATUS_SUCCESSFUL -> return

                DownloadManager.STATUS_FAILED -> {
                    throw IllegalStateException(
                        "Android не смог скачать локальную модель. Код: " +
                            snapshot.reason
                    )
                }

                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_PAUSED -> {
                    onStatus(
                        "Локальный ИИ: жду сеть для первой загрузки (~977 МБ)…"
                    )
                }

                DownloadManager.STATUS_RUNNING -> {
                    if (snapshot.total > 0L) {
                        val percent = (
                            snapshot.downloaded * 100L / snapshot.total
                        ).toInt().coerceIn(0, 100)

                        if (percent != lastPercent) {
                            lastPercent = percent
                            onStatus(
                                "Локальный ИИ: загрузка " + percent + "% · " +
                                    formatMb(snapshot.downloaded) + " / " +
                                    formatMb(snapshot.total)
                            )
                        }
                    } else {
                        onStatus(
                            "Локальный ИИ: загрузка · " +
                                formatMb(snapshot.downloaded)
                        )
                    }
                }
            }

            delay(1_000)
        }
    }

    private fun readSnapshot(id: Long): Snapshot? =
        downloadManager.query(
            DownloadManager.Query().setFilterById(id)
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                Snapshot(
                    status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    ),
                    downloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                        )
                    ),
                    total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                        )
                    ),
                    reason = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                    ),
                )
            }
        }

    private fun clearDownloadId() {
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
    }

    private fun verifySha256(file: File): Boolean {
        if (!file.exists() || file.length() < MIN_MODEL_BYTES) return false

        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024)

        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }

        val actual = digest.digest().joinToString("") { byte ->
            "%02x".format(byte)
        }

        return actual.equals(LocalAiConfig.MODEL_SHA256, ignoreCase = true)
    }

    private fun formatMb(bytes: Long): String =
        String.format(Locale.US, "%.0f МБ", bytes / 1024.0 / 1024.0)

    private data class Snapshot(
        val status: Int,
        val downloaded: Long,
        val total: Long,
        val reason: Int,
    )

    private companion object {
        const val MIN_MODEL_BYTES = 900_000_000L
        const val KEY_DOWNLOAD_ID = "download_id"
    }
}
