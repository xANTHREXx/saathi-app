package app.saathi.data

import android.content.Context
import android.os.Build
import app.saathi.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

data class CrashRecord(
    val exceptionType: String,
    val message: String?,
    val stackTrace: String,
    val crashedAt: String
)

object CrashTracker {
    private const val CRASH_DIR = "crashes"
    private const val MAX_TRACE_LEN = 8000

    /** Saved-but-unsent crashes are dropped after this long when reports are off. */
    private const val KEEP_MS = 7L * 24 * 60 * 60 * 1000

    /** The record that is saved and sent: message and trace scrubbed, time rounded down to the hour. */
    fun formatCrashRecord(t: Throwable, nowMs: Long = System.currentTimeMillis()): CrashRecord {
        val trace = scrub(t.stackTraceToString())
        return CrashRecord(
            exceptionType = t.javaClass.simpleName.ifBlank { t.javaClass.name },
            message = t.message?.let { scrub(it) },
            stackTrace = truncateStackTrace(trace, MAX_TRACE_LEN),
            crashedAt = hourIso(nowMs)
        )
    }

    fun truncateStackTrace(trace: String, maxLen: Int = MAX_TRACE_LEN): String {
        return if (trace.length > maxLen) trace.substring(0, maxLen) else trace
    }

    fun install(ctx: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                saveCrashSync(ctx, throwable)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashSync(ctx: Context, t: Throwable) {
        val record = formatCrashRecord(t)
        val dir = File(ctx.filesDir, CRASH_DIR).apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}.json")
        val json = kotlinx.serialization.json.buildJsonObject {
            put("exceptionType", kotlinx.serialization.json.JsonPrimitive(record.exceptionType))
            put("message", record.message?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
            put("stackTrace", kotlinx.serialization.json.JsonPrimitive(record.stackTrace))
            put("crashedAt", kotlinx.serialization.json.JsonPrimitive(record.crashedAt))
        }
        file.writeText(json.toString())
    }

    fun flushPending(ctx: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val dir = File(ctx.filesDir, CRASH_DIR)
                if (!dir.exists()) return@launch
                val files = dir.listFiles { f -> f.extension == "json" } ?: return@launch
                if (files.isEmpty()) return@launch

                // Reports are off: send nothing. Keep a week of crashes in case she turns them on, then let go.
                if (!Store.settings(ctx).crashReports) {
                    val cutoff = System.currentTimeMillis() - KEEP_MS
                    files.filter { it.lastModified() < cutoff }.forEach { it.delete() }
                    return@launch
                }

                val installId = InstallId.get(ctx)
                val crashesArray = StringBuilder("[")
                files.forEachIndexed { index, file ->
                    if (index > 0) crashesArray.append(",")
                    crashesArray.append(file.readText())
                }
                crashesArray.append("]")

                val payload = Api.buildTelemetryJson(
                    installId = installId,
                    versionCode = BuildConfig.VERSION_CODE,
                    versionName = BuildConfig.VERSION_NAME,
                    osVersion = Build.VERSION.SDK_INT,
                    deviceModel = Build.MODEL,
                    crashesJson = crashesArray.toString(),
                    eventsJson = "[]"
                )

                if (Api.postTelemetry(payload)) {
                    files.forEach { it.delete() }
                }
            }
        }
    }
}
