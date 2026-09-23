package tj.kod.assistant

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder

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

        directSite(clean)?.let {
            return@withContext listOf(it)
        }

        val capped = limit.coerceIn(1, 8)

        val providers = listOf<suspend () -> List<WebSearchResult>>(
            { searchBingRss(clean, capped) },
            { searchDuckDuckGoHtml(clean, capped) },
            { searchDuckDuckGoLite(clean, capped) },
        )

        var lastError: Throwable? = null

        for (provider in providers) {
            val result = runCatching {
                provider()
            }.onFailure {
                lastError = it
            }.getOrDefault(emptyList())

            if (result.isNotEmpty()) {
                return@withContext result
            }
        }

        if (lastError != null) {
            throw IllegalStateException(
                "Все поисковые источники недоступны: " +
                    (lastError?.message ?: "неизвестная ошибка"),
                lastError,
            )
        }

        emptyList()
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
            return "Поиск не вернул результатов."
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

    private fun searchBingRss(
        query: String,
        limit: Int,
    ): List<WebSearchResult> {
        val url =
            "https://www.bing.com/search?format=rss&q=" +
                URLEncoder.encode(query, "UTF-8")

        val document = Jsoup.connect(url)
            .ignoreContentType(true)
            .userAgent(USER_AGENT)
            .timeout(20_000)
            .get()

        return document.select("item")
            .mapNotNull { item ->
                val title = item.selectFirst("title")
                    ?.text()
                    ?.trim()
                    .orEmpty()
                val link = item.selectFirst("link")
                    ?.text()
                    ?.trim()
                    .orEmpty()
                val description = item.selectFirst("description")
                    ?.text()
                    ?.trim()
                    .orEmpty()

                if (title.isBlank() || link.isBlank()) {
                    null
                } else {
                    WebSearchResult(
                        title = title,
                        snippet = description,
                        url = link,
                    )
                }
            }
            .distinctBy { it.url }
            .take(limit)
    }

    private fun searchDuckDuckGoHtml(
        query: String,
        limit: Int,
    ): List<WebSearchResult> {
        val document = Jsoup.connect("https://html.duckduckgo.com/html/")
            .data("q", query)
            .userAgent(USER_AGENT)
            .referrer("https://duckduckgo.com/")
            .timeout(20_000)
            .get()

        return document.select(".result")
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
            .take(limit)
    }

    private fun searchDuckDuckGoLite(
        query: String,
        limit: Int,
    ): List<WebSearchResult> {
        val url =
            "https://lite.duckduckgo.com/lite/?q=" +
                URLEncoder.encode(query, "UTF-8")

        val document = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .referrer("https://duckduckgo.com/")
            .timeout(20_000)
            .get()

        return document.select("a.result-link, a.result-link-extra")
            .mapNotNull { link ->
                val title = link.text().trim()
                val href = normalizeDuckUrl(link.attr("href"))
                if (title.isBlank() || href.isBlank()) {
                    null
                } else {
                    WebSearchResult(
                        title = title,
                        snippet = "",
                        url = href,
                    )
                }
            }
            .distinctBy { it.url }
            .take(limit)
    }

    private fun directSite(query: String): WebSearchResult? {
        val key = query
            .lowercase()
            .trim(' ', '.', ',', '!', '?')

        return when (key) {
            "ютуб", "youtube" -> WebSearchResult(
                title = "YouTube",
                snippet = "Официальный сайт YouTube",
                url = "https://www.youtube.com/",
            )
            "гугл", "google" -> WebSearchResult(
                title = "Google",
                snippet = "Поиск Google",
                url = "https://www.google.com/",
            )
            "тикток", "tiktok" -> WebSearchResult(
                title = "TikTok",
                snippet = "Официальный сайт TikTok",
                url = "https://www.tiktok.com/",
            )
            "инстаграм", "instagram" -> WebSearchResult(
                title = "Instagram",
                snippet = "Официальный сайт Instagram",
                url = "https://www.instagram.com/",
            )
            else -> null
        }
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

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
                "Chrome/140.0 Mobile Safari/537.36 KOT-Assistant"
    }
}
