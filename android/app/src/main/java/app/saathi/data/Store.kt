package app.saathi.data

import android.content.Context
import app.saathi.core.Contact
import app.saathi.core.SavedPlace
import app.saathi.core.TripEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Settings(
    val name: String = "",
    val contacts: List<Contact> = emptyList(),
    val places: List<SavedPlace> = emptyList(),
    val phraseKey: String = "touched",
    /** Power-button presses that raise help; 0 is off. Off at first, since many phones use quick presses themselves. */
    val powerPresses: Int = 0,
    val communityConsent: Boolean = false,
    /** Whether she has been asked about community sharing. */
    val consentAsked: Boolean = false,
    val onboarded: Boolean = false,
    /** Send scrubbed crash reports. On unless she turns it off. */
    val crashReports: Boolean = true,
    /** Send anonymous usage events. Off until she agrees. */
    val usageStats: Boolean = false,
    /** A test phone's data is kept out of real numbers. Null until set: debug builds count as test phones, release builds don't. */
    val testDevice: Boolean? = null,
    /** Home's "Two more things" card: she opened a message to her contacts about Saathi. We can't know it was sent. */
    val contactsToldAsked: Boolean = false,
    /** ...and the help tile was added to Quick Settings, or she said it is done. */
    val tileAsked: Boolean = false,
    /** She chose "Not now" on that card. */
    val readyCardDismissed: Boolean = false,
    /** Contacts already sent the "I've added you to Saathi" text from her SIM, by [app.saathi.core.introKey]. */
    val introduced: List<String> = emptyList(),
    /** During a trip, text her contacts when the battery reaches 5% or the phone is switched off. */
    val dyingPhoneText: Boolean = true,
    /** The "Check on me" length she picked last, in minutes. */
    val checkMinutes: Int = 20,
    /** Text her contacts when a trip reaches the saved place it was headed to. Off: a routine text every trip teaches them to skim. */
    val arrivalText: Boolean = false,
    /** On a trip, three quick presses of the earphone button ask for help (5 s to undo). Off until she turns it on. */
    val headsetHelp: Boolean = false,
)

@Serializable
data class Evidence(val name: String, val sha256: String, val receivedAt: Long)

/** One alert in a trip's history: when it went out, who was told, and whether it was her manual "Try again". */
@Serializable
data class AlertLogEntry(val at: Long, val told: List<String>, val retry: Boolean = false)

private val LEGACY_ALERT = Regex("""^([A-Z_]+) at (\d+): told ?(.*)$""")

/** Records from before 0.3 kept raw log lines ("NO_RESPONSE at 1792604360: told Chinu"); read them back as entries. */
fun legacyAlertEntry(line: String): AlertLogEntry? {
    val m = LEGACY_ALERT.matchEntire(line.trim()) ?: return null
    val told = m.groupValues[3].split(",").map { it.trim() }.filter { it.isNotEmpty() }
    return AlertLogEntry(m.groupValues[2].toLong(), told, retry = m.groupValues[1] == "RETRY")
}

@Serializable
data class TripRecord(
    val caseId: String,
    val createdAt: Long,
    val mode: String,
    val app: String? = null,
    val plate: String? = null,
    val events: List<String> = emptyList(),
    val alerts: List<String> = emptyList(),
    /** The same events and alerts as data, so the trips list can word them in her language. Empty on older records. */
    val eventItems: List<TripEvent> = emptyList(),
    val alertEntries: List<AlertLogEntry> = emptyList(),
    val plateCheck: String? = null,
    val evidence: List<Evidence> = emptyList(),
    val seal: String? = null,
    val feltOff: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val endedAt: Long? = null,
    /** Saved-place labels the trip started at and was headed to, when known. */
    val from: String? = null,
    val to: String? = null,
)

/** Small JSON files in the app's private storage. */
object Store {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun settings(ctx: Context): Settings =
        runCatching { json.decodeFromString<Settings>(file(ctx, "settings.json").readText()) }
            .getOrDefault(Settings())

    fun save(ctx: Context, s: Settings) = file(ctx, "settings.json").writeText(json.encodeToString(s))

    fun records(ctx: Context): List<TripRecord> =
        runCatching { json.decodeFromString<List<TripRecord>>(file(ctx, "records.json").readText()) }
            .getOrDefault(emptyList())

    fun saveRecord(ctx: Context, r: TripRecord) {
        val all = records(ctx).filterNot { it.caseId == r.caseId } + r
        file(ctx, "records.json").writeText(json.encodeToString(all))
    }

    fun deleteRecord(ctx: Context, caseId: String) {
        file(ctx, "records.json").writeText(json.encodeToString(records(ctx).filterNot { it.caseId == caseId }))
    }

    private fun file(ctx: Context, name: String) = File(ctx.filesDir, name)
}
