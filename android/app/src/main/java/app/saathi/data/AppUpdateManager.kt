package app.saathi.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.util.Log
import androidx.core.content.FileProvider
import app.saathi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** What came of a download: only [Verified] may be installed. */
sealed class DownloadResult {
    /** The file's SHA-256 matched the one the server published. */
    data class Verified(val file: File) : DownloadResult()
    /** The file arrived but its SHA-256 differs. It has been deleted. */
    object Mismatch : DownloadResult()
    /** The network or disk failed. */
    object Failed : DownloadResult()
}

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val UPDATE_FILE = "saathi-update.apk"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isUpdateAvailable(currentVersionCode: Int, release: Api.Release?): Boolean {
        if (release == null) return false
        return release.versionCode > currentVersionCode
    }

    // ---- pure helpers (unit-tested) ----

    /** 64 lowercase hex characters, the shape of a SHA-256 digest. */
    fun isValidSha256(s: String): Boolean = s.length == 64 && s.all { it in '0'..'9' || it in 'a'..'f' }

    /** The APK must come over https from where this build says releases live. */
    fun isTrustedUrl(url: String, prefix: String): Boolean =
        prefix.startsWith("https://") && url.startsWith(prefix) && url.length > prefix.length

    /** Why a release can't be trusted, or null when it can. */
    fun rejectionReason(release: Api.Release, urlPrefix: String): String? = when {
        !isValidSha256(release.apkSha256) -> "apkSha256 is not 64 lowercase hex characters"
        !isTrustedUrl(release.apkUrl, urlPrefix) -> "apkUrl is outside the trusted prefix"
        else -> null
    }

    /** Streams [input] through SHA-256 and returns lowercase hex. Doesn't close the stream. */
    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun fileSha256Hex(file: File): String = file.inputStream().use { sha256Hex(it) }

    // ---- check ----

    /**
     * Success(null): nothing to install (up to date, throttled, or a release we refuse to trust).
     * Failure: the check itself didn't work; the throttle clock is left alone so the next launch tries again.
     */
    suspend fun checkUpdate(ctx: Context, force: Boolean = false): Result<Api.Release?> = withContext(Dispatchers.IO) {
        val prefs = ctx.getSharedPreferences("saathi_updater", Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong("last_check_ms", 0L)
        val now = System.currentTimeMillis()
        val sixHoursMs = 6 * 60 * 60 * 1000L

        if (!force && (now - lastCheck < sixHoursMs)) {
            return@withContext Result.success(null)
        }

        val release = Api.latestRelease().getOrElse { return@withContext Result.failure(it) }
        prefs.edit().putLong("last_check_ms", now).apply()

        if (release == null || !isUpdateAvailable(BuildConfig.VERSION_CODE, release)) {
            return@withContext Result.success(null)
        }
        val reason = rejectionReason(release, BuildConfig.UPDATE_URL_PREFIX)
        if (reason != null) {
            Log.w(TAG, "Ignoring release ${release.versionName}: $reason")
            return@withContext Result.success(null)
        }
        TelemetryManager.track(ctx, "update_prompted", mapOf(
            "from_version" to BuildConfig.VERSION_CODE,
            "to_version" to release.versionCode
        ))
        Result.success(release)
    }

    // ---- download and verify ----

    private fun updatesDir(ctx: Context) = File(ctx.cacheDir, "updates").apply { mkdirs() }

    /** A previously downloaded file that still matches [release]'s hash; a stale or partial one is deleted. */
    suspend fun verifiedFile(ctx: Context, release: Api.Release): File? = withContext(Dispatchers.IO) {
        val file = File(updatesDir(ctx), UPDATE_FILE)
        if (!file.exists()) return@withContext null
        val ok = runCatching { fileSha256Hex(file) == release.apkSha256 }.getOrDefault(false)
        if (ok) file else { file.delete(); null }
    }

    /** Downloads, then checks the SHA-256 against [Api.Release.apkSha256]. Never returns an unverified file. */
    suspend fun downloadApk(
        ctx: Context,
        release: Api.Release,
        onProgress: (Float) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        val dir = updatesDir(ctx)
        val apkFile = File(dir, UPDATE_FILE)
        val partFile = File(dir, "$UPDATE_FILE.part")
        apkFile.delete()
        val startTime = System.currentTimeMillis()

        try {
            val req = Request.Builder().url(release.apkUrl).build()
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext DownloadResult.Failed
                val body = response.body ?: return@withContext DownloadResult.Failed
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                onProgress((downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f))
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (fileSha256Hex(partFile) != release.apkSha256) {
                Log.w(TAG, "Downloaded APK failed its SHA-256 check; deleted")
                partFile.delete()
                return@withContext DownloadResult.Mismatch
            }
            if (!partFile.renameTo(apkFile)) {
                partFile.delete()
                return@withContext DownloadResult.Failed
            }

            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            TelemetryManager.track(ctx, "update_completed", mapOf(
                "download_time_s" to elapsedSec
            ))
            DownloadResult.Verified(apkFile)
        } catch (t: Throwable) {
            partFile.delete()
            // A cancelled download (dialog closed) must stay cancelled.
            if (t is kotlinx.coroutines.CancellationException) throw t
            DownloadResult.Failed
        }
    }

    fun isMetered(ctx: Context): Boolean = runCatching {
        ctx.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
    }.getOrDefault(false)

    fun createInstallIntent(ctx: Context, apkFile: File): Intent {
        val authority = "${ctx.packageName}.provider"
        val uri = FileProvider.getUriForFile(ctx, authority, apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
