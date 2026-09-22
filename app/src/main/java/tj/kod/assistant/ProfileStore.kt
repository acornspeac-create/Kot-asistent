package tj.kod.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class KotProfile(
    val id: String,
    val name: String,
)

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        "kot_profiles",
        Context.MODE_PRIVATE,
    )

    fun profiles(): List<KotProfile> {
        val saved = prefs.getString(KEY_PROFILES, null)
        if (saved.isNullOrBlank()) {
            return defaultProfiles()
        }

        return runCatching {
            val array = JSONArray(saved)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        KotProfile(
                            id = item.getString("id"),
                            name = item.getString("name"),
                        )
                    )
                }
            }
        }.getOrElse { defaultProfiles() }
    }

    fun activeProfileId(): String =
        prefs.getString(KEY_ACTIVE, "me") ?: "me"

    fun setActiveProfile(id: String) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    fun addProfile(name: String): KotProfile {
        val clean = name.trim().ifBlank { "Новый профиль" }
        val profile = KotProfile(
            id = "profile_" + System.currentTimeMillis(),
            name = clean,
        )

        val updated = profiles() + profile
        saveProfiles(updated)
        return profile
    }

    fun exportJson(): JSONObject {
        val array = JSONArray()
        profiles().forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
            )
        }

        return JSONObject()
            .put("activeProfileId", activeProfileId())
            .put("profiles", array)
    }

    private fun saveProfiles(profiles: List<KotProfile>) {
        val array = JSONArray()
        profiles.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
            )
        }

        prefs.edit()
            .putString(KEY_PROFILES, array.toString())
            .apply()
    }

    private fun defaultProfiles(): List<KotProfile> = listOf(
        KotProfile("me", "Я"),
        KotProfile("family", "Семья"),
        KotProfile("work", "Работа"),
        KotProfile("car", "Машина"),
    )

    private companion object {
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE = "active_profile"
    }
}
