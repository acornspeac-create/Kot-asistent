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
    private val flasherApi = FlasherApi()
    private val offlineAssistant = OfflineAssistant()

    val messages = mutableStateListOf<Message>()
    val flasherDevices = mutableStateListOf<FlasherDevice>()

    var input by mutableStateOf("")
    var serverUrl by mutableStateOf(settings.serverUrl())
    var serverToken by mutableStateOf(settings.serverToken())
    var flasherUrl by mutableStateOf(settings.flasherUrl())
    var flasherToken by mutableStateOf(settings.flasherToken())
    var flasherManifestPath by mutableStateOf("")
    var flasherConfirmation by mutableStateOf("")
    var selectedFlasherSerial by mutableStateOf("")
    var showFlasher by mutableStateOf(false)
    var showAccess by mutableStateOf(false)
    var flasherBusy by mutableStateOf(false)
    var flasherStatus by mutableStateOf("KOT Flasher готов к настройке")
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

    fun saveFlasherConfig() {
        settings.saveFlasherConfig(
            url = flasherUrl,
            token = flasherToken,
        )

        flasherUrl = settings.flasherUrl()
        flasherToken = settings.flasherToken()
        flasherStatus = when {
            flasherUrl.isBlank() -> "Укажи адрес KOT Flasher Agent"
            flasherToken.isBlank() -> "Укажи токен KOT Flasher Agent"
            else -> "Настройки flasher сохранены"
        }
    }

    fun refreshFlasherDevices() {
        if (flasherBusy) return

        viewModelScope.launch {
            flasherBusy = true
            flasherStatus = "Ищу телефоны…"

            runCatching {
                flasherApi.devices(
                    agentUrl = flasherUrl,
                    agentToken = flasherToken,
                )
            }.onSuccess { devices ->
                flasherDevices.clear()
                flasherDevices.addAll(devices)

                if (selectedFlasherSerial.isNotBlank() &&
                    devices.none { it.serial == selectedFlasherSerial }
                ) {
                    selectedFlasherSerial = ""
                }

                flasherStatus = if (devices.isEmpty()) {
                    "Телефоны не найдены"
                } else {
                    "Найдено устройств: " + devices.size
                }
            }.onFailure {
                flasherStatus = it.message ?: "Ошибка подключения к KOT Flasher Agent"
            }

            flasherBusy = false
        }
    }

    fun selectFlasherDevice(serial: String) {
        selectedFlasherSerial = serial
        flasherConfirmation = ""
        flasherStatus = "Выбрано устройство: " + serial
    }

    fun prepareFlashMode() {
        if (flasherBusy) return

        val serial = selectedFlasherSerial
        if (serial.isBlank()) {
            flasherStatus = "Сначала выбери телефон"
            return
        }

        viewModelScope.launch {
            flasherBusy = true
            flasherStatus = "Перевожу телефон в нужный режим прошивки…"

            runCatching {
                flasherApi.rebootToFlashMode(
                    agentUrl = flasherUrl,
                    agentToken = flasherToken,
                    serial = serial,
                )
            }.onSuccess {
                flasherStatus =
                    it + " Подожди несколько секунд и нажми «Найти телефоны»."
            }.onFailure {
                flasherStatus =
                    it.message ?: "Не удалось перейти в режим прошивки"
            }

            flasherBusy = false
        }
    }

    fun checkFlashPlan() {
        if (flasherBusy) return

        if (selectedFlasherSerial.isBlank()) {
            flasherStatus = "Сначала выбери телефон"
            return
        }

        if (flasherManifestPath.isBlank()) {
            flasherStatus = "Укажи путь к manifest JSON на компьютере"
            return
        }

        viewModelScope.launch {
            flasherBusy = true
            flasherStatus = "Проверяю прошивку без записи…"

            runCatching {
                flasherApi.flash(
                    agentUrl = flasherUrl,
                    agentToken = flasherToken,
                    serial = selectedFlasherSerial,
                    manifestPath = flasherManifestPath,
                    execute = false,
                )
            }.onSuccess {
                flasherStatus = it
            }.onFailure {
                flasherStatus = it.message ?: "Не удалось проверить план прошивки"
            }

            flasherBusy = false
        }
    }

    fun executeFlash() {
        if (flasherBusy) return

        val serial = selectedFlasherSerial
        if (serial.isBlank()) {
            flasherStatus = "Сначала выбери телефон"
            return
        }

        if (flasherManifestPath.isBlank()) {
            flasherStatus = "Укажи путь к manifest JSON на компьютере"
            return
        }

        val expected = "FLASH " + serial
        if (flasherConfirmation != expected) {
            flasherStatus = "Для запуска введи точно: " + expected
            return
        }

        viewModelScope.launch {
            flasherBusy = true
            flasherStatus = "Прошивка выполняется. Не отключай USB…"

            runCatching {
                flasherApi.flash(
                    agentUrl = flasherUrl,
                    agentToken = flasherToken,
                    serial = serial,
                    manifestPath = flasherManifestPath,
                    execute = true,
                    confirmation = flasherConfirmation,
                )
            }.onSuccess {
                flasherStatus = it
                flasherConfirmation = ""
            }.onFailure {
                flasherStatus = it.message ?: "Ошибка прошивки"
            }

            flasherBusy = false
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
                }.getOrElse { error ->
                    usedOffline = true
                    val detail = error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
                    "Не удалось подключиться к AI-серверу. Ошибка: " + detail
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
