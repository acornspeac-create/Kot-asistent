package tj.kod.assistant

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun KotMainMenuScreen(
    viewModel: AssistantViewModel,
    onOpenTask: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "KOT Assistant",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Главное меню • профиль: " +
                    (viewModel.profiles.firstOrNull {
                        it.id == viewModel.activeProfileId
                    }?.name ?: "Я"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = viewModel.status,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        items(KotTasks.all) { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = task.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Режим: " + viewModel.taskModeLabel(task.id),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Button(
                        onClick = { onOpenTask(task.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Открыть")
                    }
                }
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
                        "Офлайн: путь к локальной модели изображения задаётся в разделе «Офлайн-модели».",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item { CommandBox(viewModel, onSpeak, "Опиши изображение") }
            }

            "video" -> {
                item {
                    Text(
                        "Офлайн-видео требует установленной локальной видео-модели и достаточно памяти/вычислений.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item { CommandBox(viewModel, onSpeak, "Опиши видео") }
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

        OutlinedTextField(
            value = viewModel.offlineTextModelPath,
            onValueChange = { viewModel.offlineTextModelPath = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Текстовая/голосовая модель") },
        )

        OutlinedTextField(
            value = viewModel.offlineImageModelPath,
            onValueChange = { viewModel.offlineImageModelPath = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Модель генерации фото") },
        )

        OutlinedTextField(
            value = viewModel.offlineVideoModelPath,
            onValueChange = { viewModel.offlineVideoModelPath = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Модель генерации видео") },
        )

        Button(
            onClick = viewModel::saveOfflineModels,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сохранить модели")
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
