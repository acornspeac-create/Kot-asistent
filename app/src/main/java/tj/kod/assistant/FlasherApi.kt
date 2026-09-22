package tj.kod.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class FlasherDevice(
    val transport: String,
    val serial: String,
    val state: String,
    val model: String,
    val product: String,
    val vendor: String,
    val driver: String,
    val flashMode: String,
)

class FlasherApi {
    suspend fun devices(
        agentUrl: String,
        agentToken: String,
    ): List<FlasherDevice> = withContext(Dispatchers.IO) {
        val body = request(
            agentUrl = agentUrl,
            agentToken = agentToken,
            method = "GET",
            path = "/devices",
        )

        val devices = JSONObject(body).optJSONArray("devices") ?: JSONArray()
        buildList {
            for (i in 0 until devices.length()) {
                val item = devices.optJSONObject(i) ?: continue
                add(
                    FlasherDevice(
                        transport = item.optString("transport"),
                        serial = item.optString("serial"),
                        state = item.optString("state"),
                        model = item.optString("model"),
                        product = item.optString("product"),
                        vendor = item.optString("vendor"),
                        driver = item.optString("driver"),
                        flashMode = item.optString("flashMode"),
                    )
                )
            }
        }
    }

    suspend fun rebootToFlashMode(
        agentUrl: String,
        agentToken: String,
        serial: String,
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("serial", serial)
            .put("target", "flash")

        val raw = request(
            agentUrl = agentUrl,
            agentToken = agentToken,
            method = "POST",
            path = "/reboot",
            payload = payload,
        )

        val json = JSONObject(raw)
        if (json.optBoolean("ok")) {
            "Телефон переведён в режим прошивки."
        } else {
            json.optString("error").ifBlank { raw }
        }
    }

    suspend fun flash(
        agentUrl: String,
        agentToken: String,
        serial: String,
        manifestPath: String,
        execute: Boolean,
        confirmation: String = "",
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("serial", serial)
            .put("manifestPath", manifestPath)
            .put("execute", execute)

        if (execute) {
            payload.put("confirmation", confirmation)
        }

        val raw = request(
            agentUrl = agentUrl,
            agentToken = agentToken,
            method = "POST",
            path = "/flash",
            payload = payload,
        )

        val json = JSONObject(raw)
        when {
            json.optBoolean("dryRun") -> {
                val plan = json.optJSONArray("plan")
                "План проверен. Команд прошивки: " + (plan?.length() ?: 0)
            }
            json.optBoolean("ok") -> "Прошивка завершена."
            else -> json.optString("error").ifBlank { raw }
        }
    }

    private fun request(
        agentUrl: String,
        agentToken: String,
        method: String,
        path: String,
        payload: JSONObject? = null,
    ): String {
        require(agentUrl.isNotBlank()) { "Укажи адрес KOT Flasher Agent." }
        require(agentToken.isNotBlank()) { "Укажи токен KOT Flasher Agent." }

        val connection = URL(agentUrl.trimEnd('/') + path)
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 240_000
            connection.setRequestProperty(
                "Authorization",
                "Bearer " + agentToken,
            )
            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8",
            )

            if (payload != null) {
                connection.doOutput = true
                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(body).optString("error")
                }.getOrDefault("").ifBlank { body }

                error("Flasher Agent вернул " + status + ": " + message)
            }

            return body
        } finally {
            connection.disconnect()
        }
    }
}
