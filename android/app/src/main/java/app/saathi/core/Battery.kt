package app.saathi.core

/**
 * Low battery during a trip: fires once when the level first reaches [thresholdPct] while not charging.
 * [reset] at trip start arms it again.
 */
class BatteryWatch(private val thresholdPct: Int = 5) {
    private var fired = false

    fun onLevel(pct: Int, charging: Boolean): Boolean {
        if (fired || charging || pct < 0 || pct > thresholdPct) return false
        fired = true
        return true
    }

    fun reset() { fired = false }
}

/** At or below this, a shutdown during a trip is read as the battery running out. */
const val BATTERY_DEAD_PCT = 2
