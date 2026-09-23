package tj.kod.assistant

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun KotMainMenuScreen(
    viewModel: AssistantViewModel,
    onOpenTask: (String) -> Unit,
) {
    val profileName = viewModel.profiles.firstOrNull {
        it.id == viewModel.activeProfileId
    }?.name ?: "Я"

    val intelligence = setOf(
        "chat", "web", "code", "models", "voice", "camera", "image", "video",
    )
    val tools = setOf(
        "phone", "files", "automations", "autopilot", "flasher",
    )
    val system = setOf(
        "memory", "profiles", "vault", "backup", "updates", "access",
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "KOT",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "● READY",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Text(
                        "Личный AI-центр",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "Профиль: $profileName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                    Text(
                        viewModel.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    )
                }
            }
        }

        item {
            Text(
                "Быстрый запуск",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onOpenTask("chat") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Чат")
                }
                Button(
                    onClick = { onOpenTask("models") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("MAX ИИ")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onOpenTask("autopilot") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Сделай сам")
                }
                Button(
                    onClick = { onOpenTask("updates") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Обновление")
                }
            }
        }

        item {
            DashboardSectionTitle(
                title = "Интеллект",
                subtitle = "Модели, голос, зрение и генерация",
            )
        }

        items(
            KotTasks.all.filter { it.id in intelligence },
            key = { it.id },
        ) { task ->
            DashboardTaskCard(
                task = task,
                mode = viewModel.taskModeLabel(task.id),
                onOpen = { onOpenTask(task.id) },
            )
        }

        item {
            DashboardSectionTitle(
                title = "Инструменты",
                subtitle = "Телефон, файлы и автоматизация",
            )
        }

        items(
            KotTasks.all.filter { it.id in tools },
            key = { it.id },
        ) { task ->
            DashboardTaskCard(
                task = task,
                mode = viewModel.taskModeLabel(task.id),
                onOpen = { onOpenTask(task.id) },
            )
        }

        item {
            DashboardSectionTitle(
                title = "Система",
                subtitle = "Память, защита, резервные копии и обновления",
            )
        }

        items(
            KotTasks.all.filter { it.id in system },
            key = { it.id },
        ) { task ->
            DashboardTaskCard(
                task = task,
                mode = viewModel.taskModeLabel(task.id),
                onOpen = { onOpenTask(task.id) },
            )
        }
    }
}

@Composable
private fun DashboardSectionTitle(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier.padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DashboardTaskCard(
    task: KotTask,
    mode: String,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                task.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Режим: $mode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "KOT",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF4E8CFF),
                )
            }
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Открыть")
            }
        }
    }
}

