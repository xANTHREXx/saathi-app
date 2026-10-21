package app.saathi.core

/**
 * Has she reached where the trip was headed? [needed] fixes in a row within [radiusM] of [dest], and at least
 * [minTripS] into the trip, so a jittery first fix near a saved place doesn't end a trip that just began.
 * One fix outside the radius starts the count again.
 */
class Arrival(
    private val dest: Fix,
    private val radiusM: Double = 150.0,
    private val needed: Int = 2,
    private val minTripS: Long = 120,
) {
    private var inside = 0

    /** [tripStartS] is the trip's first fix time, on the same clock as [fix]. True once she has arrived. */
    fun add(fix: Fix, tripStartS: Long): Boolean {
        if (Geo.distanceM(fix, dest) > radiusM) { inside = 0; return false }
        inside++
        return inside >= needed && fix.t - tripStartS >= minTripS
    }
}
