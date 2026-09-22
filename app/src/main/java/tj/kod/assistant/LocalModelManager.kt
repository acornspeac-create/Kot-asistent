package tj.kod.assistant

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import java.io.File

data class ModelDownloadStart(
    val id: Long,
    val path: String,
    val alreadyReady: Boolean,
)

class LocalModelManager(
    private val context: Context,
) {
    private val downloadManager =
        context.getSystemService(DownloadManager::class.java)

    fun recommendedModelPath(): String =
        recommendedFile().absolutePath

    fun startRecommendedTextModelDownload(): ModelDownloadStart {
        val target = recommendedFile()
        target.parentFile?.mkdirs()

        if (target.exists() && target.length() > MIN_READY_BYTES) {
            return ModelDownloadStart(
                id = -1L,
                path = target.absolutePath,
                alreadyReady = true,
            )
        }

        if (target.exists()) {
            target.delete()
        }

        val request = DownloadManager.Request(
            Uri.parse(RECOMMENDED_MODEL_URL)
        )
            .setTitle("KOT Offline AI")
            .setDescription("Qwen3 0.6B INT4 • локальная модель")
            .setMimeType("application/octet-stream")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                RECOMMENDED_FILE_NAME,
            )

        val id = downloadManager.enqueue(request)

        return ModelDownloadStart(
            id = id,
            path = target.absolutePath,
            alreadyReady = false,
        )
    }

    fun downloadStatus(downloadId: Long): String {
        if (downloadId < 0L) {
            return if (recommendedFile().exists()) {
                "Модель уже скачана"
            } else {
                "Загрузка ещё не запускалась"
            }
        }

        val query = DownloadManager.Query().setFilterById(downloadId)

        return downloadManager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use "Загрузка не найдена"
            }

            statusFromCursor(cursor)
        } ?: "DownloadManager недоступен"
    }

    fun isRecommendedReady(): Boolean =
        recommendedFile().exists() &&
            recommendedFile().length() > MIN_READY_BYTES

    fun discoverTextModels(): List<String> {
        val roots = buildList<File> {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?.let(::add)

            add(File(context.filesDir, "models"))

            if (AccessController.hasAllFilesAccess()) {
                @Suppress("DEPRECATION")
                add(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )
                )
            }
        }.distinctBy { it.absolutePath }

        val found = linkedSetOf<String>()

        for (root in roots) {
            if (!root.exists()) continue

            var inspected = 0
            root.walkTopDown().forEach { file ->
                if (inspected >= MAX_SCAN_FILES) return@forEach
                inspected += 1

                if (
                    file.isFile &&
                    file.extension.equals("litertlm", ignoreCase = true)
                ) {
                    found += file.absolutePath
                }
            }
        }

        return found
            .map(::File)
            .sortedByDescending { it.lastModified() }
            .map { it.absolutePath }
    }

    private fun recommendedFile(): File {
        val dir = context.getExternalFilesDir(
            Environment.DIRECTORY_DOWNLOADS
        ) ?: File(context.filesDir, "models")

        return File(dir, RECOMMENDED_FILE_NAME)
    }

    private fun statusFromCursor(cursor: Cursor): String {
        val status = cursor.getInt(
            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
        )

        return when (status) {
            DownloadManager.STATUS_PENDING -> "Загрузка ожидает запуска"
            DownloadManager.STATUS_PAUSED -> "Загрузка приостановлена"
            DownloadManager.STATUS_RUNNING -> {
                val soFar = cursor.getLong(
                    cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                    )
                )
                val total = cursor.getLong(
                    cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                    )
                )

                if (total > 0L) {
                    val percent = ((soFar * 100L) / total).coerceIn(0L, 100L)
                    "Скачивается: $percent%"
                } else {
                    "Скачивается…"
                }
            }

            DownloadManager.STATUS_SUCCESSFUL -> "Модель скачана и готова"
            DownloadManager.STATUS_FAILED -> {
                val reason = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                )
                "Ошибка загрузки, код: $reason"
            }

            else -> "Неизвестный статус загрузки"
        }
    }

    companion object {
        const val RECOMMENDED_FILE_NAME =
            "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"

        const val RECOMMENDED_MODEL_URL =
            "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/" +
                RECOMMENDED_FILE_NAME +
                "?download=true"

        private const val MIN_READY_BYTES = 100L * 1024L * 1024L
        private const val MAX_SCAN_FILES = 2_000
    }
}
