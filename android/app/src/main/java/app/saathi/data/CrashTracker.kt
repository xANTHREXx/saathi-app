package app.saathi.data

import android.content.Context
import android.os.Build
import app.saathi.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class CrashRecord(
    val exceptionType: String,
    val message: String?,
    val stackTrace: String,
    val crashedAt: String
)

object CrashTracker {
    private const val CRASH_DIR = "crashes"
    private const val MAX_TRACE_LEN = 8000

    fun formatCrashRecord(t: Throwable): CrashRecord {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val trace = t.stackTraceToString()
        return CrashRecord(
            exceptionType = t.javaClass.simpleName.ifBlank { t.javaClass.name },
            message = t.message,
            stackTrace = truncateStackTrace(trace, MAX_TRACE_LEN),
            crashedAt = isoFormat.format(Date())
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
