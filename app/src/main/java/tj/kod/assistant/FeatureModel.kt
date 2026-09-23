package tj.kod.assistant

enum class ConnectionMode(
    val label: String,
) {
    AUTO("Авто"),
    ONLINE("Онлайн"),
    OFFLINE("Офлайн"),
}

enum class PersonaMode(
    val label: String,
) {
    NORMAL("Обычный"),
    FUNNY_FRIEND("Смешной друг"),
    FUNNY_GIRLFRIEND("Смешная подруга"),
    ADULT_COMPANION("18+ компаньонка"),
    BUSINESS("Деловой"),
    TEACHER("Учитель"),
    MECHANIC("Механик"),
    BUILDER("Строитель"),
    PROGRAMMER("Программист"),
}

data class KotTask(
    val id: String,
    val title: String,
    val subtitle: String,
    val online: Boolean = true,
    val offline: Boolean = true,
)

object KotTasks {
    val all = listOf(
        KotTask("chat", "Чат и голос", "Разговор, вопросы, голосовые команды"),
        KotTask("web", "Интернет-поиск", "Поиск по сайтам без обязательного AI-сервера"),
        KotTask("code", "Программист", "Генерация, разбор и исправление кода"),
        KotTask("voice", "Офлайн-голос", "Непрерывный диалог, активация словом «Кот» и TTS"),
        KotTask("camera", "Камера и зрение", "Камера, фото, распознавание и анализ"),
        KotTask("image", "Генерация фото", "Создание изображений онлайн или локальной моделью"),
        KotTask("video", "Генерация видео", "Создание видео онлайн или локальной моделью"),
        KotTask("phone", "Управление телефоном", "Приложения, экран, Wi‑Fi, Bluetooth и системные действия"),
        KotTask("files", "Файлы", "Поиск, открытие, APK и локальное хранилище"),
        KotTask("memory", "Память", "Личная долговременная память и история"),
        KotTask("automations", "Автоматические сценарии", "Регулярные и событийные действия"),
        KotTask("autopilot", "Сделай сам", "Несколько локальных действий одной командой"),
        KotTask("models", "Офлайн-модели", "Текст, голос, распознавание, фото и видео"),
        KotTask("profiles", "Профили", "Я, семья, работа, машина и отдельные настройки"),
        KotTask("vault", "Приватная папка", "Зашифрованные локальные данные KOT"),
        KotTask("backup", "Резервная копия", "Экспорт настроек, профилей и памяти"),
        KotTask("updates", "Обновление KOT", "Проверка новой версии и установка поверх текущей"),
        KotTask("flasher", "Прошивка Android", "KOT Flasher и автоматический выбор драйвера"),
        KotTask("access", "Максимальный доступ", "Разрешения, Accessibility и Device Owner"),
    )

    fun byId(id: String): KotTask? = all.firstOrNull { it.id == id }
}
