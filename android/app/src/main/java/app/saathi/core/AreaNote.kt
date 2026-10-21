package app.saathi.core

/** One anonymous community point: rounded place and a 3-hour band of the day (IST), 0..7. */
data class Report(val lat: Double, val lng: Double, val band: Int)

private val NIGHT_BANDS = setOf(7, 0) // 9 pm to 3 am

/** What the map says about reports near her: [count] within [radiusM] metres, after 9 pm only if [nightOnly]. */
data class AreaNote(val count: Int, val nightOnly: Boolean, val radiusM: Int)

/**
 * A plain note about reports near her, filled in from counts. No AI. Nothing under 3 reports,
 * and it never says a place is safe. The screen words it in her language.
 */
fun areaNote(points: List<Report>, here: Fix, nightOnly: Boolean, radiusM: Double = 300.0): AreaNote? {
    val n = points.count {
        (!nightOnly || it.band in NIGHT_BANDS) && Geo.distanceM(here, Fix(it.lat, it.lng, 0)) <= radiusM
    }
    if (n < 3) return null
    return AreaNote(n, nightOnly, radiusM.toInt())
}
