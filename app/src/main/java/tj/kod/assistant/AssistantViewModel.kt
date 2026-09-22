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
import java.io.File

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
    private val backupManager = BackupManager(context, settings, profilesStore, memory)
    private val vaultStore = VaultStore(context)
    private val automationStore = AutomationStore(context)
    private val localAi = LocalAiEngine(context)
    private val localModels = LocalModelManager(context)
    private val localImage = LocalImageEngine(context)
    private val localImageModels = LocalImageModelManager(context)

    val messages = mutableStateListOf<Message>()
    val flasherDevices = mutableStateListOf<FlasherDevice>()
    val profiles = mutableStateListOf<KotProfile>()
    val discoveredTextModels = mutableStateListOf<String>()
    val discoveredImageModels = mutableStateListOf<String>()

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
    var localModelStatus by mutableStateOf("Локальная LLM не установлена")
    var localModelDownloadId by mutableStateOf(-1L)
    var imageModelStatus by mutableStateOf("Офлайн-модель фото не установлена")
    var imageModelDownloadId by mutableStateOf(-1L)
    var generatedImagePath by mutableStateOf("")
    var flasherBusy by mutableStateOf(false)
    var flasherStatus by mutableStateOf("KOT Flasher готов к настройке")
    var busy by mutableStateOf(false)
    var status by mutableStateOf("Готов")

    init {
        messages.addAll(memory.load())
        profiles.addAll(profilesStore.profiles())
        discoverLocalTextModels(selectFirstWhenEmpty = true)
        discoverLocalImageModels(selectFirstWhenEmpty = true)
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
        localModelStatus = if (
            offlineTextModelPath.isNotBlank() &&
            File(offlineTextModelPath).isFile
        ) {
            "Текстовая модель выбрана: " + File(offlineTextModelPath).name
        } else {
            "Путь текстовой модели сохранён, но файл пока не найден"
        }
        status = "Пути офлайн-моделей сохранены"
    }

    fun downloadRecommendedTextModel() {
        val start = runCatching {
            localModels.startRecommendedTextModelDownload()
        }.getOrElse {
            localModelStatus =
                "Не удалось начать загрузку: " +
                    (it.message ?: it::class.java.simpleName)
            return
        }

        offlineTextModelPath = start.path
        settings.saveOfflineModelPaths(
            text = offlineTextModelPath,
            image = offlineImageModelPath,
            video = offlineVideoModelPath,
        )

        if (start.alreadyReady) {
            localModelDownloadId = -1L
            localModelStatus = "Qwen3 1.7B уже скачана и готова"
            discoverLocalTextModels(selectFirstWhenEmpty = false)
        } else {
            localModelDownloadId = start.id
            localModelStatus =
                "Загрузка Qwen3 1.7B INT4 запущена. Можно выйти из KOT и вернуться позже."
        }
    }

    fun checkTextModelDownload() {
        val downloadState = localModels.downloadStatus(
            localModelDownloadId
        )

        localModelStatus = downloadState

        if (localModels.isRecommendedReady()) {
            offlineTextModelPath = localModels.recommendedModelPath()
            settings.saveOfflineModelPaths(
                text = offlineTextModelPath,
                image = offlineImageModelPath,
                video = offlineVideoModelPath,
            )
            discoverLocalTextModels(selectFirstWhenEmpty = false)
            localModelStatus =
                "Модель готова: " + File(offlineTextModelPath).name
        }
    }

    fun discoverLocalTextModels(
        selectFirstWhenEmpty: Boolean = false,
    ) {
        val found = localModels.discoverTextModels()
        discoveredTextModels.clear()
        discoveredTextModels.addAll(found)

        if (
            selectFirstWhenEmpty &&
            offlineTextModelPath.isBlank() &&
            found.isNotEmpty()
        ) {
            offlineTextModelPath = found.first()
            settings.saveOfflineModelPaths(
                text = offlineTextModelPath,
                image = offlineImageModelPath,
                video = offlineVideoModelPath,
            )
        }

        localModelStatus = when {
            offlineTextModelPath.isNotBlank() &&
                File(offlineTextModelPath).isFile ->
                "Готова: " + File(offlineTextModelPath).name

            found.isNotEmpty() ->
                "Найдено локальных моделей: " + found.size

            else ->
                "Локальная LLM не найдена. Скачай рекомендуемую модель."
        }
    }

    fun selectLocalTextModel(path: String) {
        offlineTextModelPath = path
        settings.saveOfflineModelPaths(
            text = offlineTextModelPath,
            image = offlineImageModelPath,
            video = offlineVideoModelPath,
        )
        localModelStatus = "Выбрана: " + File(path).name
    }

    fun testLocalTextModel() {
        if (busy) return

        val modelPath = offlineTextModelPath.trim()
        if (modelPath.isBlank() || !File(modelPath).isFile) {
            localModelStatus =
                "Сначала скачай или выбери .litertlm модель"
            return
        }

        viewModelScope.launch {
            busy = true
            localModelStatus = "Запускаю локальную модель…"

            runCatching {
                localAi.reply(
                    modelPath = modelPath,
                    prompt = buildLocalPrompt(
                        userText = "Ответь одной короткой фразой: офлайн ИИ KOT работает.",
                        history = "",
                    ),
                )
            }.onSuccess { result ->
                localModelStatus =
                    "Офлайн ИИ работает • " + result.backend +
                        " • ответ: " + result.text.take(140)
            }.onFailure {
                localModelStatus =
                    "Ошибка локального ИИ: " +
                        (it.message ?: it::class.java.simpleName)
            }

            busy = false
        }
    }

    fun downloadRecommendedImageModel() {
        val start = runCatching {
            localImageModels.startRecommendedDownload()
        }.getOrElse {
            imageModelStatus =
                "Не удалось начать загрузку фото-модели: " +
                    (it.message ?: it::class.java.simpleName)
            return
        }

        offlineImageModelPath = start.path
        settings.saveOfflineModelPaths(
            text = offlineTextModelPath,
            image = offlineImageModelPath,
            video = offlineVideoModelPath,
        )

        if (start.alreadyReady) {
            imageModelDownloadId = -1L
            imageModelStatus = "Stable Diffusion 1.5 уже скачана"
            discoverLocalImageModels(selectFirstWhenEmpty = false)
        } else {
            imageModelDownloadId = start.id
            imageModelStatus =
                "Загрузка Stable Diffusion 1.5 Q4_0 запущена (~1.57 ГБ)"
        }
    }

    fun checkImageModelDownload() {
        imageModelStatus =
            localImageModels.downloadStatus(imageModelDownloadId)

        if (localImageModels.isRecommendedReady()) {
            offlineImageModelPath = localImageModels.recommendedPath()
            settings.saveOfflineModelPaths(
                text = offlineTextModelPath,
                image = offlineImageModelPath,
                video = offlineVideoModelPath,
            )
            discoverLocalImageModels(selectFirstWhenEmpty = false)
            imageModelStatus =
                "Фото-модель готова: " +
                    File(offlineImageModelPath).name
        }
    }

    fun discoverLocalImageModels(
        selectFirstWhenEmpty: Boolean = false,
    ) {
        val found = localImageModels.discoverModels()
        discoveredImageModels.clear()
        discoveredImageModels.addAll(found)

        if (
            selectFirstWhenEmpty &&
            offlineImageModelPath.isBlank() &&
            found.isNotEmpty()
        ) {
            offlineImageModelPath = found.first()
            settings.saveOfflineModelPaths(
                text = offlineTextModelPath,
                image = offlineImageModelPath,
                video = offlineVideoModelPath,
            )
        }

        imageModelStatus = when {
            offlineImageModelPath.isNotBlank() &&
                File(offlineImageModelPath).isFile ->
                "Готова: " + File(offlineImageModelPath).name

            found.isNotEmpty() ->
                "Найдено моделей изображения: " + found.size

            else ->
                "Офлайн-модель фото не найдена"
        }
    }

    fun selectLocalImageModel(path: String) {
        offlineImageModelPath = path
        settings.saveOfflineModelPaths(
            text = offlineTextModelPath,
            image = offlineImageModelPath,
            video = offlineVideoModelPath,
        )
        imageModelStatus = "Выбрана: " + File(path).name
    }

    fun generateOfflineImage() {
        if (busy) return

        val prompt = input.trim()
        if (prompt.isBlank()) {
            imageModelStatus = "Сначала опиши изображение"
            return
        }

        val modelPath = offlineImageModelPath.trim()
        if (modelPath.isBlank() || !File(modelPath).isFile) {
            imageModelStatus =
                "Сначала скачай или выбери модель изображения"
            return
        }

        viewModelScope.launch {
            busy = true
            imageModelStatus =
                "Генерирую полностью офлайн • CPU • 512×512…"

            runCatching {
                localImage.generate(
                    modelPath = modelPath,
                    prompt = prompt,
                    width = 512,
                    height = 512,
                    steps = 20,
                )
            }.onSuccess { result ->
                generatedImagePath = result.filePath
                imageModelStatus =
                    "Готово офлайн: " + File(result.filePath).name
            }.onFailure {
                imageModelStatus =
                    "Ошибка офлайн-фото: " +
                        (it.message ?: it::class.java.simpleName)
            }

            busy = false
        }
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

            val taskId = selectedTaskId.ifBlank { "chat" }
            val mode = settings.taskMode(taskId)
            selectedTaskMode = mode

            val onlineConfigured =
                serverUrl.isNotBlank() &&
                    serverToken.isNotBlank()

            val localReady =
                offlineTextModelPath.isNotBlank() &&
                    File(offlineTextModelPath).isFile

            suspend fun askLocal(): String {
                if (!localReady) {
                    return offlineAssistant.reply(
                        clean,
                        priorMessages,
                        personaMode,
                        humorLevel,
                    )
                }

                val result = localAi.reply(
                    modelPath = offlineTextModelPath,
                    prompt = buildLocalPrompt(
                        userText = clean,
                        history = priorHistory,
                    ),
                )

                localModelStatus =
                    "Офлайн ИИ • " + result.backend +
                        " • " + File(offlineTextModelPath).name

                return result.text
            }

            suspend fun askOnline(): String {
                if (!onlineConfigured) {
                    error("AI-сервер не настроен")
                }

                return api.ask(
                    serverUrl = serverUrl,
                    serverToken = serverToken,
                    text = buildOnlinePrompt(clean),
                    history = priorHistory,
                )
            }

            val reply: String
            var usedOffline = false

            when (mode) {
                ConnectionMode.OFFLINE -> {
                    usedOffline = true
                    status = if (localReady) {
                        "Офлайн ИИ думает…"
                    } else {
                        "Офлайн без LLM…"
                    }

                    reply = runCatching {
                        askLocal()
                    }.getOrElse { error ->
                        localModelStatus =
                            "Ошибка локальной модели: " +
                                (error.message ?: error::class.java.simpleName)

                        offlineAssistant.reply(
                            clean,
                            priorMessages,
                            personaMode,
                            humorLevel,
                        )
                    }
                }

                ConnectionMode.ONLINE -> {
                    status = "Онлайн…"

                    reply = runCatching {
                        askOnline()
                    }.getOrElse { error ->
                        "Не удалось выполнить запрос онлайн: " +
                            (error.message ?: error::class.java.simpleName)
                    }
                }

                ConnectionMode.AUTO -> {
                    val preferOnline =
                        onlineConfigured && shouldPreferOnline(clean)

                    if (preferOnline) {
                        status = "Авто: усиленный онлайн ИИ…"

                        val onlineAttempt = runCatching {
                            askOnline()
                        }

                        if (onlineAttempt.isSuccess) {
                            reply = onlineAttempt.getOrThrow()
                        } else if (localReady) {
                            status = "Авто: онлайн недоступен, использую локальный ИИ…"
                            usedOffline = true
                            reply = runCatching {
                                askLocal()
                            }.getOrElse {
                                offlineAssistant.reply(
                                    clean,
                                    priorMessages,
                                    personaMode,
                                    humorLevel,
                                )
                            }
                        } else {
                            usedOffline = true
                            reply = offlineAssistant.reply(
                                clean,
                                priorMessages,
                                personaMode,
                                humorLevel,
                            )
                        }
                    } else if (localReady) {
                        status = "Авто: локальный ИИ думает…"

                        val localAttempt = runCatching {
                            askLocal()
                        }

                        if (localAttempt.isSuccess) {
                            usedOffline = true
                            reply = localAttempt.getOrThrow()
                        } else if (onlineConfigured) {
                            status = "Авто: локальный ИИ недоступен, подключаю онлайн…"
                            reply = runCatching {
                                askOnline()
                            }.getOrElse {
                                usedOffline = true
                                offlineAssistant.reply(
                                    clean,
                                    priorMessages,
                                    personaMode,
                                    humorLevel,
                                )
                            }
                        } else {
                            usedOffline = true
                            reply = offlineAssistant.reply(
                                clean,
                                priorMessages,
                                personaMode,
                                humorLevel,
                            )
                        }
                    } else if (onlineConfigured) {
                        status = "Авто: онлайн…"
                        reply = runCatching {
                            askOnline()
                        }.getOrElse {
                            usedOffline = true
                            offlineAssistant.reply(
                                clean,
                                priorMessages,
                                personaMode,
                                humorLevel,
                            )
                        }
                    } else {
                        usedOffline = true
                        status = "Авто: базовый локальный режим"
                        reply = offlineAssistant.reply(
                            clean,
                            priorMessages,
                            personaMode,
                            humorLevel,
                        )
                    }
                }
            }

            val finalReply = improveReply(
                raw = reply,
                userText = clean,
                priorMessages = priorMessages,
            )

            val assistantMessage = Message("assistant", finalReply)
            messages += assistantMessage
            memory.append(assistantMessage)

            busy = false
            status = if (usedOffline) "Офлайн" else "Готов"
            onReply(finalReply)
        }
    }

    private fun shouldPreferOnline(text: String): Boolean {
        if (text.length >= 80) return true

        val lower = text.lowercase()
        val complexHints = listOf(
            "найди",
            "сравни",
            "объясни",
            "почему",
            "как сделать",
            "как исправить",
            "проанализ",
            "рассчитай",
            "посчитай",
            "переведи",
            "напиши код",
            "ошибк",
            "новост",
            "погода",
            "курс",
            "цена",
            "купить",
            "продать",
        )

        return complexHints.any { hint -> lower.contains(hint) }
    }

    private fun improveReply(
        raw: String,
        userText: String,
        priorMessages: List<Message>,
    ): String {
        var candidate = raw.trim()

        val cannedOffline = listOf(
            "сейчас я офлайн",
            "сейчас работаю в офлайн-режиме",
            "для полного ии-ответа нужен",
            "запрос сохранён",
            "сообщение запомнил",
        )

        if (
            candidate.isBlank() ||
            cannedOffline.any { candidate.lowercase().contains(it) }
        ) {
            candidate = offlineAssistant.reply(
                userText,
                priorMessages,
                personaMode,
                humorLevel,
            )
        }

        val previous = priorMessages
            .asReversed()
            .firstOrNull { it.role == "assistant" }
            ?.text
            .orEmpty()

        if (
            previous.isNotBlank() &&
            normalizeReply(previous) == normalizeReply(candidate)
        ) {
            candidate = offlineAssistant.reply(
                userText,
                priorMessages + Message("assistant", candidate),
                personaMode,
                humorLevel,
            )
        }

        return candidate.trim()
    }

    private fun normalizeReply(value: String): String =
        value
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun buildLocalPrompt(
        userText: String,
        history: String,
    ): String {
        val persona = when (personaMode) {
            PersonaMode.NORMAL ->
                "Обычный полезный личный ассистент."
            PersonaMode.FUNNY_FRIEND ->
                "Очень смешной дружелюбный друг. Шути живо, но не унижай людей и национальности."
            PersonaMode.FUNNY_GIRLFRIEND ->
                "Очень смешная дружелюбная подруга. Будь тёплой, энергичной и остроумной."
            PersonaMode.ADULT_COMPANION ->
                "Взрослая флиртующая виртуальная собеседница. Только вымышленная взрослая персона."
            PersonaMode.BUSINESS ->
                "Деловой помощник: кратко, конкретно, по делу."
            PersonaMode.TEACHER ->
                "Терпеливый преподаватель, объясняющий понятно."
            PersonaMode.MECHANIC ->
                "Практичный автомеханик и технический помощник."
            PersonaMode.BUILDER ->
                "Практичный строитель и помощник по ремонту."
            PersonaMode.PROGRAMMER ->
                "Сильный программист и инженер."
        }

        val profileName =
            profiles.firstOrNull { it.id == activeProfileId }?.name
                ?: activeProfileId

        return buildString {
            appendLine("Ты KOT — умный личный AI-ассистент на телефоне.")
            appendLine("Всегда отвечай на языке пользователя; по умолчанию по-русски.")
            appendLine("Сначала пойми реальную цель запроса и отвечай прямо по существу.")
            appendLine("Не повторяй один и тот же ответ и не используй шаблонную фразу два раза подряд.")
            appendLine("Не говори про онлайн, офлайн, сервер или модель, если пользователь сам об этом не спросил.")
            appendLine("Используй историю диалога, чтобы понимать короткие продолжения вроде «а это?», «почему?» и «дальше».")
            appendLine("Для простого разговора отвечай естественно; для сложной задачи давай конкретные шаги и расчёты.")
            appendLine("Не выдумывай текущие новости, цены, погоду или другие данные, которых у тебя нет.")
            appendLine("Профиль: $profileName.")
            appendLine("Характер: $persona")
            appendLine("Манера речи/акцент: $accent.")
            appendLine("Уровень юмора: $humorLevel из 3.")
            appendLine("Не делай акцент или национальность объектом унижения; юмор строй на ситуации и словах.")
            if (history.isNotBlank()) {
                appendLine("История разговора:")
                appendLine(history.takeLast(7_000))
            }
            appendLine("Текущий запрос пользователя: $userText")
            append("Ответ KOT:")
        }
    }

    private fun buildOnlinePrompt(userText: String): String =
        buildString {
            appendLine("Ты KOT — умный личный ассистент.")
            appendLine("Отвечай прямо на запрос пользователя, без лишних сообщений о режиме работы.")
            appendLine("Не повторяй предыдущий ответ. Используй переданную историю разговора.")
            appendLine("Если задача сложная, дай конкретное решение, шаги, расчёты или код.")
            appendLine("Стиль: " + personaMode.label + ".")
            appendLine("Манера/акцент: " + accent + ". Юмор: " + humorLevel + "/3.")
            append("Запрос пользователя: " + userText)
        }

    fun clearMemory() {
        memory.clear()
        messages.clear()
        status = "Локальная память очищена"
    }

    override fun onCleared() {
        localImage.close()
        localAi.close()
        super.onCleared()
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
