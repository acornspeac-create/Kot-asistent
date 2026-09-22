package tj.kod.assistant

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

class OfflineAssistant {
    fun reply(
        text: String,
        messages: List<Message>,
        persona: PersonaMode = PersonaMode.NORMAL,
        humorLevel: Int = 0,
    ): String {
        val clean = text.trim()
        val lower = clean.lowercase(Locale.getDefault())

        calculate(clean)?.let { return it }

        if (
            lower.contains("который час") ||
            lower == "время" ||
            lower.contains("сколько времени")
        ) {
            return "Сейчас " + LocalTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm")
            ) + "."
        }

        if (
            lower == "дата" ||
            lower.contains("какая сегодня дата") ||
            lower.contains("какое сегодня число")
        ) {
            return "Сегодня " + LocalDate.now().format(
                DateTimeFormatter.ofPattern("dd.MM.yyyy")
            ) + "."
        }

        if (isGreeting(lower)) {
            return when (persona) {
                PersonaMode.FUNNY_FRIEND ->
                    if (humorLevel >= 2) "Привет, брат! Я здесь. Что сегодня разнесём по задачам?"
                    else "Привет! Я здесь. Чем займёмся?"
                PersonaMode.FUNNY_GIRLFRIEND ->
                    if (humorLevel >= 2) "Привет! Я на связи. Ну что, спасаем день от скуки?"
                    else "Привет! Я здесь. Что хочешь сделать?"
                PersonaMode.BUSINESS -> "Привет. Я готов. Какая задача первая?"
                else -> "Привет! Я здесь. Что будем делать?"
            }
        }

        if (isHowAreYou(lower)) {
            return when (persona) {
                PersonaMode.FUNNY_FRIEND ->
                    if (humorLevel >= 2) "Нормально, процессор не дымится — уже успех. А ты как?"
                    else "Всё нормально. А ты как?"
                PersonaMode.FUNNY_GIRLFRIEND ->
                    if (humorLevel >= 2) "Отлично. Настроение рабочее, скуку сегодня не пропускаем. А ты как?"
                    else "Хорошо. А у тебя как дела?"
                PersonaMode.BUSINESS -> "В рабочем режиме. Что нужно решить?"
                else -> "Всё хорошо, я на месте. А ты как?"
            }
        }

        if (isWhatAreYouDoing(lower)) {
            return when (persona) {
                PersonaMode.FUNNY_FRIEND ->
                    if (humorLevel >= 2) "Жду твою следующую команду и делаю вид, что не скучал."
                    else "Жду твою следующую задачу."
                PersonaMode.BUSINESS -> "Готов обрабатывать следующую задачу."
                else -> "Сейчас общаюсь с тобой и жду следующую задачу."
            }
        }

        if (
            lower == "спасибо" ||
            lower == "спс" ||
            lower.startsWith("спасибо ")
        ) {
            return when (persona) {
                PersonaMode.FUNNY_FRIEND -> "Всегда пожалуйста. Погнали дальше."
                PersonaMode.BUSINESS -> "Пожалуйста. Готов к следующей задаче."
                else -> "Пожалуйста! Что ещё сделать?"
            }
        }

        if (
            lower.contains("мне скучно") ||
            lower == "скучно" ||
            lower.contains("развесели")
        ) {
            return when (persona) {
                PersonaMode.FUNNY_FRIEND ->
                    "Тогда так: либо я кидаю тебе короткую шутку, либо придумываем безумную, но полезную задачу. Выбирай."
                PersonaMode.FUNNY_GIRLFRIEND ->
                    "Скуку отменяем. Могу пошутить, придумать игру на двоих в чате или просто поболтать."
                else ->
                    "Могу развлечь: шутка, мини-игра, загадка или просто разговор. Что выбираешь?"
            }
        }

        if (
            lower.contains("кто ты") ||
            lower.contains("как тебя зовут") ||
            lower == "ты кто"
        ) {
            return "Я KOT — твой личный ассистент. Могу работать с текстом, голосом, локальной памятью, задачами телефона и ИИ-моделью, если она установлена."
        }

        if (
            lower.contains("ты онлайн") ||
            lower.contains("ты офлайн") ||
            lower.contains("есть интернет")
        ) {
            return "Сейчас этот ответ сформирован локально. Режим подключения KOT выбирается отдельно для каждой задачи."
        }

        if (
            lower.contains("что ты умеешь") ||
            lower.contains("что умеешь") ||
            lower == "команды"
        ) {
            return "Могу общаться, помнить локальный диалог, считать, работать с голосом, выполнять разрешённые действия на телефоне и подключать локальный или серверный ИИ. Скажи задачу обычными словами."
        }

        if (
            lower.contains("последнее сообщение") ||
            lower.contains("что я сказал") ||
            lower.contains("что я говорил")
        ) {
            val previous = messages
                .asReversed()
                .firstOrNull { it.role == "user" && it.text != clean }

            return if (previous != null) {
                "Ты до этого написал: «" + previous.text + "»."
            } else {
                "Предыдущего сообщения в локальной памяти пока нет."
            }
        }

        if (lower.startsWith("запомни ")) {
            return "Запомнил это в локальной истории диалога."
        }

        if (
            lower.startsWith("повтори") ||
            lower.contains("что ты сказал")
        ) {
            val previousAssistant = messages
                .asReversed()
                .firstOrNull { it.role == "assistant" }

            return previousAssistant?.text
                ?: "У меня пока нет предыдущего ответа, который можно повторить."
        }