@Composable
fun KotTaskScreen(
    viewModel: AssistantViewModel,
    task: KotTask,
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
    onListen: () -> Unit,
    onSpeak: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = task.title,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = task.subtitle,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        item {
            ConnectionModeSelector(viewModel)
        }

        when (task.id) {
            "chat" -> {
                item { PersonaSection(viewModel) }
                item { ServerSection(viewModel) }
                item {
                    Button(
                        onClick = onListen,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.busy,
                    ) {
                        Text("🎙 Говорить")
                    }
                }
                item { CommandBox(viewModel, onSpeak) }
            }

            "web" -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "Живой поиск по интернету",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "KOT ищет веб-источники напрямую. AI-сервер для самого поиска не обязателен; с локальной моделью KOT дополнительно соберёт ответ по найденным страницам.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                item {
                    CommandBox(
                        viewModel = viewModel,
                        onSpeak = onSpeak,
                        label = "Что найти в интернете",
                    )
                }
            }

            "code" -> {
                item {
                    Text(
                        "Режим программиста: попроси написать функцию, экран, Android-модуль, исправить ошибку или разобрать код.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    CommandBox(
                        viewModel = viewModel,
                        onSpeak = onSpeak,
                        label = "Что написать или исправить",
                    )
                }
            }

            "voice" -> {
                item {
                    Text(
                        "Непрерывный голос работает, пока KOT открыт. Слово активации можно изменить.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    Button(
                        onClick = {
                            viewModel.continuousVoice = !viewModel.continuousVoice
                            viewModel.saveVoiceMode()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (viewModel.continuousVoice) {
                                "✓ Непрерывный голос включён"
                            } else {
                                "Включить непрерывный голос"
                            }
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = viewModel.wakeWord,
                        onValueChange = { viewModel.wakeWord = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Слово активации") },
                        singleLine = true,
                    )
                }
                item {
                    Button(
                        onClick = viewModel::saveVoiceMode,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Сохранить голосовой режим")
                    }
                }
                item {
                    Button(
                        onClick = onListen,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Начать слушать")
                    }
                }
            }

            "camera" -> {
                item {
                    Button(
                        onClick = onOpenCamera,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Открыть камеру")
                    }
                }
                item {
                    Text(
                        "Офлайн-анализ использует локальную vision-модель, если она установлена. Онлайн-анализ может использовать сервер.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item { CommandBox(viewModel, onSpeak) }
            }

            "image" -> {
                item {
                    Text(
                        viewModel.imageModelStatus,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                item {
                    OutlinedTextField(
                        value = viewModel.input,
                        onValueChange = { viewModel.input = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Опиши изображение") },
                        minLines = 3,
                        enabled = !viewModel.busy,
                    )
                }

                item {
                    Button(
                        onClick = viewModel::generateOfflineImage,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.busy &&
                            viewModel.input.isNotBlank(),
                    ) {
                        Text(
                            if (viewModel.busy) {
                                "Генерирую…"
                            } else {
                                "Сгенерировать полностью офлайн"
                            }
                        )
                    }
                }

                if (viewModel.generatedImagePath.isNotBlank()) {
                    item {
                        val bitmap = remember(
                            viewModel.generatedImagePath
                        ) {
                            BitmapFactory.decodeFile(
                                viewModel.generatedImagePath
                            )
                        }

                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Фото KOT",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Text(
                            "Файл: " + viewModel.generatedImagePath,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                item {
                    Text(
                        "Модель и загрузка находятся в «Офлайн-модели». Первый запуск после выбора модели может быть медленным.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            "video" -> {
                item {
                    Text(
                        viewModel.videoStatus,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    Text(
                        "Wan 2.1 T2V 1.3B • 320×192 • 9 кадров • полностью на телефоне.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    OutlinedTextField(
                        value = viewModel.input,
                        onValueChange = { viewModel.input = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Опиши видео") },
                        minLines = 3,
                        enabled = !viewModel.busy,
                    )
                }
                item {
                    Button(
                        onClick = viewModel::generateOfflineVideo,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.busy &&
                            viewModel.input.isNotBlank(),
                    ) {
                        Text(
                            if (viewModel.busy) "Генерирую видео…"
                            else "Сгенерировать офлайн-видео"
                        )
                    }
                }
                if (viewModel.generatedVideoPath.isNotBlank()) {
                    item {
                        Text(
                            "Файл: " + viewModel.generatedVideoPath,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    item {
                        Button(
                            onClick = viewModel::openGeneratedVideo,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Открыть видео")
                        }
                    }
                }
            }

            "phone" -> {
                item {
                    Text(
                        "Локальные команды: домой, назад, недавние, быстрые настройки, уведомления, открой Wi‑Fi, открой Bluetooth, открой <приложение>, заблокируй экран.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item { CommandBox(viewModel, onSpeak, "Команда телефону") }
            }

            "files" -> {
                item {
                    Text(
                        "Команды: «найди файл <имя>», «установи apk <полный путь>».",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item { CommandBox(viewModel, onSpeak, "Команда с файлами") }
            }

            "memory" -> {
                item {
                    Text(
                        "Сообщений в локальной памяти интерфейса: " +
                            viewModel.messages.size,
                    )
                }
                item {
                    Button(
                        onClick = viewModel::clearMemory,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Очистить локальную память")
                    }
                }
            }

            "automations" -> {
                item {
                    Text(
                        "Ежедневный сценарий запускается системным WorkManager даже после закрытия KOT.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    OutlinedTextField(
                        value = viewModel.automationText,
                        onValueChange = { viewModel.automationText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Что напоминать каждый день") },
                        minLines = 3,
                    )
                }
                item {
                    Button(
                        onClick = viewModel::saveDailyAutomation,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Включить ежедневный сценарий")
                    }
                }
                item {
                    Button(
                        onClick = viewModel::disableDailyAutomation,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Выключить ежедневный сценарий")
                    }
                }
                if (viewModel.automationStatus.isNotBlank()) {
                    item { Text(viewModel.automationStatus) }
                }
            }

            "autopilot" -> {
                item {
                    Text(
                        "Локальный «Сделай сам»: перечисли несколько действий через точку с запятой. Например: «домой; открой Telegram».",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    CommandBox(
                        viewModel,
                        onSpeak,
                        "Действия через ;",
                    )
                }
            }

            "models" -> {
                item { OfflineModelsSection(viewModel) }
            }

            "profiles" -> {
                item { ProfilesSection(viewModel) }
            }

            "vault" -> {
                item {
                    Text(
                        "Данные этого раздела шифруются ключом Android Keystore и хранятся локально.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    OutlinedTextField(
                        value = viewModel.vaultText,
                        onValueChange = { viewModel.vaultText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Приватные заметки KOT") },
                        minLines = 5,
                    )
                }
                item {
                    Button(
                        onClick = viewModel::saveVault,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Зашифровать и сохранить")
                    }
                }
                item {
                    Button(
                        onClick = viewModel::clearVault,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Очистить приватную папку")
                    }
                }
                if (viewModel.vaultStatus.isNotBlank()) {
                    item { Text(viewModel.vaultStatus) }
                }
            }

            "backup" -> {
                item {
                    Button(
                        onClick = viewModel::createBackup,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Создать резервную копию")
                    }
                }
                if (viewModel.backupStatus.isNotBlank()) {
                    item { Text(viewModel.backupStatus) }
                }
            }

            "updates" -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Обновление поверх текущей версии",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "KOT проверит GitHub, скачает новый APK и предложит системную установку. Настройки и память приложения сохраняются.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = viewModel::checkForUpdatesNow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Проверить обновления сейчас")
                    }
                }
                item {
                    Button(
                        onClick = viewModel::createBackup,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Создать резервную копию перед обновлением")
                    }
                }
                if (viewModel.backupStatus.isNotBlank()) {
                    item { Text(viewModel.backupStatus) }
                }
            }

            else -> {
                item { CommandBox(viewModel, onSpeak) }
            }
        }

        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("← Главное меню")
            }
        }
    }
}

@Composable
private fun ConnectionModeSelector(viewModel: AssistantViewModel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Режим задачи",
            style = MaterialTheme.typography.titleSmall,
        )

        ConnectionMode.entries.forEach { mode ->
            Button(
                onClick = { viewModel.setTaskMode(mode) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (viewModel.selectedTaskMode == mode) {
                        "✓ " + mode.label
                    } else {
                        mode.label
                    }
                )
            }
        }
    }
}

@Composable
private fun PersonaSection(viewModel: AssistantViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Характер KOT",
                style = MaterialTheme.typography.titleMedium,
            )

            PersonaMode.entries.forEach { persona ->
                Button(
                    onClick = { viewModel.setPersona(persona) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (viewModel.personaMode == persona) {
                            "✓ " + persona.label
                        } else {
                            persona.label
                        }
                    )
                }
            }

            OutlinedTextField(
                value = viewModel.accent,
                onValueChange = { viewModel.accent = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Акцент / манера речи") },
                singleLine = true,
            )

            Text("Юмор: " + viewModel.humorLevel + " / 3")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (0..3).forEach { level ->
                    Button(
                        onClick = {
                            viewModel.humorLevel = level
                            viewModel.saveCompanionStyle()
                        },
                    ) {
                        Text(level.toString())
                    }
                }
            }

            Button(
                onClick = viewModel::saveCompanionStyle,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить стиль")
            }
        }
    }
}

@Composable
private fun ServerSection(viewModel: AssistantViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Онлайн-сервер", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = viewModel.serverUrl,
                onValueChange = { viewModel.serverUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Адрес AI-сервера") },
                singleLine = true,
            )

            OutlinedTextField(
                value = viewModel.serverToken,
                onValueChange = { viewModel.serverToken = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ключ сервера") },
                singleLine = true,
            )

            Button(
                onClick = viewModel::saveServerConfig,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить сервер")
            }
        }
    }
}

@Composable
private fun CommandBox(
    viewModel: AssistantViewModel,
    onSpeak: (String) -> Unit,
    label: String = "Напиши команду",
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = viewModel.input,
            onValueChange = { viewModel.input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            maxLines = 5,
            enabled = !viewModel.busy,
        )

        Button(
            onClick = { viewModel.submit(onReply = onSpeak) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.busy && viewModel.input.isNotBlank(),
        ) {
            Text(if (viewModel.busy) "Выполняю…" else "Выполнить")
        }

        viewModel.messages.takeLast(4).forEach { message ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        if (message.role == "user") "Ты" else "KOT",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(message.text)
                }
            }
        }
    }
}

@Composable
private fun OfflineModelsSection(viewModel: AssistantViewModel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Менеджер локальных моделей",
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text = viewModel.localModelStatus,
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Рекомендуемая модель",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Qwen3 1.7B INT4 • около 932 МБ • заметно умнее 0.6B и работает через LiteRT-LM на телефоне.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = viewModel::downloadRecommendedTextModel,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.busy,
                ) {
                    Text("Скачать умный офлайн ИИ")
                }
                Button(
                    onClick = viewModel::checkTextModelDownload,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.busy,
                ) {
                    Text("Проверить загрузку")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Максимальный интеллект",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Qwen3 4B Instruct INT4 • около 2.66 ГБ • умнее, но требует больше памяти и работает медленнее.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    viewModel.maxLocalModelStatus,
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = viewModel::downloadMaxTextModel,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.busy,
                ) {
                    Text("Скачать MAX ИИ")
                }
                Button(
                    onClick = viewModel::checkMaxTextModelDownload,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.busy,
                ) {
                    Text("Проверить MAX загрузку")
                }
            }
        }

        Button(
            onClick = { viewModel.discoverLocalTextModels() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.busy,
        ) {
            Text("Найти .litertlm на телефоне")
        }

        viewModel.discoveredTextModels.forEach { path ->
            Button(
                onClick = { viewModel.selectLocalTextModel(path) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.busy,
            ) {
                val selected = path == viewModel.offlineTextModelPath
                Text(
                    (if (selected) "✓ " else "") +
                        java.io.File(path).name
                )
            }
        }

        OutlinedTextField(
            value = viewModel.offlineTextModelPath,
            onValueChange = { viewModel.offlineTextModelPath = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Путь к текстовой .litertlm модели") },
        )

        Button(
            onClick = viewModel::testLocalTextModel,
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.busy,
        ) {
            Text(if (viewModel.busy) "Проверяю…" else "Проверить офлайн ИИ")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Офлайн-генерация фото",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    viewModel.imageModelStatus,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Stable Diffusion 1.5 Q4_0 • около 1.57 ГБ • stable-diffusion.cpp.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = viewModel::downloadRecommendedImageModel,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.busy,
                ) {
                    Text("Скачать модель фото")
                }
                Button(
                    onClick = viewModel::checkImageModelDownload,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.busy,
                ) {
                    Text("Проверить загрузку фото-модели")
                }
                Button(
                    onClick = { viewModel.discoverLocalImageModels() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.busy,
                ) {
                    Text("Найти модели фото на телефоне")
                }
            }
        }

        viewModel.discoveredImageModels.forEach { path ->
            Button(
                onClick = { viewModel.selectLocalImageModel(path) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.busy,
            ) {
                val selected = path == viewModel.offlineImageModelPath
                Text(
                    (if (selected) "✓ " else "") +
                        java.io.File(path).name
                )
            }
        }

        OutlinedTextField(
            value = viewModel.offlineImageModelPath,
            onValueChange = { viewModel.offlineImageModelPath = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Модель генерации фото (.gguf/.safetensors)") },
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Офлайн-генерация видео",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    viewModel.videoStatus,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Wan 2.1 T2V 1.3B Q4_0 + VAE + UMT5 • около 4.46 ГБ.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = viewModel::downloadStarterVideoPack,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.busy,
                ) {
                    Text("Скачать видео-комплект")
                }
                Button(
                    onClick = viewModel::checkVideoPackDownload,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.busy,
                ) {
                    Text("Проверить загрузку видео")
                }
            }
        }

        OutlinedTextField(
            value = viewModel.offlineVideoModelPath,
            onValueChange = { viewModel.offlineVideoModelPath = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Wan diffusion модель") },
        )

        OutlinedTextField(
            value = viewModel.videoVaePath,
            onValueChange = { viewModel.videoVaePath = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Wan VAE") },
        )

        OutlinedTextField(
            value = viewModel.videoTextEncoderPath,
            onValueChange = { viewModel.videoTextEncoderPath = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("UMT5 text encoder") },
        )

        Button(
            onClick = viewModel::saveVideoModels,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сохранить видео-модели")
        }

        Button(
            onClick = viewModel::saveOfflineModels,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сохранить пути моделей")
        }
    }
}

@Composable
private fun ProfilesSection(viewModel: AssistantViewModel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Профили", style = MaterialTheme.typography.titleMedium)

        viewModel.profiles.forEach { profile ->
            Button(
                onClick = { viewModel.selectProfile(profile.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (viewModel.activeProfileId == profile.id) {
                        "✓ " + profile.name
                    } else {
                        profile.name
                    }
                )
            }
        }

        OutlinedTextField(
            value = viewModel.newProfileName,
            onValueChange = { viewModel.newProfileName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Новый профиль") },
            singleLine = true,
        )

        Button(
            onClick = viewModel::addProfile,
            modifier = Modifier.fillMaxWidth(),
            enabled = viewModel.newProfileName.isNotBlank(),
        ) {
            Text("Добавить профиль")
        }
    }
}
