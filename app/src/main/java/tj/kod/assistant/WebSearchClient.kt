package tj.kod.assistant

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLDecoder

data class WebSearchResult(
    val title: String,
    val snippet: String,
    val url: String,
)

class WebSearchClient {
    suspend fun search(
        query: String,
        limit: Int = 5,
    ): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()

        val document = Jsoup.connect("https://html.duckduckgo.com/html/")
            .data("q", clean)
            .userAgent(
                "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                    "Chrome/140.0 Mobile Safari/537.36 KOT-Assistant"
            )
            .referrer("https://duckduckgo.com/")
            .timeout(20_000)
            .get()

        document.select(".result")
            .mapNotNull { result ->
                val link = result.selectFirst(".result__a")
                    ?: return@mapNotNull null
                val title = link.text().trim()
                val href = normalizeDuckUrl(link.attr("href"))
                val snippet = result.selectFirst(".result__snippet")
                    ?.text()
                    ?.trim()
                    .orEmpty()

                if (title.isBlank() || href.isBlank()) {
                    null
                } else {
                    WebSearchResult(
                        title = title,
                        snippet = snippet,
                        url = href,
                    )
                }
            }
            .distinctBy { it.url }
            .take(limit.coerceIn(1, 8))
    }

    fun toPromptContext(results: List<WebSearchResult>): String =
        results.mapIndexed { index, item ->
            buildString {
                append(index + 1)
                append(". ")
                appendLine(item.title)
                if (item.snippet.isNotBlank()) {
                    appendLine(item.snippet)
                }
                append("Источник: ")
                append(item.url)
            }
        }.joinToString("\n\n")

    fun renderForUser(results: List<WebSearchResult>): String {
        if (results.isEmpty()) {
            return "Поиск не вернул результатов. Попробуй сформулировать запрос иначе."
        }

        return buildString {
            appendLine("Нашёл в интернете:")
            results.forEachIndexed { index, item ->
                appendLine()
                append(index + 1)
                append(". ")
                appendLine(item.title)
                if (item.snippet.isNotBlank()) {
                    appendLine(item.snippet)
                }
                appendLine(item.url)
            }
        }.trim()
    }

    private fun normalizeDuckUrl(raw: String): String {
        if (raw.isBlank()) return ""

        val absolute = when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> "https://duckduckgo.com$raw"
            else -> raw
        }

        return runCatching {
            val uri = Uri.parse(absolute)
            val redirected = uri.getQueryParameter("uddg")
            if (redirected.isNullOrBlank()) {
                absolute
            } else {
                URLDecoder.decode(redirected, "UTF-8")
            }
        }.getOrDefault(absolute)
    }
}
