package tj.kod.assistant

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LocalAiResult(
    val text: String,
    val backend: String,
)

class LocalAiEngine(
    private val context: Context,
) : AutoCloseable {
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var loadedModelPath: String? = null
    private var loadedBackend: String = ""

    suspend fun reply(
        modelPath: String,
        prompt: String,
    ): LocalAiResult = mutex.withLock {
        withContext(Dispatchers.Default) {
            val model = File(modelPath)

            require(model.exists() && model.isFile) {
                "Файл локальной модели не найден: $modelPath"
            }
            require(model.extension.equals("litertlm", ignoreCase = true)) {
                "Для LiteRT-LM нужен файл .litertlm"
            }

            ensureEngine(model.absolutePath)

            val activeEngine = engine
                ?: error("Локальный AI-движок не запущен")

            val text = activeEngine.createConversation().use { conversation ->
                conversation.sendMessage(prompt).toString().trim()
            }

            LocalAiResult(
                text = text.ifBlank {
                    "Локальная модель не вернула текст."
                },
                backend = loadedBackend,
            )
        }
    }

    fun loadedStatus(): String {
        val path = loadedModelPath ?: return "Локальная модель ещё не загружена в память"
        return "Загружена: " + File(path).name + " • " + loadedBackend
    }

    private fun ensureEngine(modelPath: String) {
        if (engine != null && loadedModelPath == modelPath) return

        closeInternal()

        var lastError: Throwable? = null

        val attempts = listOf<Pair<String, () -> Backend>>(
            "GPU/OpenCL" to { Backend.GPU() },
            "CPU" to { Backend.CPU() },
        )

        for ((label, backendFactory) in attempts) {
            var candidate: Engine? = null

            try {
                candidate = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = backendFactory(),
                        cacheDir = context.cacheDir.absolutePath,
                    )
                )
                candidate.initialize()

                engine = candidate
                loadedModelPath = modelPath
                loadedBackend = label
                return
            } catch (error: Throwable) {
                lastError = error
                runCatching { candidate?.close() }
            }
        }

        throw IllegalStateException(
            "Не удалось запустить локальную модель ни на GPU, ни на CPU: " +
                (lastError?.message ?: "неизвестная ошибка"),
            lastError,
        )
    }

    override fun close() {
        closeInternal()
    }

    private fun closeInternal() {
        runCatching { engine?.close() }
        engine = null
        loadedModelPath = null
        loadedBackend = ""
    }
}
