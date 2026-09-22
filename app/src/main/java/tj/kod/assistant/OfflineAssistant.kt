package tj.kod.assistant

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class OfflineAssistant {
    fun reply(
        text: String,
        messages: List<Message>,
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

        return "Сейчас я офлайн. Я сохранил твоё сообщение в локальной памяти. Для полного ИИ-ответа и поиска в интернете нужен доступ к настроенному серверу."
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
