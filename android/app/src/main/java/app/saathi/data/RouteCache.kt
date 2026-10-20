package app.saathi.data

import android.content.Context
import app.saathi.core.Fix
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Routes between saved places, kept on the phone, so her regular commute is watched for detours even offline. */
object RouteCache {
    @Serializable
    data class Entry(val points: List<Fix>, val durationS: Long?, val savedAt: Long)

    private const val MAX_AGE_S = 30L * 86400 // roads change; refresh monthly
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun get(ctx: Context, key: String, now: Long = System.currentTimeMillis() / 1000): Entry? =
        all(ctx)[key]?.takeIf { now - it.savedAt < MAX_AGE_S }

    /** Any cached copy, however old: better than no route when offline. */
    @Synchronized
    fun stale(ctx: Context, key: String): Entry? = all(ctx)[key]

    @Synchronized
    fun put(ctx: Context, key: String, e: Entry) = file(ctx).writeText(json.encodeToString(all(ctx) + (key to e)))

    private fun all(ctx: Context): Map<String, Entry> =
        runCatching { json.decodeFromString<Map<String, Entry>>(file(ctx).readText()) }.getOrDefault(emptyMap())

    private fun file(ctx: Context) = File(ctx.filesDir, "routes.json")
}
