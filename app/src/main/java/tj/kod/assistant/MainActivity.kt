package tj.kod.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var viewModel: AssistantViewModel
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private val microphonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startListening()
            } else {
                viewModel.status = "Нужен доступ к микрофону"
            }
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.status = "Автообновления включены"
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
        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (viewModel.showFlasher) {
                        FlasherScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.showFlasher = false },
                        )
                    } else {
                        AssistantScreen(
                            viewModel = viewModel,
                            onListen = ::requestVoice,
                            onSpeak = ::speak,
                            onOpenFlasher = { viewModel.showFlasher = true },
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("ru", "RU"))
            if (
                result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                textToSpeech?.language = Locale.getDefault()
            }
        }
    }

    private fun requestVoice() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startListening()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
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

                    if (recognizedText.isNotBlank()) {
                        viewModel.submit(recognizedText, ::speak)
                    } else {
                        viewModel.status = "Не удалось распознать речь"
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
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говори")
        }

        recognizer.startListening(intent)
    }

    private fun speak(text: String) {
        if (text.isBlank()) return

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "kot-assistant-reply",
        )
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun AssistantScreen(
    viewModel: AssistantViewModel,
    onListen: () -> Unit,
    onSpeak: (String) -> Unit,
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

        Button(
            onClick = onOpenFlasher,
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.busy,
        ) {
            Text("Прошивка Android")
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
