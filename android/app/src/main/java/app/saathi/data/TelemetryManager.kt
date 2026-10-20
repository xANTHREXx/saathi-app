package app.saathi.data

import android.content.Context
import android.os.Build
import app.saathi.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class TelemetryEvent(
    val name: String,
    val properties: Map<String, Any>,
    val occurredAt: String
)

/**
 * Anonymous usage events, only while she has agreed (Settings.usageStats). The queue file is the
 * single source of truth: every event is written to it at once, so a session cut short loses nothing.
 */
object TelemetryManager {
    private const val QUEUE_FILE = "telemetry_queue.json"
    private const val MAX_CAPACITY = 200
    private const val BATCH_THRESHOLD = 10

    private val lock = Any()
    private val flushing = AtomicBoolean(false)
    private const val RETRY_WAIT_MS = 60_000L
    @Volatile private var lastFailMs = 0L

    /** Time is rounded down to the hour so events can't be lined up with a trip minute by minute. */
    fun createEventRecord(name: String, properties: Map<String, Any>, nowMs: Long = System.currentTimeMillis()): TelemetryEvent =
        TelemetryEvent(name, properties, hourIso(nowMs))

    fun enforceCap(events: List<TelemetryEvent>, maxCapacity: Int = MAX_CAPACITY): List<TelemetryEvent> {
        return if (events.size > maxCapacity) events.takeLast(maxCapacity) else events
    }

    /** The queue after one more event, newest [maxCapacity] kept. */
    fun appendCapped(queue: List<TelemetryEvent>, event: TelemetryEvent, maxCapacity: Int = MAX_CAPACITY): List<TelemetryEvent> =
        enforceCap(queue + event, maxCapacity)

    /**
     * The queue after [sent] (a snapshot taken from its front) went out. Anything added since stays.
     * If the cap trimmed the front meanwhile, only the part of [sent] still in the queue is removed.
     */
    fun removeSent(current: List<TelemetryEvent>, sent: List<TelemetryEvent>): List<TelemetryEvent> {
        for (skipped in 0..sent.size) {
            val remaining = sent.drop(skipped)
            if (current.size >= remaining.size && current.subList(0, remaining.size) == remaining) {
                return current.drop(remaining.size)
            }
        }
        return current
    }

    fun serializeEvents(events: List<TelemetryEvent>): String {
        val array = buildJsonArray {
            for (e in events) {
                add(buildJsonObject {
                    put("name", JsonPrimitive(e.name))
                    put("occurredAt", JsonPrimitive(e.occurredAt))
                    put("properties", buildJsonObject {
                        e.properties.forEach { (k, v) ->
                            when (v) {
                                is Number -> put(k, JsonPrimitive(v))
                                is Boolean -> put(k, JsonPrimitive(v))
                                else -> put(k, JsonPrimitive(v.toString()))
                            }
                        }
                    })
                })
            }
        }
        return array.toString()
    }

    fun parseEvents(text: String): List<TelemetryEvent> = runCatching {
        Json.parseToJsonElement(text).jsonArray.map { e ->
            val obj = e.jsonObject
            val propsObj = obj["properties"]?.jsonObject ?: JsonObject(emptyMap())
            val props = mutableMapOf<String, Any>()
            propsObj.forEach { (k, v) ->
                val prim = v.jsonPrimitive
                val boolVal = if (prim.isString) null else prim.booleanOrNull
                val longVal = if (prim.isString) null else prim.content.toLongOrNull()
                val doubleVal = if (prim.isString) null else prim.content.toDoubleOrNull()
                props[k] = boolVal ?: longVal ?: doubleVal ?: prim.content
            }
            TelemetryEvent(
                obj["name"]?.jsonPrimitive?.content ?: "",
                props,
                obj["occurredAt"]?.jsonPrimitive?.content ?: "",
            )
        }
    }.getOrDefault(emptyList())

    /**
     * Records an event, but only if she has agreed to usage stats. Safe to call from any thread, and it never throws:
     * it runs on the alert path, and nothing here may stop an alert.
     */
    fun track(ctx: Context, name: String, properties: Map<String, Any> = emptyMap()) {
        runCatching { trackOrThrow(ctx, name, properties) }
    }

    private fun trackOrThrow(ctx: Context, name: String, properties: Map<String, Any>) {
        if (!Store.settings(ctx).usageStats) return
        val record = createEventRecord(name, properties)
        val size = synchronized(lock) {
            val queue = appendCapped(readQueue(ctx), record)
            writeQueue(ctx, queue)
            queue.size
        }
        // After a failed send, wait a minute before the next batch attempt so being offline doesn't mean a request per event.
        if (size >= BATCH_THRESHOLD && System.currentTimeMillis() - lastFailMs > RETRY_WAIT_MS) flush(ctx)
    }

    /** Sends everything queued. One flush at a time; on failure the queue is left as it was. */
    fun flush(ctx: Context) {
        if (!flushing.compareAndSet(false, true)) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!Store.settings(ctx).usageStats) {
                    clearQueue(ctx)
                    return@launch
                }
                val toSend = synchronized(lock) { readQueue(ctx) }
                if (toSend.isEmpty()) return@launch

                val payload = Api.buildTelemetryJson(
                    installId = InstallId.get(ctx),
                    versionCode = BuildConfig.VERSION_CODE,
                    versionName = BuildConfig.VERSION_NAME,
                    osVersion = Build.VERSION.SDK_INT,
                    deviceModel = Build.MODEL,
                    crashesJson = "[]",
                    eventsJson = serializeEvents(toSend)
                )

                if (Api.postTelemetry(payload)) {
                    synchronized(lock) {
                        val rest = removeSent(readQueue(ctx), toSend)
                        if (rest.isEmpty()) deleteQueueFile(ctx) else writeQueue(ctx, rest)
                    }
                } else {
                    lastFailMs = System.currentTimeMillis()
                }
            } finally {
                flushing.set(false)
            }
        }
    }

    /** Drops every queued event, for when she turns usage stats off. */
    fun clearQueue(ctx: Context) {
        synchronized(lock) { deleteQueueFile(ctx) }
    }

    private fun readQueue(ctx: Context): List<TelemetryEvent> {
        val file = File(ctx.filesDir, QUEUE_FILE)
        if (!file.exists()) return emptyList()
        return runCatching { parseEvents(file.readText()) }.getOrDefault(emptyList())
    }

    private fun writeQueue(ctx: Context, events: List<TelemetryEvent>) {
        runCatching {
            val file = File(ctx.filesDir, QUEUE_FILE)
            val tmp = File(ctx.filesDir, "$QUEUE_FILE.tmp")
            tmp.writeText(serializeEvents(events))
            if (!tmp.renameTo(file)) {
                file.writeText(serializeEvents(events))
                tmp.delete()
            }
        }
    }

    private fun deleteQueueFile(ctx: Context) {
        runCatching {
            val file = File(ctx.filesDir, QUEUE_FILE)
            if (file.exists()) file.delete()
        }
    }
}
