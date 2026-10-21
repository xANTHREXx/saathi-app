package app.saathi.core

/** How long the last sign of life must be in the past before a missing clean stop counts as a kill. */
const val STOPPED_GAP_MS = 3 * 60_000L

/**
 * Did the phone stop Saathi in the middle of a trip? Returns when it last showed signs of life, or
 * null when nothing points to a kill.
 *
 * - [lastHeartbeatMs] is the service's last pulse, 0 if it never pulsed.
 * - [wasOnTrip] is whether that pulse said a trip was running.
 * - [cleanStop] is whether the trip was ended on purpose after that pulse.
 * - [exitedByUser] is what Android says about how the last process ended: true if she swiped the app
 *   away or force-stopped it, false if the system ended it, null when it can't say (before Android 11).
 */
fun stoppedAt(
    lastHeartbeatMs: Long, wasOnTrip: Boolean, cleanStop: Boolean, nowMs: Long, exitedByUser: Boolean?,
): Long? {
    if (lastHeartbeatMs <= 0 || !wasOnTrip || cleanStop) return null
    if (nowMs - lastHeartbeatMs <= STOPPED_GAP_MS) return null
    if (exitedByUser == true) return null
    return lastHeartbeatMs
}

/** Whether a moment was today, yesterday, or earlier, in her time zone: the notice words each differently. */
enum class StoppedDay { TODAY, YESTERDAY, EARLIER }

fun stoppedDay(atMs: Long, nowMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): StoppedDay {
    val at = java.time.Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return when {
        at == today -> StoppedDay.TODAY
        at == today.minusDays(1) -> StoppedDay.YESTERDAY
        else -> StoppedDay.EARLIER
    }
}
