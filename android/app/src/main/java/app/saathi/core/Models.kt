package app.saathi.core

import kotlinx.serialization.Serializable

/** One location reading. [t] is epoch seconds. */
@Serializable
data class Fix(val lat: Double, val lng: Double, val t: Long)

@Serializable
enum class Mode { AUTO, CAB, BIKE, WALK, BUS, TRAIN }

@Serializable
enum class EventKind { LONG_STOP, OFF_ROUTE, LATE }

@Serializable
data class TripEvent(val kind: EventKind, val detail: String, val at: Long)

/** A place she goes often (home, work, college), set once. Used to guess where a trip is headed. */
@Serializable
data class SavedPlace(val label: String, val lat: Double, val lng: Double) {
    val fix get() = Fix(lat, lng, 0)
}
