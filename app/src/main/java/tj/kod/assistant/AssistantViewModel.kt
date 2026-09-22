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
    private val appContext = context.applicationContext
    private val memory = MemoryStore(context)
    private val settings = SettingsStore(context)
    private val api = AssistantApi()
    private val flasherApi = FlasherApi()
    private val offlineAssistant = OfflineAssistant()
    private val ownerActions = OwnerActionExecutor(context)
    private val profilesStore = ProfileStore(context)
    private val backupManager = BackupManager(context, settings, profilesStore)
    private val vaultStore = VaultStore(context)
    private val automationStore = AutomationStore(context)

    val messages = mutableStateListOf<Message>()
    val flasherDevices = mutableStateListOf<FlasherDevice>()
    val profiles = mutableStateListOf<KotProfile>()

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
    var selectedTaskId by mutableStateOf("")
    var selectedTaskMode by mutableStateOf(ConnectionMode.AUTO)
    var personaMode by mutableStateOf(settings.personaMode())
    var accent by mutableStateOf(settings.accent())
    var humorLevel by mutableStateOf(settings.humorLevel())
    var newProfileName by mutableStateOf("")
    var activeProfileId by mutableStateOf(profilesStore.activeProfileId())
    var offlineTextModelPath by mutableStateOf(settings.offlineTextModelPath())
    var offlineImageModelPath by mutableStateOf(settings.offlineImageModelPath())
    var offlineVideoModelPath by mutableStateOf(settings.offlineVideoModelPath())
    var backupStatus by mutableStateOf("")
    var continuousVoice by mutableStateOf(settings.continuousVoice())
    var wakeWord by mutableStateOf(settings.wakeWord())
    var vaultText by mutableStateOf(vaultStore.loadText())
    var vaultStatus by mutableStateOf("")
    var automationText by mutableStateOf(automationStore.dailyText())
    var automationStatus by mutableStateOf("")
    var flasherBusy by mutableStateOf(false)
    var flasherStatus by mutableStateOf("KOT Flasher готов к настройке")
    var busy by mutableStateOf(false)
    var status by mutableStateOf("Готов")

    init {
        messages.addAll(memory.load())
        profiles.addAll(profilesStore.profiles())
    }

    fun taskModeLabel(taskId: String): String =
        settings.taskMode(taskId).label

    fun openTask(taskId: String) {
        selectedTaskId = taskId
        selectedTaskMode = settings.taskMode(taskId)

        when (taskId) {
            "flasher" -> showFlasher = true
            "access" -> showAccess = true
        }
    }

    fun closeTask() {
        selectedTaskId = ""
        showFlasher = false
        showAccess = false
    }

    fun setTaskMode(mode: ConnectionMode) {
        val taskId = selectedTaskId.ifBlank { "chat" }
        selectedTaskMode = mode
        settings.saveTaskMode(taskId, mode)
        status = "Режим «" + mode.label + "» сохранён для " +
            (KotTasks.byId(taskId)?.title ?: taskId)
    }

    fun setPersona(mode: PersonaMode) {
        personaMode = mode
        settings.savePersonaMode(mode)
    }

    fun saveCompanionStyle() {
        settings.saveAccent(accent)
        settings.saveHumorLevel(humorLevel)
        status = "Стиль общения сохранён"
    }

    fun addProfile() {
        val profile = profilesStore.addProfile(newProfileName)
        profiles.add(profile)
        newProfileName = ""
        selectProfile(profile.id)
    }

    fun selectProfile(id: String) {
        activeProfileId = id
        profilesStore.setActiveProfile(id)
        status = "Активный профиль: " +
            (profiles.firstOrNull { it.id == id }?.name ?: id)
    }

    fun saveOfflineModels() {
        settings.saveOfflineModelPaths(
            text = offlineTextModelPath,
            image = offlineImageModelPath,
            video = offlineVideoModelPath,
        )
        status = "Пути офлайн-моделей сохранены"
    }

    fun createBackup() {
        backupStatus = runCatching {
            backupManager.createBackup().absolutePath
        }.fold(
            onSuccess = { "Резервная копия создана: " + it },
            onFailure = { "Ошибка резервной копии: " + (it.message ?: "неизвестно") },
        )
    }

    fun saveVoiceMode() {
        settings.saveVoiceMode(
            continuous = continuousVoice,
            wakeWord = wakeWord,
        )
        status = "Голосовой режим сохранён"
    }

    fun saveVault() {
        vaultStatus = runCatching {
            vaultStore.saveText(vaultText)
            "Приватные данные зашифрованы и сохранены"
        }.getOrElse {
            "Ошибка приватного хранилища: " +
                (it.message ?: it::class.java.simpleName)
        }
    }

    fun clearVault() {
        vaultStore.clear()
        vaultText = ""
        vaultStatus = "Приватная папка очищена"
    }

    fun saveDailyAutomation() {
        val clean = automationText.trim()
        if (clean.isBlank()) {
            automationStatus = "Напиши текст ежедневного сценария"
            return
        }

        automationStore.saveDailyText(clean)
        AutomationScheduler.scheduleDaily(appContext)
        automationStatus = "Ежедневный сценарий сохранён"
    }

    fun disableDailyAutomation() {
        AutomationScheduler.cancelDaily(appContext)
        automationStatus = "Ежедневный сценарий выключен"
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

        if (selectedTaskId == "autopilot") {
            ownerActions.tryExecuteSequence(clean)?.let { directReply ->
                val assistantMessage = Message("assistant", directReply)
                messages += assistantMessage
                memory.append(assistantMessage)
                status = "Готов"
                onReply(directReply)
                return
            }
        }

        ownerActions.tryExecute(clean)?.let { directReply ->
            val assistantMessage = Message("assistant", directReply)
            messages += assistantMessage
            memory.append(assistantMessage)
            status = "Готов"
            onReply(directReply)
            return
        }

        viewModelScope.launch {
            busy = true

            val onlineConfigured =
                serverUrl.isNotBlank() &&
                serverToken.isNotBlank()

            status = if (onlineConfigured) "Думаю…" else "Офлайн…"

            var usedOffline = !onlineConfigured

            val reply = if (!onlineConfigured) {
                offlineAssistant.reply(clean, priorMessages, personaMode, humorLevel)
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
