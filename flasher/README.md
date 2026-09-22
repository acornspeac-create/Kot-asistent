# KOT Flasher Agent

Локальный модуль KOT Assistant для автоматической прошивки Android-устройств через ADB, Fastboot и Samsung Download Mode.

## Что умеет v0.2

- поиск устройств через ADB, Fastboot и Heimdall;
- определение производителя, модели, product/device, Android и батареи;
- автоматический выбор драйвера;
- Samsung → Download Mode + Heimdall;
- Xiaomi/Redmi/POCO, Pixel/Google, OnePlus, Motorola, OPPO, realme, vivo/iQOO, Nothing, Sony, Huawei/Honor и другие совместимые устройства → Fastboot;
- автоматический переход из ADB в нужный режим прошивки;
- проверка SHA-256 образов;
- проверка vendor/product/model из manifest;
- dry-run план без записи;
- прошивка разделов и reboot;
- Fastboot wipe, если он явно включён в manifest.

## Подготовка

Установите:

1. Node.js 20+.
2. Android Platform Tools (`adb` и `fastboot`).
3. Для Samsung — Heimdall, доступный как команда `heimdall`.

Подключите телефон USB-кабелем. Для определения модели в обычном Android включите USB debugging и один раз подтвердите RSA-ключ компьютера.

## Команды

```bash
cd flasher
npm run devices
node kot-flasher.mjs inspect SERIAL
node kot-flasher.mjs plan SERIAL ./firmware.example.json
node kot-flasher.mjs flash SERIAL ./firmware.example.json --execute
```

## Автоматический выбор режима

Если телефон ещё загружен в Android, API `POST /reboot` с `target: "flash"` автоматически выбирает:

- Samsung → `adb reboot download`;
- остальные поддерживаемые Fastboot-устройства → `adb reboot bootloader`.

Для Samsung KOT сначала запоминает модель и product через ADB, затем после перехода в Download Mode использует эти данные для проверки manifest.

## Локальный API

Linux/macOS:

```bash
export KOT_FLASHER_TOKEN="change-me-to-a-long-random-token"
export KOT_FLASHER_HOST="0.0.0.0"
npm start
```

Windows PowerShell:

```powershell
$env:KOT_FLASHER_TOKEN="change-me-to-a-long-random-token"
$env:KOT_FLASHER_HOST="0.0.0.0"
npm start
```

По умолчанию агент слушает `127.0.0.1:8791`. Для управления с телефона в той же Wi‑Fi сети задайте `KOT_FLASHER_HOST=0.0.0.0`.

Endpoints:

- `GET /health` — состояние агента и установленных драйверов;
- `GET /drivers` — список доступных драйверов;
- `GET /devices` — найденные устройства;
- `POST /inspect` — подробные сведения об устройстве;
- `POST /reboot` — system/recovery/bootloader/download/flash;
- `POST /flash` — dry-run или реальная прошивка.

Реальная прошивка по HTTP требует `execute: true` и подтверждение `FLASH SERIAL`.

## Ограничения

KOT не обходит FRP, аккаунт-блокировки и OEM-защиту загрузчика. Если конкретная модель требует официальной разблокировки bootloader, она должна быть выполнена штатным способом производителя.

Samsung-прошивка через Heimdall выполняется только если KOT успел определить модель/product через ADB до перехода в Download Mode.
