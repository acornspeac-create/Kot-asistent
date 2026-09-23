package tj.kod.assistant

object CoreEditPlanner {
    private val sourceRoot =
        "app/src/main/java/tj/kod/assistant/"

    fun candidatePaths(instruction: String): List<String> {
        val lower = instruction.lowercase()
        val selected = linkedSetOf<String>()

        Regex("""[A-Za-z0-9_./-]+\.(?:kt|kts|xml|yml|yaml|md|mjs|json)""")
            .findAll(instruction)
            .map { it.value }
            .filter { '/' in it || it.endsWith(".kt") }
            .forEach { raw ->
                val path = if ('/' in raw) {
                    raw
                } else {
                    sourceRoot + raw
                }
                selected += path
            }

        if (
            listOf("дизайн", "интерфейс", "тема", "цвет", "кноп", "экран", "ui")
                .any { it in lower }
        ) {
            selected += sourceRoot + "KotTheme.kt"
            selected += sourceRoot + "KotMenuScreens.kt"
        }

        if (
            listOf("интернет", "поиск", "сайт", "web", "брауз")
                .any { it in lower }
        ) {
            selected += sourceRoot + "WebSearchClient.kt"
            selected += sourceRoot + "OfflineAssistant.kt"
        }

        if (
            listOf("памят", "контекст", "запомина", "истори")
                .any { it in lower }
        ) {
            selected += sourceRoot + "MemoryStore.kt"
            selected += sourceRoot + "OfflineAssistant.kt"
        }

        if (
            listOf("интеллект", "умнее", "ответ", "модель", "рассуж")
                .any { it in lower }
        ) {
            selected += sourceRoot + "OfflineAssistant.kt"
            selected += sourceRoot + "LocalAiEngine.kt"
        }

        if (
            listOf("обнов", "apk", "релиз", "установ")
                .any { it in lower }
        ) {
            selected += sourceRoot + "UpdateWorker.kt"
        }

        if (
            listOf("програм", "код", "проект", "github", "самоусоверш")
                .any { it in lower }
        ) {
            selected += sourceRoot + "CoreProjectClient.kt"
            selected += sourceRoot + "CoreEditPlanner.kt"
        }

        if (selected.isEmpty()) {
            selected += sourceRoot + "OfflineAssistant.kt"
            selected += sourceRoot + "LocalAiEngine.kt"
            selected += sourceRoot + "KotTheme.kt"
        }

        return selected.take(3)
    }
}
