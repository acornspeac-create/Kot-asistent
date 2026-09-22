package tj.kod.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class MemoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("assistant_memory", Context.MODE_PRIVATE)

    fun load(): List<Message> {
        val raw = prefs.getString(KEY_MESSAGES, "[]") ?: "[]"

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        Message(
                            role = item.optString("role", "assistant"),
                            text = item.optString("text", ""),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun append(message: Message) {
        val messages = (load() + message).takeLast(MAX_MESSAGES)
        val array = JSONArray()

        messages.forEach { item ->
            array.put(
                JSONObject()
                    .put("role", item.role)
                    .put("text", item.text)
            )
        }

        prefs.edit().putString(KEY_MESSAGES, array.toString()).apply()
    }

    fun recentTranscript(limit: Int = 20): String =
        load()
            .takeLast(limit)
            .joinToString("\n") { item -> item.role + ": " + item.text }

    fun exportJson(): JSONArray {
        val array = JSONArray()
        load().forEach { item ->
            array.put(
                JSONObject()
                    .put("role", item.role)
                    .put("text", item.text)
            )
        }
        return array
    }

    fun clear() {
        prefs.edit().remove(KEY_MESSAGES).apply()
    }

    private companion object {
        const val KEY_MESSAGES = "messages"
        const val MAX_MESSAGES = 60
    }
}
