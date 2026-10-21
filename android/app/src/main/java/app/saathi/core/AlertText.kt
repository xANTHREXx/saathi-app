package app.saathi.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Reason { NO_RESPONSE, POWER_BUTTON, NOTIFICATION, WIDGET_SOS, HOLD, SPEAK_FOR_ME, COMPANION, PLATE_MISMATCH, QS_TILE }

private fun phrase(r: Reason) = when (r) {
    Reason.NO_RESPONSE -> "hasn't answered a check-in"
    Reason.POWER_BUTTON, Reason.NOTIFICATION, Reason.WIDGET_SOS, Reason.HOLD, Reason.QS_TILE -> "asked for help"
    Reason.SPEAK_FOR_ME -> "used Speak for me"
    Reason.COMPANION -> "asked for help on a call"
    Reason.PLATE_MISMATCH -> "says the vehicle plate doesn't match the booking"
}

private const val SMS_MAX = 320
private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** The alert SMS. Plain words, under 320 characters, always a map link when there is a fix. */
fun alertText(
    name: String,
    reason: Reason,
    plate: String?,
    event: TripEvent?,
    place: String?,
    fix: Fix?,
    fixAgeS: Long,
    liveUrl: String?,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    fun build(withPlace: Boolean): String = buildString {
        append("Saathi alert: $name ${phrase(reason)}.")
        plate?.let { append(" Vehicle $it.") }
        val what = listOfNotNull(event?.detail, place?.takeIf { withPlace }?.let { "near $it" })
        if (what.isNotEmpty()) append(" Trip: ${what.joinToString(" ")}.")
        fix?.let {
            val time = clock.format(Instant.ofEpochSecond(it.t).atZone(zone))
            val stale = if (fixAgeS > 120) " (last known)" else ""
            append(" Location at $time$stale: https://maps.google.com/?q=")
            append(String.format(Locale.US, "%.5f,%.5f", it.lat, it.lng))
        }
        liveUrl?.let { append(" Live: $it") }
    }
    val full = build(withPlace = true)
    return if (full.length <= SMS_MAX) full else build(withPlace = false).take(SMS_MAX)
}

fun okText(name: String) = "Saathi: false alarm, $name is fine."

/** What other Saathi users' rides say about a vehicle: [rides] counted, [noneRaised] true only when no ride reported anything. */
data class VehicleNote(val rides: Int, val noneRaised: Boolean, val sample: Boolean)

/**
 * What other Saathi users' rides say about this vehicle, for her only. Counts, never names, and never "safe":
 * a ride where nothing was reported doesn't prove anything. Reports below the flag threshold aren't shown,
 * but then it doesn't claim "none" either. The screen words it in her language.
 * Null when there's nothing to say or the vehicle is flagged (the flag warning covers that).
 */
fun vehicleNote(rides: Int, reports: Int, flagged: Boolean, sample: Boolean): VehicleNote? {
    if (flagged || rides <= 0) return null
    return VehicleNote(rides, noneRaised = reports == 0, sample = sample)
}
