package app.saathi.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.saathi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    object Idle : DownloadState()
    object Checking : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data class Ready(val file: File) : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

object AppUpdateManager {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isUpdateAvailable(currentVersionCode: Int, release: Api.Release?): Boolean {
        if (release == null) return false
        return release.versionCode > currentVersionCode
    }

    suspend fun checkUpdate(ctx: Context, force: Boolean = false): Api.Release? = withContext(Dispatchers.IO) {
        val prefs = ctx.getSharedPreferences("saathi_updater", Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong("last_check_ms", 0L)
        val now = System.currentTimeMillis()
        val sixHoursMs = 6 * 60 * 60 * 1000L

        if (!force && (now - lastCheck < sixHoursMs)) {
            return@withContext null
        }

        val release = Api.latestRelease()
        prefs.edit().putLong("last_check_ms", now).apply()

        if (isUpdateAvailable(BuildConfig.VERSION_CODE, release)) {
            TelemetryManager.track(ctx, "update_prompted", mapOf(
                "from_version" to BuildConfig.VERSION_CODE,
                "to_version" to (release?.versionCode ?: 0)
            ))
            release
        } else {
            null
        }
    }

    suspend fun downloadApk(
        ctx: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val updatesDir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "saathi-update.apk")
        val startTime = System.currentTimeMillis()

        runCatching {
            val req = Request.Builder().url(apkUrl).build()
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    apkFile.delete()
                    return@withContext null
                }
                val body = response.body ?: return@withContext null
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                val progress = downloadedBytes.toFloat() / totalBytes
                                onProgress(progress.coerceIn(0f, 1f))
                            }
                        }
                        output.flush()
                    }
                }
            }

            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            TelemetryManager.track(ctx, "update_completed", mapOf(
                "download_time_s" to elapsedSec
            ))
            apkFile
        }.getOrElse {
            apkFile.delete()
            null
        }
    }

    fun createInstallIntent(ctx: Context, apkFile: File): Intent {
        val authority = "${ctx.packageName}.provider"
        val uri = FileProvider.getUriForFile(ctx, authority, apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
