package app.saathi.data

import android.content.Context
import android.provider.Settings
import app.saathi.BuildConfig
import app.saathi.core.Fix
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Calls to the Saathi backend (one Supabase Edge Function). Every call can fail; callers fall back to what the
 * phone knows. Community calls carry the device id, which the server only ever stores as a keyed hash.
 */
object Api {
    private val http = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()

    /** `rides`: Saathi users who rode it; `reports`: those who said something felt off. Both over 90 days. */
    data class Vehicle(val rides: Int, val reports: Int, val flagged: Boolean, val sample: Boolean)

    fun vehicle(ctx: Context, plate: String): Vehicle? = runCatching {
        get("/community/vehicle?plate=${URLEncoder.encode(plate, "UTF-8")}", device(ctx))?.let {
            Vehicle(it.int("rides"), it.int("reports"), it.bool("flagged"), it.bool("sample"))
        }
    }.getOrNull()

    fun point(ctx: Context, lat: Double, lng: Double, t: Long, kind: String, plate: String?, caseId: String) = runCatching {
        post("/community/point", """{"lat":$lat,"lng":$lng,"t":$t,"kind":${q(kind)},"plate":${plate?.let { q(it) } ?: "null"},"case":${q(caseId)}}""", device(ctx))
    }

    /** One ride in a vehicle, so the next woman sees how many Saathi rides it has had. */
    fun ride(ctx: Context, plate: String, t: Long, caseId: String) = runCatching {
        post("/community/ride", """{"plate":${q(plate)},"t":$t,"case":${q(caseId)}}""", device(ctx))
    }

    /** Removes what this record contributed to community data. */
    fun deleteMine(ctx: Context, caseId: String) = runCatching {
        val req = Request.Builder().url("${BuildConfig.API_URL}/community/mine?case=${URLEncoder.encode(caseId, "UTF-8")}")
            .delete().header("x-saathi-device", device(ctx)).build()
        http.newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    data class Route(val points: List<Fix>, val durationS: Long?)

    /** Expected route and travel time. Null when offline or no route is available (bus, train, no key). */
    fun route(from: Fix, to: Fix, mode: String): Route? = runCatching {
        post("/route", """{"from":{"lat":${from.lat},"lng":${from.lng}},"to":{"lat":${to.lat},"lng":${to.lng}},"mode":${q(mode)}}""")?.let { o ->
            val pts = o["points"]!!.jsonArray.map { e -> e.jsonObject.let { Fix(it["lat"]!!.jsonPrimitive.double, it["lng"]!!.jsonPrimitive.double, 0) } }
            if (pts.size < 2) null else Route(pts, o["duration_s"]?.jsonPrimitive?.longOrNull)
        }
    }.getOrNull()

    data class Reply(val reply: String, val raiseAlert: Boolean)
    data class Pt(val lat: Double, val lng: Double, val band: Int, val kind: String)

    /** One companion turn. Null when offline. */
    fun companion(text: String, lang: String, history: List<Pair<String, String>>): Reply? = runCatching {
        val hist = history.takeLast(6).joinToString(",") { (role, t) -> """{"role":${q(role)},"text":${q(t)}}""" }
        post("/companion/turn", """{"text":${q(text)},"lang":${q(lang)},"history":[$hist]}""")?.let {
            Reply(it["reply"]?.jsonPrimitive?.content ?: "", it["raise_alert"]?.jsonPrimitive?.booleanOrNull ?: false)
        }
    }.getOrNull()

    fun communityPoints(): Pair<Boolean, List<Pt>>? = runCatching {
        get("/community/points")?.let { o ->
            val sample = o["sample"]?.jsonPrimitive?.booleanOrNull ?: false
            val pts = o["points"]!!.jsonArray.map { e ->
                val p = e.jsonObject
                Pt(p["lat"]!!.jsonPrimitive.double, p["lng"]!!.jsonPrimitive.double,
                    p["band"]?.jsonPrimitive?.intOrNull ?: 0, p["kind"]?.jsonPrimitive?.content ?: "report")
            }
            sample to pts
        }
    }.getOrNull()

    private fun q(s: String) = kotlinx.serialization.json.JsonPrimitive(s).toString()
    private fun JsonObject.int(k: String) = this[k]?.jsonPrimitive?.intOrNull ?: 0
    private fun JsonObject.bool(k: String) = this[k]?.jsonPrimitive?.booleanOrNull ?: false

    fun device(ctx: Context): String = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    private fun get(path: String, deviceId: String? = null): JsonObject? {
        val req = Request.Builder().url(BuildConfig.API_URL + path)
        deviceId?.let { req.header("x-saathi-device", it) }
        return http.newCall(req.build()).execute().use { r ->
            if (r.isSuccessful) Json.parseToJsonElement(r.body!!.string()).jsonObject else null
        }
    }

    private fun post(path: String, body: String, deviceId: String? = null): JsonObject? {
        val req = Request.Builder().url(BuildConfig.API_URL + path).post(body.toRequestBody(jsonType))
        deviceId?.let { req.header("x-saathi-device", it) }
        return http.newCall(req.build()).execute().use { r ->
            if (r.isSuccessful) Json.parseToJsonElement(r.body!!.string()).jsonObject else null
        }
    }
}
