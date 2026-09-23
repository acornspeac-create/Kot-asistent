package tj.kod.assistant

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import java.io.File

data class VideoPackPaths(
    val diffusion: String,
    val vae: String,
    val textEncoder: String,
)

data class VideoPackDownloads(
    val diffusionId: Long,
    val vaeId: Long,
    val textEncoderId: Long,
    val paths: VideoPackPaths,
)

class LocalVideoModelManager(
    private val context: Context,
) {
    private val downloads =
        context.getSystemService(DownloadManager::class.java)

    fun starterPaths(): VideoPackPaths =
        VideoPackPaths(
            diffusion = modelFile(DIFFUSION_FILE).absolutePath,
            vae = modelFile(VAE_FILE).absolutePath,
            textEncoder = modelFile(TEXT_ENCODER_FILE).absolutePath,
        )

    fun startStarterPack(): VideoPackDownloads {
        val paths = starterPaths()

        return VideoPackDownloads(
            diffusionId = enqueueIfMissing(
                modelFile(DIFFUSION_FILE),
                DIFFUSION_MIN_BYTES,
                DIFFUSION_URL,
                "KOT Video 1/3",
                "Wan 2.1 T2V 1.3B Q4_0 • 1.35 ГБ",
            ),
            vaeId = enqueueIfMissing(
                modelFile(VAE_FILE),
                VAE_MIN_BYTES,
                VAE_URL,
                "KOT Video 2/3",
                "Wan 2.1 VAE • 254 МБ",
            ),
            textEncoderId = enqueueIfMissing(
                modelFile(TEXT_ENCODER_FILE),
                TEXT_MIN_BYTES,
                TEXT_ENCODER_URL,
                "KOT Video 3/3",
                "UMT5 Q3 • 2.86 ГБ",
            ),
            paths = paths,
        )
    }

    fun ready(): Boolean =
        modelFile(DIFFUSION_FILE).length() > DIFFUSION_MIN_BYTES &&
            modelFile(VAE_FILE).length() > VAE_MIN_BYTES &&
            modelFile(TEXT_ENCODER_FILE).length() > TEXT_MIN_BYTES

    fun status(
        diffusionId: Long,
        vaeId: Long,
        textEncoderId: Long,
    ): String {
        if (ready()) return "Видео-комплект готов"

        return listOf(
            "Wan " + oneStatus(diffusionId, modelFile(DIFFUSION_FILE)),
            "VAE " + oneStatus(vaeId, modelFile(VAE_FILE)),
            "UMT5 " + oneStatus(textEncoderId, modelFile(TEXT_ENCODER_FILE)),
        ).joinToString(" • ")
    }

    private fun enqueueIfMissing(
        target: File,
        minBytes: Long,
        url: String,
        title: String,
        description: String,
    ): Long {
        target.parentFile?.mkdirs()

        if (target.exists() && target.length() > minBytes) {
            return -1L
        }

        if (target.exists()) target.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription(description)
            .setMimeType("application/octet-stream")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                target.name,
            )

        return downloads.enqueue(request)
    }

    private fun oneStatus(
        id: Long,
        target: File,
    ): String {
        if (id < 0L) {
            return if (target.exists()) "готов" else "не запущено"
        }

        val query = DownloadManager.Query().setFilterById(id)

        return downloads.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use "не найден"

            when (
                cursor.getInt(
                    cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_STATUS
                    )
                )
            ) {
                DownloadManager.STATUS_PENDING -> "ожидает"
                DownloadManager.STATUS_PAUSED -> "пауза"
                DownloadManager.STATUS_RUNNING -> progress(cursor)
                DownloadManager.STATUS_SUCCESSFUL -> "готов"
                DownloadManager.STATUS_FAILED -> "ошибка"
                else -> "неизвестно"
            }
        } ?: "недоступно"
    }

    private fun progress(cursor: Cursor): String {
        val done = cursor.getLong(
            cursor.getColumnIndexOrThrow(
                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
            )
        )
        val total = cursor.getLong(
            cursor.getColumnIndexOrThrow(
                DownloadManager.COLUMN_TOTAL_SIZE_BYTES
            )
        )

        return if (total > 0L) {
            (((done * 100L) / total).coerceIn(0L, 100L))
                .toString() + "%"
        } else {
            "скачивается"
        }
    }

    private fun modelFile(name: String): File {
        val dir = context.getExternalFilesDir(
            Environment.DIRECTORY_DOWNLOADS
        ) ?: File(context.filesDir, "models")

        return File(dir, name)
    }

    companion object {
        const val DIFFUSION_FILE = "wan2.1-t2v-1.3b-q4_0.gguf"
        const val VAE_FILE = "wan_2.1_vae.safetensors"
        const val TEXT_ENCODER_FILE = "umt5-xxl-encoder-Q3_K_S.gguf"

        const val DIFFUSION_URL =
            "https://huggingface.co/calcuis/wan-1.3b-gguf/resolve/main/" +
                DIFFUSION_FILE +
                "?download=true"

        const val VAE_URL =
            "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/" +
                "split_files/vae/" +
                VAE_FILE +
                "?download=true"

        const val TEXT_ENCODER_URL =
            "https://huggingface.co/city96/umt5-xxl-encoder-gguf/resolve/main/" +
                TEXT_ENCODER_FILE +
                "?download=true"

        private const val DIFFUSION_MIN_BYTES = 900L * 1024L * 1024L
        private const val VAE_MIN_BYTES = 180L * 1024L * 1024L
        private const val TEXT_MIN_BYTES = 2500L * 1024L * 1024L
    }
}
