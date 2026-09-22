package tj.kod.assistant

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object UpdateScheduler {
    private const val WORK_NAME = "kot-assistant-autonomous-update"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<UpdateWorker>(
            6,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

class UpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            val release = fetchLatestRelease()
            val latestBuild = parseBuildNumber(release.optString("tag_name"))
            val currentBuild = currentBuildNumber()

            if (latestBuild <= currentBuild) {
                return@runCatching
            }

            val assets = release.optJSONArray("assets") ?: return@runCatching
            var apkUrl: String? = null

            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")

                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }

            if (apkUrl.isNullOrBlank()) {
                return@runCatching
            }

            val file = downloadApk(apkUrl, latestBuild)
            val releasePage = release.optString(
                "html_url",
                "https://github.com/acornspeac-create/Kot-asistent/releases/latest",
            )

            showUpdateNotification(
                file = file,
                latestBuild = latestBuild,
                releasePage = releasePage,
            )
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    private fun fetchLatestRelease(): JSONObject {
        val connection = URL(
            "https://api.github.com/repos/acornspeac-create/Kot-asistent/releases/latest"
        ).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty(
                "Accept",
                "application/vnd.github+json",
            )
            connection.setRequestProperty(
                "User-Agent",
                "KOT-Assistant-Android",
            )

            val status = connection.responseCode
            val body = if (status in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (status !in 200..299) {
                error("GitHub release check failed: " + status + " " + body)
            }

            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseBuildNumber(tag: String): Long =
        tag.substringAfterLast('.').toLongOrNull() ?: 0L

    private fun currentBuildNumber(): Long {
        val info = applicationContext.packageManager.getPackageInfo(
            applicationContext.packageName,
            0,
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun downloadApk(
        url: String,
        build: Long,
    ): File {
        require(url.startsWith(
            "https://github.com/acornspeac-create/Kot-asistent/releases/download/"
        )) {
            "Unexpected update host"
        }

        val directory = File(applicationContext.filesDir, "updates").apply {
            mkdirs()
        }

        val target = File(directory, "KOT-Assistant-" + build + ".apk")
        val temp = File(directory, target.name + ".part")

        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.setRequestProperty(
                "User-Agent",
                "KOT-Assistant-Android",
            )

            if (connection.responseCode !in 200..299) {
                error("APK download failed: " + connection.responseCode)
            }

            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (target.exists()) {
                target.delete()
            }

            check(temp.renameTo(target)) {
                "Could not finalize downloaded APK"
            }

            return target
        } finally {
            connection.disconnect()
            if (temp.exists() && !target.exists()) {
                temp.delete()
            }
        }
    }

    private fun showUpdateNotification(
        file: File,
        latestBuild: Long,
        releasePage: String,
    ) {
        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        val channelId = "kot_updates"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "KOT Assistant updates",
                    NotificationManager.IMPORTANCE_HIGH,
                )
            )
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val installIntent = buildInstallIntent(file, releasePage)

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            latestBuild.toInt(),
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(
            applicationContext,
            channelId,
        )
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("KOT Assistant обновился")
            .setContentText(
                "Новая сборка " + latestBuild + " уже скачана. Нажми для установки."
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .build()

        manager.notify(7000 + (latestBuild % 1000).toInt(), notification)
    }

    private fun buildInstallIntent(
        file: File,
        releasePage: String,
    ): Intent {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !applicationContext.packageManager.canRequestPackageInstalls()
        ) {
            return Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + applicationContext.packageName),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val uri = FileProvider.getUriForFile(
            applicationContext,
            applicationContext.packageName + ".fileprovider",
            file,
        )

        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                uri,
                "application/vnd.android.package-archive",
            )
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("release_page", releasePage)
    }
}
