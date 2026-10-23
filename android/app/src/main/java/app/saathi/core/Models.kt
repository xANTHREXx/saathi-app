package app.saathi.core

import kotlinx.serialization.Serializable

/** One location reading. [t] is epoch seconds. */
@Serializable
data class Fix(val lat: Double, val lng: Double, val t: Long)

@Serializable
enum class Mode { AUTO, CAB, BIKE, WALK, BUS, TRAIN }

/** SHAKE is no longer produced (the shake gesture was removed), but stays so trip records that stored it still decode. */
@Serializable
enum class EventKind { LONG_STOP, OFF_ROUTE, LATE, TIMER, SHAKE }

/**
 * What the watcher noticed. [detail] is plain English, kept for the alert SMS, the live page and old records;
 * the app's own screens word it from [kind] and [value] (minutes, or metres for OFF_ROUTE) in her language.
 */
@Serializable
data class TripEvent(val kind: EventKind, val detail: String, val at: Long, val value: Int = 0)

/** A place she goes often (home, work, college), set once. Used to guess where a trip is headed. */
@Serializable
data class SavedPlace(
    val label: String,
    val lat: Double,
    val lng: Double,
    /** The place in words, e.g. "12th Main, Indiranagar, Bengaluru". */
    val address: String? = null,
    /** Saved from her current location. */
    val fromLocation: Boolean = false,
) {
    val fix get() = Fix(lat, lng, 0)
}
