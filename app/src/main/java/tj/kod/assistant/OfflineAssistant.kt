package tj.kod.assistant

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class OfflineAssistant {
    fun reply(
        text: String,
        messages: List<Message>,
        persona: PersonaMode = PersonaMode.NORMAL,
        humorLevel: Int = 0,
    ): String {
        val clean = text.trim()
        val lower = clean.lowercase(Locale.getDefault())

        calculate(clean)?.let { result ->
            return result
        }

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

        if (
            lower.startsWith("привет") ||
            lower.startsWith("здравств") ||
            lower == "хай"
        ) {
            return "Я здесь. Сейчас работаю в офлайн-режиме."
        }

        if (
            lower.contains("что ты умеешь") ||
            lower.contains("помощь") ||
            lower == "команды"
        ) {
            return "Офлайн я помню локальный диалог, могу отвечать на простые команды, показывать время и дату, считать базовые примеры и работать голосом. Для сложных вопросов и интернета подключаю сервер."
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
                "Последнее сохранённое сообщение: " + previous.text
            } else {
                "В локальной памяти пока нет предыдущего сообщения."
            }
        }

        if (lower.startsWith("запомни ")) {
            return "Запомнил локально. Это сообщение сохранено в памяти телефона."
        }

        return when (persona) {
            PersonaMode.FUNNY_FRIEND -> {
                val extra = if (humorLevel >= 2) {
                    " Но я на месте — можем хотя бы не дать скуке победить без боя."
                } else {
                    ""
                }
                "Брат, я сейчас полностью офлайн. Сообщение запомнил." + extra
            }

            PersonaMode.FUNNY_GIRLFRIEND -> {
                val extra = if (humorLevel >= 2) {
                    " И да, скучать в мою смену запрещено."
                } else {
                    ""
                }
                "Я рядом и сейчас работаю офлайн. Всё сохранила." + extra
            }

            PersonaMode.ADULT_COMPANION ->
                "Я рядом. Сейчас работаю офлайн и могу поддержать лёгкий взрослый, флиртующий разговор в пределах локальных возможностей."

            PersonaMode.BUSINESS ->
                "Офлайн-режим активен. Запрос сохранён. Для расширенного анализа нужен локальный ИИ-модуль или онлайн-сервер."

            PersonaMode.TEACHER ->
                "Я офлайн. Запрос сохранил; простые вещи могу разбирать локально, а для сложного объяснения нужна локальная модель или сервер."

            PersonaMode.MECHANIC ->
                "Я офлайн. Запрос по технике сохранил. Для подробной диагностики подключи локальную модель или онлайн-режим."

            PersonaMode.BUILDER ->
                "Я офлайн. Задачу по строительству сохранил. Базовые расчёты доступны локально."

            PersonaMode.PROGRAMMER ->
                "Офлайн-режим. Команду сохранил; простые локальные действия доступны, для полноценной генерации кода нужна локальная модель или сервер."

            PersonaMode.NORMAL ->
                "Сейчас я офлайн. Я сохранил твоё сообщение в локальной памяти. Для полного ИИ-ответа нужен установленный локальный ИИ или доступ к серверу."
        }
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
