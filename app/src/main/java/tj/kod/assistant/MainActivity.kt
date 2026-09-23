package tj.kod.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import java.util.Locale
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var viewModel: AssistantViewModel
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                viewModel.status = if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    "Shizuku подключён к KOT"
                } else {
                    "Доступ Shizuku для KOT не выдан"
                }
            }
        }

    private val shizukuBinderReceivedListener =
        Shizuku.OnBinderReceivedListener {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                viewModel.status = "Shizuku подключён к KOT"
            }
        }

    private val shizukuBinderDeadListener =
        Shizuku.OnBinderDeadListener {
            viewModel.status = "Shizuku остановлен"
        }

    private val accessPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val missing = AccessController.missingRuntimePermissions(this)
            viewModel.status = if (missing.isEmpty()) {
                "Основные разрешения выданы"
            } else {
                "Выдано не всё. Осталось разрешений: " + missing.size
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(
            this,
            AssistantViewModel.Factory(applicationContext),
        )[AssistantViewModel::class.java]

        textToSpeech = TextToSpeech(this, this)
        setupSpeechRecognizer()

        UpdateScheduler.schedule(applicationContext)

        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        setContent {
            KotTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        viewModel.showFlasher -> {
                            FlasherScreen(
                                viewModel = viewModel,
                                onBack = viewModel::closeTask,
                            )
                        }

                        viewModel.showAccess -> {
                            AccessScreen(
                                onRequestRuntimeAccess = ::requestMaximumRuntimeAccess,
                                onAllFiles = { AccessController.openAllFilesAccess(this) },
                                onOverlay = { AccessController.openOverlayAccess(this) },
                                onInstallPackagesAccess = {
                                    AccessController.openInstallUnknownApps(this)
                                },
                                onNotificationAccess = {
                                    AccessController.openNotificationListenerSettings(this)
                                },
                                onAccessibility = {
                                    AccessController.openAccessibilitySettings(this)
                                },
                                onDeviceAdmin = {
                                    AccessController.openDeviceAdmin(this)
                                },
                                onShizukuAccess = ::requestShizukuAccess,
                                onAppSettings = {
                                    AccessController.openAppSettings(this)
                                },
                                onBack = viewModel::closeTask,
                            )
                        }

                        viewModel.selectedTaskId.isBlank() -> {
                            KotMainMenuScreen(
                                viewModel = viewModel,
                                onOpenTask = viewModel::openTask,
                            )
                        }

                        else -> {
                            val task = KotTasks.byId(viewModel.selectedTaskId)
                            if (task == null) {
                                viewModel.closeTask()
                            } else {
                                KotTaskScreen(
                                    viewModel = viewModel,
                                    task = task,
                                    onBack = viewModel::closeTask,
                                    onOpenCamera = ::openCamera,
                                    onListen = ::requestVoice,
                                    onSpeak = ::speak,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            applyTtsStyle()

            textToSpeech?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onError(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        if (viewModel.continuousVoice) {
                            runOnUiThread {
                                if (AccessController.hasMicrophone(this@MainActivity)) {
                                    window.decorView.postDelayed(
                                        { startListening() },
                                        350L,
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    private fun applyTtsStyle() {
        val accent = viewModel.accent.lowercase(Locale.getDefault())

        val preferredLocale = when {
            "узбек" in accent -> Locale("uz", "UZ")
            "таджик" in accent -> Locale("tg", "TJ")
            "турец" in accent -> Locale("tr", "TR")
            "англ" in accent || "english" in accent -> Locale.US
            "чечен" in accent -> Locale("ce", "RU")
            else -> Locale("ru", "RU")
        }

        val result = textToSpeech?.setLanguage(preferredLocale)
        if (
            result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            textToSpeech?.setLanguage(Locale("ru", "RU"))
        }

        val basePitch = when (viewModel.personaMode) {
            PersonaMode.FUNNY_GIRLFRIEND -> 1.12f
            PersonaMode.ADULT_COMPANION -> 1.06f
            PersonaMode.FUNNY_FRIEND -> 0.94f
            else -> 1.0f
        }

        val humorBoost = (viewModel.humorLevel.coerceIn(0, 3) * 0.03f)
        textToSpeech?.setPitch((basePitch + humorBoost).coerceIn(0.75f, 1.35f))
        textToSpeech?.setSpeechRate(
            (1.0f + viewModel.humorLevel.coerceIn(0, 3) * 0.04f)
                .coerceIn(0.85f, 1.25f)
        )
    }

    private fun requestMaximumRuntimeAccess() {
        val autoGranted = AccessController.autoGrantRuntimePermissionsIfDeviceOwner(this)
        val missing = AccessController.missingRuntimePermissions(this)

        if (missing.isEmpty()) {
            viewModel.status = if (autoGranted > 0) {
                "Device Owner: разрешения выданы автоматически"
            } else {
                "Основные разрешения уже выданы"
            }
            return
        }

        accessPermissions.launch(missing)
    }

    private fun requestShizukuAccess() {
        if (!Shizuku.pingBinder()) {
            viewModel.status =
                "Shizuku не запущен. Сначала запусти его через беспроводную отладку."
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            viewModel.status = "Shizuku уже подключён к KOT"
            return
        }

        if (Shizuku.shouldShowRequestPermissionRationale()) {
            viewModel.status =
                "Shizuku ранее получил отказ. Разреши KOT в приложении Shizuku."
            return
        }

        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        viewModel.status = "Запрашиваю доступ Shizuku…"
    }

    private fun openCamera() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            viewModel.status =
                "Камера не разрешена. Открой «Максимальный доступ» и выдай права один раз."
            viewModel.openTask("access")
            return
        }

        runCatching {
            startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        }.onFailure {
            viewModel.status = "Не удалось открыть камеру"
        }
    }

    private fun requestVoice() {
        if (AccessController.hasMicrophone(this)) {
            startListening()
        } else {
            viewModel.status =
                "Микрофон не разрешён. Открой «Максимальный доступ» и выдай права один раз."
            viewModel.showAccess = true
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            viewModel.status = "Распознавание речи недоступно на этом телефоне"
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    viewModel.status = "Слушаю…"
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    viewModel.status = "Распознаю…"
                }

                override fun onError(error: Int) {
                    viewModel.status = "Не расслышал. Нажми «Говорить» ещё раз."
                }

                override fun onResults(results: Bundle?) {
                    val recognizedText = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()

                    if (recognizedText.isBlank()) {
                        viewModel.status = "Не удалось распознать речь"
                        return
                    }

                    if (viewModel.continuousVoice) {
                        val wake = viewModel.wakeWord
                            .trim()
                            .lowercase(Locale.getDefault())

                        val lower = recognizedText
                            .trim()
                            .lowercase(Locale.getDefault())

                        if (wake.isNotBlank() && !lower.startsWith(wake)) {
                            viewModel.status = "Жду слово «" + viewModel.wakeWord + "»"
                            window.decorView.postDelayed(
                                { startListening() },
                                300L,
                            )
                            return
                        }

                        val command = if (wake.isBlank()) {
                            recognizedText.trim()
                        } else {
                            recognizedText
                                .trim()
                                .drop(viewModel.wakeWord.trim().length)
                                .trimStart(' ', ',', ':', '-', '—')
                        }

                        if (command.isBlank()) {
                            viewModel.status = "Слушаю команду…"
                            window.decorView.postDelayed(
                                { startListening() },
                                300L,
                            )
                        } else {
                            viewModel.submit(command, ::speak)
                        }
                    } else {
                        viewModel.submit(recognizedText, ::speak)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun startListening() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            viewModel.status = "Распознавание речи недоступно"
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(
                RecognizerIntent.EXTRA_PREFER_OFFLINE,
                viewModel.selectedTaskMode == ConnectionMode.OFFLINE,
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говори")
        }

        recognizer.startListening(intent)
    }

    private fun speak(text: String) {
        if (text.isBlank()) return

        applyTtsStyle()

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "kot-assistant-reply",
        )
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)

        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val SHIZUKU_REQUEST_CODE = 701
    }
}

@Composable
private fun AssistantScreen(
    viewModel: AssistantViewModel,
    onListen: () -> Unit,
    onSpeak: (String) -> Unit,
    onOpenAccess: () -> Unit,
    onOpenFlasher: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "KOT Assistant",
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text = viewModel.status,
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = viewModel.serverUrl,
            onValueChange = { viewModel.serverUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Адрес AI-сервера") },
            singleLine = true,
            enabled = !viewModel.busy,
        )

        OutlinedTextField(
            value = viewModel.serverToken,
            onValueChange = { viewModel.serverToken = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ключ доступа к серверу") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !viewModel.busy,
        )

        Button(
            onClick = viewModel::saveServerConfig,
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.busy,
        ) {
            Text("Сохранить AI-сервер")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onOpenAccess,
                modifier = Modifier.weight(1f),
                enabled = !viewModel.busy,
            ) {
                Text("Максимальный доступ")
            }

            Button(
                onClick = onOpenFlasher,
                modifier = Modifier.weight(1f),
                enabled = !viewModel.busy,
            ) {
                Text("Прошивка Android")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.42f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(viewModel.messages) { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.role == "user") {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    },
                ) {
                    Card(modifier = Modifier.fillMaxWidth(0.86f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (message.role == "user") "Ты" else "Ассистент",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(text = message.text)

                            if (message.role == "assistant") {
                                Button(
                                    onClick = { onSpeak(message.text) },
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    Text("Озвучить")
                                }
                            }
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = viewModel.input,
            onValueChange = { viewModel.input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Напиши команду") },
            enabled = !viewModel.busy,
            maxLines = 4,
        )

        Button(
            onClick = onListen,
            enabled = !viewModel.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("🎙 Говорить")
        }

        Button(
            onClick = { viewModel.submit(onReply = onSpeak) },
            enabled = !viewModel.busy && viewModel.input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (viewModel.busy) "Жду…" else "Отправить")
        }

        Button(
            onClick = viewModel::clearMemory,
            enabled = !viewModel.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Очистить локальную память")
        }
    }
}


@Composable
private fun AccessScreen(
    onRequestRuntimeAccess: () -> Unit,
    onAllFiles: () -> Unit,
    onOverlay: () -> Unit,
    onInstallPackagesAccess: () -> Unit,
    onNotificationAccess: () -> Unit,
    onAccessibility: () -> Unit,
    onDeviceAdmin: () -> Unit,
    onShizukuAccess: () -> Unit,
    onAppSettings: () -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = "Максимальный доступ KOT",
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        item {
            Text(
                text = "Выдай доступ один раз здесь. После этого KOT не будет повторно спрашивать обычные разрешения во время каждого действия.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        item {
            Button(
                onClick = onRequestRuntimeAccess,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Выдать основные разрешения одним запросом")
            }
        }

        item {
            Button(
                onClick = onAllFiles,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Доступ ко всем файлам")
            }
        }

        item {
            Button(
                onClick = onOverlay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Работа поверх других приложений")
            }
        }

        item {
            Button(
                onClick = onInstallPackagesAccess,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Разрешить установку APK")
            }
        }

        item {
            Button(
                onClick = onNotificationAccess,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Доступ к уведомлениям")
            }
        }

        item {
            Button(
                onClick = onAccessibility,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Управление интерфейсом (Accessibility)")
            }
        }

        item {
            Button(
                onClick = onDeviceAdmin,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Администратор устройства")
            }
        }

        item {
            Button(
                onClick = onShizukuAccess,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Подключить Shizuku")
            }
        }

        item {
            Button(
                onClick = onAppSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Системные настройки доступа KOT")
            }
        }

        item {
            Text(
                text = "Некоторые специальные права Android всё равно включает только через системный экран. После однократного включения KOT может использовать их без повторного запроса.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Назад")
            }
        }
    }
}

@Composable
private fun FlasherScreen(
    viewModel: AssistantViewModel,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "KOT Flasher",
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text = "Прошивка выполняется на компьютере через локальный KOT Flasher Agent. Не отключай USB во время записи.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = viewModel.flasherStatus,
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = viewModel.flasherUrl,
            onValueChange = { viewModel.flasherUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Адрес Flasher Agent, например http://192.168.1.10:8791") },
            singleLine = true,
            enabled = !viewModel.flasherBusy,
        )

        OutlinedTextField(
            value = viewModel.flasherToken,
            onValueChange = { viewModel.flasherToken = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Токен Flasher Agent") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !viewModel.flasherBusy,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = viewModel::saveFlasherConfig,
                modifier = Modifier.weight(1f),
                enabled = !viewModel.flasherBusy,
            ) {
                Text("Сохранить")
            }

            Button(
                onClick = viewModel::refreshFlasherDevices,
                modifier = Modifier.weight(1f),
                enabled = !viewModel.flasherBusy,
            ) {
                Text("Найти телефоны")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.30f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(viewModel.flasherDevices) { device ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = device.model.ifBlank {
                                device.product.ifBlank { "Android-устройство" }
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("Serial: " + device.serial)
                        Text("Режим: " + device.transport + " / " + device.state)
                        if (device.product.isNotBlank()) {
                            Text("Product: " + device.product)
                        }
                        if (device.vendor.isNotBlank()) {
                            Text("Марка: " + device.vendor)
                        }
                        if (device.driver.isNotBlank()) {
                            Text("Драйвер: " + device.driver)
                        }
                        if (device.flashMode.isNotBlank()) {
                            Text("Режим прошивки: " + device.flashMode)
                        }
                        Button(
                            onClick = { viewModel.selectFlasherDevice(device.serial) },
                            modifier = Modifier.padding(top = 8.dp),
                            enabled = !viewModel.flasherBusy,
                        ) {
                            Text(
                                if (viewModel.selectedFlasherSerial == device.serial) {
                                    "Выбрано"
                                } else {
                                    "Выбрать"
                                }
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = viewModel::prepareFlashMode,
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.flasherBusy &&
                viewModel.selectedFlasherSerial.isNotBlank(),
        ) {
            Text("Автоматически перейти в режим прошивки")
        }

        OutlinedTextField(
            value = viewModel.flasherManifestPath,
            onValueChange = { viewModel.flasherManifestPath = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Путь к manifest JSON на компьютере") },
            singleLine = true,
            enabled = !viewModel.flasherBusy,
        )

        Button(
            onClick = viewModel::checkFlashPlan,
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.flasherBusy &&
                viewModel.selectedFlasherSerial.isNotBlank(),
        ) {
            Text("Проверить прошивку без записи")
        }

        OutlinedTextField(
            value = viewModel.flasherConfirmation,
            onValueChange = { viewModel.flasherConfirmation = it },
            modifier = Modifier.fillMaxWidth(),
            label = {
                val serial = viewModel.selectedFlasherSerial
                Text(
                    if (serial.isBlank()) {
                        "Сначала выбери телефон"
                    } else {
                        "Для запуска введи: FLASH " + serial
                    }
                )
            },
            singleLine = true,
            enabled = !viewModel.flasherBusy &&
                viewModel.selectedFlasherSerial.isNotBlank(),
        )

        Button(
            onClick = viewModel::executeFlash,
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.flasherBusy &&
                viewModel.selectedFlasherSerial.isNotBlank() &&
                viewModel.flasherConfirmation ==
                    "FLASH " + viewModel.selectedFlasherSerial,
        ) {
            Text("НАЧАТЬ ПРОШИВКУ")
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.flasherBusy,
        ) {
            Text("Назад")
        }
    }
}