        if (
            lower.contains("погода") ||
            lower.contains("курс валют") ||
            lower.contains("новости сегодня")
        ) {
            return "Для актуальных данных нужен интернет. Я не буду придумывать текущую информацию."
        }

        return fallback(
            clean = clean,
            messages = messages,
            persona = persona,
            humorLevel = humorLevel,
        )
    }

    private fun isGreeting(lower: String): Boolean {
        val value = lower.trim(' ', '!', '?', '.', ',')
        return value == "привет" ||
            value == "хай" ||
            value == "салам" ||
            value.startsWith("здравств") ||
            value.startsWith("доброе утро") ||
            value.startsWith("добрый день") ||
            value.startsWith("добрый вечер")
    }

    private fun isHowAreYou(lower: String): Boolean {
        val value = lower.trim(' ', '!', '?', '.', ',')
        return value == "как ты" ||
            value == "как дела" ||
            value == "как у тебя дела" ||
            value == "как поживаешь" ||
            value == "как жизнь"
    }

    private fun isWhatAreYouDoing(lower: String): Boolean {
        val value = lower.trim(' ', '!', '?', '.', ',')
        return value == "что делаешь" ||
            value == "чем занимаешься" ||
            value == "что сейчас делаешь"
    }

    private fun fallback(
        clean: String,
        messages: List<Message>,
        persona: PersonaMode,
        humorLevel: Int,
    ): String {
        val lastUser = messages
            .asReversed()
            .firstOrNull { it.role == "user" && it.text != clean }
            ?.text
            ?.take(120)

        val options = when (persona) {
            PersonaMode.FUNNY_FRIEND -> listOf(
                "Понял тебя. Дай чуть больше деталей — разберём это нормально, а не ответом из трёх слов.",
                "Смысл уловил. Уточни, что именно хочешь получить на выходе, и я продолжу.",
                if (humorLevel >= 2)
                    "Я в теме, но тут мне нужен ещё один кусочек контекста, иначе начну гадать как сосед у подъезда."
                else
                    "Я понял направление. Уточни один момент, чтобы ответ был точнее.",
            )

            PersonaMode.FUNNY_GIRLFRIEND -> listOf(
                "Поняла. Дай мне ещё немного деталей, и отвечу по существу.",
                "Я с тобой. Уточни, что именно хочешь узнать или сделать.",
                if (humorLevel >= 2)
                    "Почти поймала мысль. Ещё одна деталь — и не придётся играть в телепата."
                else
                    "Уточни немного, чтобы я не додумывала за тебя.",
            )

            PersonaMode.BUSINESS -> listOf(
                "Запрос понял. Уточни ожидаемый результат.",
                "Нужно немного больше данных. Что именно должно получиться в итоге?",
                "Уточни ключевое условие задачи, и продолжу.",
            )

            PersonaMode.TEACHER -> listOf(
                "Давай разберём. Сформулируй, что именно непонятно, и я объясню по шагам.",
                "Уточни вопрос чуть конкретнее — так объяснение получится полезнее.",
                "Я понял тему. Напиши, какой именно момент нужно объяснить.",
            )

            PersonaMode.MECHANIC -> listOf(
                "Чтобы не гадать, дай симптомы, модель и что уже проверяли.",
                "Понял направление. Нужны ещё детали по машине или неисправности.",
                "Опиши проблему подробнее: что происходит, когда и при каких условиях.",
            )

            PersonaMode.BUILDER -> listOf(
                "Нужны размеры и что именно хочешь получить — тогда посчитаю или предложу решение.",
                "Понял задачу. Дай размеры, материал или фото, если они важны.",
                "Уточни размеры и цель работы, чтобы ответ был точным.",
            )

            PersonaMode.PROGRAMMER -> listOf(
                "Дай код, ошибку или ожидаемое поведение — разберу точнее.",
                "Понял задачу. Нужен фрагмент кода или точный результат, который ожидаешь.",
                "Уточни стек, ошибку и что должно происходить.",
            )

            PersonaMode.ADULT_COMPANION,
            PersonaMode.NORMAL -> listOf(
                "Понял. Уточни немного, что именно хочешь узнать или сделать.",
                "Я тебя услышал. Дай ещё одну деталь, и продолжим по делу.",
                "Могу помочь с этим. Скажи, какой результат тебе нужен.",
            )
        }

        val seed = clean.hashCode() + messages.size + (lastUser?.hashCode() ?: 0)
        return options[seed.absoluteValue % options.size]
    }

    private fun calculate(text: String): String? {
        val normalized = text
            .replace(',', '.')
            .replace('×', '*')
            .replace('÷', '/')
            .trim()

        val match = Regex(
            """^\s*(-?\d+(?:\.\d+)?)\s*([+\-*/])\s*(-?\d+(?:\.\d+)?)\s*=?\s*$"""
        ).matchEntire(normalized) ?: return null

        val left = match.groupValues[1].toDoubleOrNull() ?: return null
        val operator = match.groupValues[2]
        val right = match.groupValues[3].toDoubleOrNull() ?: return null

        val value = when (operator) {
            "+" -> left + right
            "-" -> left - right
            "*" -> left * right
            "/" -> {
                if (right == 0.0) {
                    return "На ноль делить нельзя."
                }
                left / right
            }
            else -> return null
        }

        val printable = if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.6f", value)
                .trimEnd('0')
                .trimEnd('.')
        }

        return "Ответ: " + printable
    }
}
