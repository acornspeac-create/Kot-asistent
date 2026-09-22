package tj.kod.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class AutomationStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        "kot_automations",
        Context.MODE_PRIVATE,
    )

    fun dailyText(): String =
        prefs.getString(KEY_DAILY_TEXT, "") ?: ""

    fun saveDailyText(text: String) {
        prefs.edit()
            .putString(KEY_DAILY_TEXT, text.trim())
            .apply()
    }

    private companion object {
        const val KEY_DAILY_TEXT = "daily_text"
    }
}

object AutomationScheduler {
    private const val WORK_NAME = "kot_daily_automation"

    fun scheduleDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<KotAutomationWorker>(
            24,
            TimeUnit.HOURS,
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelDaily(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

class KotAutomationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val text = AutomationStore(applicationContext)
            .dailyText()
            .trim()

        if (text.isBlank()) return Result.success()

        val manager = applicationContext
            .getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "KOT Автоматизации",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(
            applicationContext,
            CHANNEL_ID,
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("KOT Assistant")
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(text)
            )
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    private companion object {
        const val CHANNEL_ID = "kot_automations"
        const val NOTIFICATION_ID = 2309
    }
}
