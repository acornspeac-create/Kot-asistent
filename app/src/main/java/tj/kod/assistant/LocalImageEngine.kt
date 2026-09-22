package tj.kod.assistant

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalImageResult(
    val filePath: String,
    val width: Int,
    val height: Int,
)

class LocalImageEngine(
    private val context: Context,
) : AutoCloseable {
    suspend fun generate(
        modelPath: String,
        prompt: String,
        negativePrompt: String = "low quality, blurry, distorted, deformed",
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        seed: Long = System.currentTimeMillis(),
    ): LocalImageResult = withContext(Dispatchers.Default) {
        val model = File(modelPath)

        require(model.exists() && model.isFile) {
            "Модель изображения не найдена: $modelPath"
        }

        require(
            model.extension.equals("gguf", ignoreCase = true) ||
                model.extension.equals("safetensors", ignoreCase = true) ||
                model.extension.equals("ckpt", ignoreCase = true)
        ) {
            "Нужна модель .gguf, .safetensors или .ckpt"
        }

        val safeWidth = width.coerceIn(256, 1024)
        val safeHeight = height.coerceIn(256, 1024)

        val rgb = nativeGenerateRgb(
            modelPath = model.absolutePath,
            prompt = prompt.trim(),
            negativePrompt = negativePrompt.trim(),
            width = safeWidth,
            height = safeHeight,
            steps = steps.coerceIn(1, 50),
            seed = seed,
        ) ?: error("Нативный генератор не вернул изображение")

        val expected = safeWidth * safeHeight * 3
        require(rgb.size == expected) {
            "Неверный размер RGB: \${rgb.size}, ожидалось $expected"
        }

        val pixels = IntArray(safeWidth * safeHeight)
        var source = 0

        for (i in pixels.indices) {
            val r = rgb[source].toInt() and 0xff
            val g = rgb[source + 1].toInt() and 0xff
            val b = rgb[source + 2].toInt() and 0xff

            pixels[i] =
                (0xff shl 24) or
                    (r shl 16) or
                    (g shl 8) or
                    b

            source += 3
        }

        val bitmap = Bitmap.createBitmap(
            pixels,
            safeWidth,
            safeHeight,
            Bitmap.Config.ARGB_8888,
        )

        val dir =
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?.let { File(it, "KOT") }
                ?: File(context.filesDir, "generated-images")

        dir.mkdirs()

        val output = File(
            dir,
            "kot-" + System.currentTimeMillis() + ".png",
        )

        FileOutputStream(output).use { stream ->
            check(
                bitmap.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    stream,
                )
            ) {
                "Не удалось сохранить PNG"
            }
        }

        bitmap.recycle()

        LocalImageResult(
            filePath = output.absolutePath,
            width = safeWidth,
            height = safeHeight,
        )
    }

    override fun close() {
        runCatching { nativeClose() }
    }

    private external fun nativeGenerateRgb(
        modelPath: String,
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        seed: Long,
    ): ByteArray?

    private external fun nativeClose()

    private companion object {
        init {
            System.loadLibrary("kot_image")
        }
    }
}
