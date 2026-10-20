package app.saathi.data

import android.content.Context
import android.os.Build
import app.saathi.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class TelemetryEvent(
    val name: String,
    val properties: Map<String, Any>,
    val occurredAt: String
)

object TelemetryManager {
    private const val QUEUE_FILE = "telemetry_queue.json"
    private const val MAX_CAPACITY = 200
    private const val BATCH_THRESHOLD = 10

    private val inMemoryBuffer = mutableListOf<TelemetryEvent>()
    private val lock = Any()

    fun createEventRecord(name: String, properties: Map<String, Any>): TelemetryEvent {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return TelemetryEvent(name, properties, isoFormat.format(Date()))
    }

    fun enforceCap(events: List<TelemetryEvent>, maxCapacity: Int = MAX_CAPACITY): List<TelemetryEvent> {
        return if (events.size > maxCapacity) events.takeLast(maxCapacity) else events
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

    fun track(ctx: Context, name: String, properties: Map<String, Any> = emptyMap()) {
        val record = createEventRecord(name, properties)
        var shouldFlush = false
        synchronized(lock) {
            inMemoryBuffer.add(record)
            if (inMemoryBuffer.size >= BATCH_THRESHOLD) {
                shouldFlush = true
            }
        }
        if (shouldFlush) {
            flush(ctx)
        }
    }

    fun flush(ctx: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val toSend: List<TelemetryEvent>
            synchronized(lock) {
                if (inMemoryBuffer.isEmpty()) {
                    loadQueueFromDisk(ctx)
                }
                if (inMemoryBuffer.isEmpty()) return@launch
                val capped = enforceCap(inMemoryBuffer, MAX_CAPACITY)
                inMemoryBuffer.clear()
                toSend = capped.toList()
            }

            val installId = InstallId.get(ctx)
            val eventsJson = serializeEvents(toSend)
            val payload = Api.buildTelemetryJson(
                installId = installId,
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                osVersion = Build.VERSION.SDK_INT,
                deviceModel = Build.MODEL,
                crashesJson = "[]",
                eventsJson = eventsJson
            )

            val success = Api.postTelemetry(payload)
            if (!success) {
                saveQueueToDisk(ctx, toSend)
            } else {
                clearDiskQueue(ctx)
            }
        }
    }

    private fun loadQueueFromDisk(ctx: Context) {
        val file = File(ctx.filesDir, QUEUE_FILE)
        if (!file.exists()) return
        runCatching {
            val element = Json.parseToJsonElement(file.readText()).jsonArray
            for (e in element) {
                val obj = e.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: ""
                val occurredAt = obj["occurredAt"]?.jsonPrimitive?.content ?: ""
                val propsObj = obj["properties"]?.jsonObject ?: JsonObject(emptyMap())
                val props = mutableMapOf<String, Any>()
                propsObj.forEach { (k, v) ->
                    val prim = v.jsonPrimitive
                    val boolVal = prim.booleanOrNull
                    val longVal = prim.content.toLongOrNull()
                    val doubleVal = prim.content.toDoubleOrNull()
                    when {
                        boolVal != null -> props[k] = boolVal
                        longVal != null -> props[k] = longVal
                        doubleVal != null -> props[k] = doubleVal
                        else -> props[k] = prim.content
                    }
                }
                inMemoryBuffer.add(TelemetryEvent(name, props, occurredAt))
            }
        }
    }

    private fun saveQueueToDisk(ctx: Context, failedBatch: List<TelemetryEvent>) {
        runCatching {
            synchronized(lock) {
                inMemoryBuffer.addAll(0, failedBatch)
                val capped = enforceCap(inMemoryBuffer, MAX_CAPACITY)
                inMemoryBuffer.clear()
                inMemoryBuffer.addAll(capped)
                val file = File(ctx.filesDir, QUEUE_FILE)
                file.writeText(serializeEvents(capped))
            }
        }
    }

    private fun clearDiskQueue(ctx: Context) {
        runCatching {
            val file = File(ctx.filesDir, QUEUE_FILE)
            if (file.exists()) file.delete()
        }
    }
}
