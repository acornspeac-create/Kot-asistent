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
    private val offlineAssistant = OfflineAssistant()

    val messages = mutableStateListOf<Message>()

    var input by mutableStateOf("")
    var serverUrl by mutableStateOf(settings.serverUrl())
    var serverToken by mutableStateOf(settings.serverToken())
    var busy by mutableStateOf(false)
    var status by mutableStateOf("Готов")

    init {
        messages.addAll(memory.load())
    }

    fun saveServerConfig() {
        settings.saveServerConfig(
            url = serverUrl,
            token = serverToken,
        )

        serverUrl = settings.serverUrl()
        serverToken = settings.serverToken()

        status = when {
            serverUrl.isBlank() -> "Офлайн-режим"
            serverToken.isBlank() -> "Нужен ключ сервера"
            else -> "Сервер настроен"
        }
    }

    fun submit(
        text: String = input,
        onReply: (String) -> Unit = {},
    ) {
        val clean = text.trim()
        if (clean.isEmpty() || busy) return

        val priorMessages = memory.load()
        val priorHistory = memory.recentTranscript()
        input = ""

        val userMessage = Message("user", clean)
        messages += userMessage
        memory.append(userMessage)

        viewModelScope.launch {
            busy = true

            val onlineConfigured =
                serverUrl.isNotBlank() &&
                serverToken.isNotBlank()

            status = if (onlineConfigured) "Думаю…" else "Офлайн…"

            var usedOffline = !onlineConfigured

            val reply = if (!onlineConfigured) {
                offlineAssistant.reply(clean, priorMessages)
            } else {
                runCatching {
                    api.ask(
                        serverUrl = serverUrl,
                        serverToken = serverToken,
                        text = clean,
                        history = priorHistory,
                    )
                }.getOrElse {
                    usedOffline = true
                    offlineAssistant.reply(clean, priorMessages)
                }
            }

            val assistantMessage = Message("assistant", reply)
            messages += assistantMessage
            memory.append(assistantMessage)

            busy = false
            status = if (usedOffline) "Офлайн" else "Готов"
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
