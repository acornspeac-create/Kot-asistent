package tj.kod.assistant

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class AssistantViewModel(
    context: Context,
) : ViewModel() {
    private val memory = MemoryStore(context)
    private val settings = SettingsStore(context)
    private val api = AssistantApi()

    val messages = mutableStateListOf<Message>()

    var input by mutableStateOf("")
    var serverUrl by mutableStateOf(settings.serverUrl())
    var busy by mutableStateOf(false)
    var status by mutableStateOf("Готов")

    init {
        messages.addAll(memory.load())
    }

    fun saveServerUrl() {
        settings.saveServerUrl(serverUrl)
        serverUrl = settings.serverUrl()
        status = if (serverUrl.isBlank()) {
            "Адрес сервера очищен"
        } else {
            "Адрес сервера сохранён"
        }
    }

    fun submit(
        text: String = input,
        onReply: (String) -> Unit = {},
    ) {
        val clean = text.trim()
        if (clean.isEmpty() || busy) return

        val priorHistory = memory.recentTranscript()
        input = ""

        val userMessage = Message("user", clean)
        messages += userMessage
        memory.append(userMessage)

        viewModelScope.launch {
            busy = true
            status = "Думаю…"

            val reply = runCatching {
                api.ask(
                    serverUrl = serverUrl,
                    text = clean,
                    history = priorHistory,
                )
            }.getOrElse { throwable ->
                "Ошибка связи: " + (throwable.message ?: "неизвестная ошибка")
            }

            val assistantMessage = Message("assistant", reply)
            messages += assistantMessage
            memory.append(assistantMessage)

            busy = false
            status = "Готов"
            onReply(reply)
        }
    }

    fun clearMemory() {
        memory.clear()
        messages.clear()
        status = "Локальная память очищена"
    }

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AssistantViewModel(context.applicationContext) as T
        }
    }
}
