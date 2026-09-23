package tj.kod.assistant

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CoreRepoFile(
    val path: String,
    val sha: String,
    val content: String,
)

data class CoreBuildState(
    val runNumber: Long,
    val status: String,
    val conclusion: String,
)

class CoreProjectClient {
    suspend fun createBackupBranch(
        repository: String,
        branch: String,
        token: String,
    ): String = withContext(Dispatchers.IO) {
        requireWriteAccess(repository, token)

        val head = headSha(repository, branch, token)
        val timestamp = SimpleDateFormat(
            "yyyyMMdd-HHmmss",
            Locale.US,
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val backup = "kot-backup-$timestamp"

        requestJson(
            method = "POST",
            url = api(repository, "/git/refs"),
            token = token,
            body = JSONObject()
                .put("ref", "refs/heads/$backup")
                .put("sha", head),
        )

        backup
    }

    suspend fun readFile(
        repository: String,
        branch: String,
        path: String,
        token: String,
    ): CoreRepoFile? = withContext(Dispatchers.IO) {
        requireRepository(repository)
        requireSafePath(path)

        val response = request(
            method = "GET",
            url = api(
                repository,
                "/contents/${encodePath(path)}?ref=${encode(branch)}",
            ),
            token = token,
            allowNotFound = true,
        )

        if (response.status == 404) {
            return@withContext null
        }

        val json = JSONObject(response.body)
        val encoded = json.optString("content")
            .replace("\n", "")
            .replace("\r", "")

        CoreRepoFile(
            path = path,
            sha = json.getString("sha"),
            content = String(
                Base64.decode(encoded, Base64.DEFAULT),
                Charsets.UTF_8,
            ),
        )
    }

    suspend fun writeFile(
        repository: String,
        branch: String,
        path: String,
        content: String,
        currentSha: String?,
        message: String,
        token: String,
    ) = withContext(Dispatchers.IO) {
        requireWriteAccess(repository, token)
        requireSafePath(path)

        val body = JSONObject()
            .put(
                "content",
                Base64.encodeToString(
                    content.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                ),
            )
            .put("message", message.take(120))
            .put("branch", branch)

        if (!currentSha.isNullOrBlank()) {
            body.put("sha", currentSha)
        }

        requestJson(
            method = "PUT",
            url = api(repository, "/contents/${encodePath(path)}"),
            token = token,
            body = body,
        )
    }

    suspend fun deleteFile(
        repository: String,
        branch: String,
        path: String,
        currentSha: String,
        message: String,
        token: String,
    ) = withContext(Dispatchers.IO) {
        requireWriteAccess(repository, token)
        requireSafePath(path)

        requestJson(
            method = "DELETE",
            url = api(repository, "/contents/${encodePath(path)}"),
            token = token,
            body = JSONObject()
                .put("message", message.take(120))
                .put("sha", currentSha)
                .put("branch", branch),
        )
    }

    suspend fun latestBuild(
        repository: String,
        branch: String,
        token: String,
    ): CoreBuildState? = withContext(Dispatchers.IO) {
        requireRepository(repository)

        val response = request(
            method = "GET",
            url = api(
                repository,
                "/actions/runs?branch=${encode(branch)}&per_page=1",
            ),
            token = token,
        )

        val runs = JSONObject(response.body)
            .optJSONArray("workflow_runs")
            ?: return@withContext null

        if (runs.length() == 0) {
            return@withContext null
        }

        val run = runs.getJSONObject(0)
        CoreBuildState(
            runNumber = run.optLong("run_number"),
            status = run.optString("status"),
            conclusion = run.optString("conclusion"),
        )
    }

    private fun headSha(
        repository: String,
        branch: String,
        token: String,
    ): String {
        val response = request(
            method = "GET",
            url = api(
                repository,
                "/git/ref/heads/${encode(branch)}",
            ),
            token = token,
        )

        return JSONObject(response.body)
            .getJSONObject("object")
            .getString("sha")
    }

    private fun requireWriteAccess(
        repository: String,
        token: String,
    ) {
        requireRepository(repository)
        require(token.isNotBlank()) {
            "GitHub token не указан"
        }
    }

    private fun requireRepository(repository: String) {
        require(isValidRepository(repository)) {
            "Репозиторий должен быть в формате owner/name"
        }
    }

    private fun requireSafePath(path: String) {
        require(path.isNotBlank()) { "Путь файла пустой" }
        require(!path.startsWith("/")) { "Путь должен быть относительно репозитория" }
        require(".." !in path.split('/')) { "Путь не должен содержать .." }
    }

    private fun api(
        repository: String,
        suffix: String,
    ): String =
        "https://api.github.com/repos/$repository$suffix"

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")

    private fun encodePath(path: String): String =
        path.split('/')
            .joinToString("/") { encode(it) }

    private fun requestJson(
        method: String,
        url: String,
        token: String,
        body: JSONObject,
    ): JSONObject {
        val response = request(
            method = method,
            url = url,
            token = token,
            body = body.toString(),
        )

        if (response.body.isBlank()) return JSONObject()
        return JSONObject(response.body)
    }

    private fun request(
        method: String,
        url: String,
        token: String,
        body: String? = null,
        allowNotFound: Boolean = false,
    ): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.setRequestProperty(
                "Accept",
                "application/vnd.github+json",
            )
            connection.setRequestProperty(
                "X-GitHub-Api-Version",
                "2022-11-28",
            )
            connection.setRequestProperty(
                "User-Agent",
                "KOT-Assistant-Core",
            )

            if (token.isNotBlank()) {
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $token",
                )
            }

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8",
                )
                connection.outputStream.use {
                    it.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val text = stream
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (
                status !in 200..299 &&
                !(allowNotFound && status == 404)
            ) {
                val message = runCatching {
                    JSONObject(text).optString("message")
                }.getOrDefault(text)

                error("GitHub $status: $message")
            }

            return HttpResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResponse(
        val status: Int,
        val body: String,
    )

    companion object {
        fun isValidRepository(value: String): Boolean =
            Regex("""^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$""")
                .matches(value.trim())
    }
}
