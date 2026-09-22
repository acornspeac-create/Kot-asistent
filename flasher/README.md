# KOT Flasher Agent

Локальный модуль KOT Assistant для безопасной автоматизации прошивки Android-устройств через ADB/Fastboot.

## Почему локальный агент

Railway и другой облачный backend не видят USB-порты вашего компьютера. Поэтому прошивка выполняется только локально на ПК, к которому телефон подключён USB-кабелем.

## Возможности v0.1

- поиск устройств через ADB и Fastboot;
- определение модели, product/device, fingerprint, Android и батареи в ADB;
- чтение product/current-slot/unlocked в Fastboot;
- проверка образов и SHA-256;
- проверка совпадения product с прошивочным манифестом;
- dry-run план без записи;
- автоматическая последовательная прошивка разделов через Fastboot;
- опциональный wipe и reboot;
- локальный HTTP API для будущего управления из KOT Assistant.

## Ограничения безопасности

KOT Flasher не выполняет обход FRP, OEM/account lock и не разблокирует bootloader скрытыми методами. Если производитель требует официальную процедуру разблокировки, её нужно пройти отдельно. На многих устройствах разблокировка bootloader стирает пользовательские данные.

## Подготовка

1. Установите Node.js 20+.
2. Установите Android Platform Tools, чтобы команды `adb` и `fastboot` были доступны в PATH.
3. Подключите телефон хорошим USB-кабелем.
4. Для ADB включите USB debugging и подтвердите RSA-ключ на телефоне.

## Команды

```bash
cd flasher
npm run devices
node kot-flasher.mjs inspect SERIAL
node kot-flasher.mjs plan SERIAL ./firmware.example.json
node kot-flasher.mjs flash SERIAL ./firmware.example.json --execute
```

Сначала всегда запускайте `plan`. Реальная запись начинается только с `--execute`.

## Локальный API

Перед запуском задайте длинный случайный токен.

Linux/macOS:
```bash
export KOT_FLASHER_TOKEN="change-me-to-a-long-random-token"
npm start
```

Windows PowerShell:
```powershell
$env:KOT_FLASHER_TOKEN="change-me-to-a-long-random-token"
npm start
```

Агент слушает только `127.0.0.1:8791`, поэтому по умолчанию недоступен другим устройствам в сети.

Endpoints:
- `GET /health` — проверка агента и наличие ADB/Fastboot.
- `GET /devices` — список устройств (Bearer token).
- `POST /inspect` — сведения об устройстве.
- `POST /reboot` — перезагрузка в system/bootloader/recovery.
- `POST /flash` — dry-run или прошивка по манифесту.

Для реальной прошивки HTTP-запрос должен содержать `execute: true` и строку подтверждения `FLASH SERIAL`.

## Поддержка производителей

v0.1 реально работает с устройствами, использующими стандартный Fastboot и официально разблокированный загрузчик. Samsung/Odin и другие vendor-specific протоколы будут отдельными драйверами, чтобы не смешивать несовместимые способы прошивки.
