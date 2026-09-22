package tj.kod.assistant

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Locale

class OwnerActionExecutor(
    private val context: Context,
) {
    private val packageManager = context.packageManager

    fun tryExecute(text: String): String? {
        val clean = text.trim()
        val lower = clean.lowercase(Locale.getDefault())

        when {
            lower == "домой" ||
                lower == "на главный экран" ||
                lower == "главный экран" -> {
                return global(
                    AccessibilityService.GLOBAL_ACTION_HOME,
                    "Открыл главный экран.",
                )
            }

            lower == "назад" ||
                lower == "вернись назад" -> {
                return global(
                    AccessibilityService.GLOBAL_ACTION_BACK,
                    "Вернулся назад.",
                )
            }

            lower.contains("последние приложения") ||
                lower == "недавние" -> {
                return global(
                    AccessibilityService.GLOBAL_ACTION_RECENTS,
                    "Открыл последние приложения.",
                )
            }

            lower.contains("быстрые настройки") -> {
                return global(
                    AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
                    "Открыл быстрые настройки.",
                )
            }

            lower == "уведомления" ||
                lower.contains("открой уведомления") -> {
                return global(
                    AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
                    "Открыл уведомления.",
                )
            }

            lower.contains("заблокируй экран") ||
                lower.contains("заблокировать экран") -> {
                return lockScreen()
            }

            lower == "открой настройки" ||
                lower == "настройки" -> {
                openIntent(Intent(Settings.ACTION_SETTINGS))
                return "Открыл настройки."
            }

            lower.contains("открой wi-fi") ||
                lower.contains("открой wifi") ||
                lower == "wifi" ||
                lower == "wi-fi" -> {
                openIntent(Intent(Settings.ACTION_WIFI_SETTINGS))
                return "Открыл настройки Wi‑Fi."
            }

            lower.contains("открой bluetooth") ||
                lower == "bluetooth" -> {
                openIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                return "Открыл настройки Bluetooth."
            }

            lower.contains("режим владельца") ||
                lower.contains("owner mode") -> {
                return ownerStatus()
            }

            lower.startsWith("открой ") -> {
                val requested = clean.substringAfter(" ", "").trim()
                if (requested.isNotBlank()) {
                    return openInstalledApp(requested)
                }
            }
        }

        return null
    }

    private fun global(action: Int, successText: String): String {
        val service = KotAccessibilityService.instance
            ?: return "Accessibility для KOT не включён. Включи его один раз в «Максимальный доступ»."

        return if (service.performGlobalAction(action)) {
            successText
        } else {
            "Android не выполнил это глобальное действие."
        }
    }

    private fun lockScreen(): String {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
            ?: return "DevicePolicyManager недоступен."

        val admin = ComponentName(
            context,
            KotDeviceAdminReceiver::class.java,
        )

        if (!dpm.isAdminActive(admin)) {
            return "Сначала включи KOT как администратора устройства в «Максимальный доступ»."
        }

        return runCatching {
            dpm.lockNow()
            "Экран заблокирован."
        }.getOrElse {
            "Не удалось заблокировать экран: " +
                (it.message ?: it::class.java.simpleName)
        }
    }

    private fun ownerStatus(): String {
        val owner = AccessController.isDeviceOwner(context)
        val accessibility = KotAccessibilityService.instance != null
        val missing = AccessController.missingRuntimePermissions(context).size

        return buildString {
            append("Режим владельца KOT: ")
            append(if (owner) "Device Owner активен" else "обычный режим")
            append(". Accessibility: ")
            append(if (accessibility) "включён" else "выключен")
            append(". Не выдано обычных разрешений: ")
            append(missing)
            append(".")
        }
    }

    private fun openInstalledApp(requested: String): String {
        val query = requested.lowercase(Locale.getDefault())

        val candidates = packageManager
            .getInstalledApplications(0)
            .mapNotNull { app ->
                val launch = packageManager.getLaunchIntentForPackage(app.packageName)
                    ?: return@mapNotNull null

                val label = runCatching {
                    packageManager.getApplicationLabel(app).toString()
                }.getOrDefault(app.packageName)

                Triple(app.packageName, label, launch)
            }

        val exact = candidates.firstOrNull {
            it.first.equals(requested, ignoreCase = true) ||
                it.second.equals(requested, ignoreCase = true)
        }

        val contains = candidates.firstOrNull {
            it.second.lowercase(Locale.getDefault()).contains(query) ||
                it.first.lowercase(Locale.getDefault()).contains(query)
        }

        val match = exact ?: contains
            ?: return "Не нашёл установленное приложение «$requested»."

        openIntent(match.third)

        return "Открыл " + match.second + "."
    }

    private fun openIntent(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
