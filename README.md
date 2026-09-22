# KOT Assistant

Личный Android‑ассистент в стиле «Jarvis»: голосовой ввод, озвучка ответов, локальная память диалога и подключение к ИИ через собственный сервер.

## Что уже есть

- Android‑приложение на Kotlin + Jetpack Compose.
- Голосовой ввод через системное распознавание речи Android.
- Озвучка ответов через Android TextToSpeech.
- Локальная память последних сообщений на телефоне.
- Поле для адреса собственного backend‑сервера.
- Серверный шлюз на Node.js к OpenAI Responses API.
- API‑ключ хранится только на сервере, а не в APK.
- GitHub Actions автоматически собирает debug APK.
- KOT Flasher Agent для локальной работы с Android через ADB/Fastboot.

## Структура

- `app/` — Android‑приложение.
- `server/` — минимальный backend‑сервер.
- `flasher/` — локальный агент для определения и прошивки Fastboot‑устройств.
- `.github/workflows/android.yml` — автоматическая сборка APK.
- `SECURITY.md` — правила хранения секретов.

## Запуск backend

Нужен Node.js 20 или новее.

```bash
cd server
export OPENAI_API_KEY="ваш_ключ"
export OPENAI_MODEL="gpt-5.6-luna"
npm start
```

Проверка:

```bash
curl http://localhost:8787/health
```

На реальном телефоне укажите в приложении HTTPS‑адрес опубликованного сервера. Для локальной разработки можно использовать адрес компьютера в вашей Wi‑Fi сети.

## KOT Flasher Agent

Саму прошивку нельзя выполнять на Railway: облачный сервер не видит USB‑порт компьютера. Поэтому KOT использует локальный агент из папки `flasher/`.

Текущая версия умеет:

- находить Android‑устройства через ADB и Fastboot;
- читать модель, product/device, Android, fingerprint и батарею;
- проверять состояние Fastboot и bootloader;
- проверять SHA‑256 образов и соответствие product;
- показывать dry‑run план без записи;
- автоматически прошивать перечисленные разделы на совместимом Fastboot‑устройстве;
- при необходимости выполнять wipe и reboot после прошивки.

Перед использованием установите Android Platform Tools и Node.js 20+:

```bash
cd flasher
npm run devices
node kot-flasher.mjs inspect SERIAL
node kot-flasher.mjs plan SERIAL ./firmware.example.json
```

Реальная запись начинается только после явного `--execute`:

```bash
node kot-flasher.mjs flash SERIAL ./firmware.example.json --execute
```

KOT Flasher не обходит FRP, OEM/account lock и не выполняет скрытую разблокировку bootloader. Производительские протоколы вроде Samsung/Odin будут подключаться отдельными драйверами.

Подробности: `flasher/README.md`.

## Сборка Android APK

GitHub Actions собирает APK автоматически после изменений в ветке `main`.

Также проект можно открыть в Android Studio и собрать обычным способом.

## Безопасность

Никогда не вставляйте OpenAI API key прямо в Android‑код, APK, README или публичный репозиторий. Ключ должен находиться только на backend‑сервере.

Для KOT Flasher используйте отдельный длинный `KOT_FLASHER_TOKEN`. Агент по умолчанию слушает только `127.0.0.1`.

## Следующие этапы

1. Добавить защищённую авторизацию телефона к backend.
2. Добавить долговременную структурированную память.
3. Подключить инструменты телефона: уведомления, календарь, контакты и разрешённые действия.
4. Подключить KOT Assistant к локальному KOT Flasher Agent.
5. Добавить отдельные драйверы Samsung/Odin и других vendor‑specific протоколов.
6. Добавить wake‑word/быстрый запуск голосом.
7. Добавить режим непрерывного голосового диалога.
8. Сделать красивый интерфейс и экран настроек.

Проект сейчас находится на рабочем MVP‑этапе: сначала добиваемся стабильной сборки APK, затем наращиваем инструменты.
