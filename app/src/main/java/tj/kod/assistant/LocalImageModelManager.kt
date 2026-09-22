package tj.kod.assistant

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import java.io.File

class LocalImageModelManager(
    private val context: Context,
) {
    private val downloadManager =
        context.getSystemService(DownloadManager::class.java)

    fun recommendedPath(): String =
        recommendedFile().absolutePath

    fun startRecommendedDownload(): ModelDownloadStart {
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
            .setTitle("KOT Offline Photo")
            .setDescription(
                "Stable Diffusion 1.5 Q4_0 • около 1.57 ГБ"
            )
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

        return ModelDownloadStart(
            id = downloadManager.enqueue(request),
            path = target.absolutePath,
            alreadyReady = false,
        )
    }

    fun downloadStatus(id: Long): String {
        if (id < 0L) {
            return if (isRecommendedReady()) {
                "Модель фото уже скачана"
            } else {
                "Загрузка модели фото ещё не запускалась"
            }
        }

        val query = DownloadManager.Query().setFilterById(id)

        return downloadManager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use "Загрузка модели фото не найдена"
            }
            statusFromCursor(cursor)
        } ?: "DownloadManager недоступен"
    }

    fun isRecommendedReady(): Boolean =
        recommendedFile().exists() &&
            recommendedFile().length() > MIN_READY_BYTES

    fun discoverModels(): List<String> {
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

                val ext = file.extension.lowercase()
                if (
                    file.isFile &&
                    (ext == "gguf" ||
                        ext == "safetensors" ||
                        ext == "ckpt")
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
        val dir =
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "models")

        return File(dir, RECOMMENDED_FILE_NAME)
    }

    private fun statusFromCursor(cursor: Cursor): String {
        val status = cursor.getInt(
            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
        )

        return when (status) {
            DownloadManager.STATUS_PENDING ->
                "Модель фото ожидает загрузку"

            DownloadManager.STATUS_PAUSED ->
                "Загрузка модели фото приостановлена"

            DownloadManager.STATUS_RUNNING -> {
                val downloaded = cursor.getLong(
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
                    val percent =
                        ((downloaded * 100L) / total).coerceIn(0L, 100L)
                    "Фото-модель скачивается: $percent%"
                } else {
                    "Фото-модель скачивается…"
                }
            }

            DownloadManager.STATUS_SUCCESSFUL ->
                "Модель фото скачана и готова"

            DownloadManager.STATUS_FAILED -> {
                val reason = cursor.getInt(
                    cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_REASON
                    )
                )
                "Ошибка загрузки фото-модели, код: $reason"
            }

            else -> "Неизвестный статус загрузки фото-модели"
        }
    }

    companion object {
        const val RECOMMENDED_FILE_NAME =
            "stable-diffusion-v1-5-pruned-emaonly-Q4_0.gguf"

        const val RECOMMENDED_MODEL_URL =
            "https://huggingface.co/second-state/" +
                "stable-diffusion-v1-5-GGUF/resolve/main/" +
                RECOMMENDED_FILE_NAME +
                "?download=true"

        private const val MIN_READY_BYTES =
            1_000L * 1024L * 1024L

        private const val MAX_SCAN_FILES = 4_000
    }
}
