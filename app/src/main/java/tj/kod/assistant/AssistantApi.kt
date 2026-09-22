package tj.kod.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AssistantApi {
    suspend fun ask(
        serverUrl: String,
        text: String,
        history: String,
    ): String = withContext(Dispatchers.IO) {
        if (serverUrl.isBlank()) {
            return@withContext "Сначала укажи адрес сервера в поле сверху и нажми «Сохранить сервер»."
        }

        val endpoint = URL(serverUrl.trimEnd('/') + "/assistant")
        val connection = endpoint.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")

            val payload = JSONObject()
                .put("text", text)
                .put("history", history)
                .toString()

            connection.outputStream.use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                error("Сервер вернул " + status + ": " + body)
            }

            JSONObject(body).optString("text").ifBlank {
                "Сервер ответил без текста."
            }
        } finally {
            connection.disconnect()
        }
    }
}
