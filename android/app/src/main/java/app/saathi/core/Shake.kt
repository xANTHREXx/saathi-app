package app.saathi.core

import kotlin.math.sqrt

/**
 * A hard shake, as an unclear signal: it only starts a check-in, never an alert. [peaks] jolts over
 * [thresholdG] within [windowMs]. One jolt spans several samples, so peaks closer than 150 ms count once.
 * After it fires, or after she answers "I'm okay" ([quiet]), nothing fires for [cooldownMs].
 * Times are on one monotonic clock (elapsed realtime).
 */
class ShakeDetector(
    private val thresholdG: Double = 2.5,
    private val peaks: Int = 3,
    private val windowMs: Long = 1_500,
    private val cooldownMs: Long = 120_000,
) {
    private val times = ArrayDeque<Long>()
    private var lastPeakMs = Long.MIN_VALUE / 2
    private var quietUntilMs = Long.MIN_VALUE / 2

    /** [x], [y], [z] in m/s², as the accelerometer reports them (gravity included). */
    fun onSample(atMs: Long, x: Float, y: Float, z: Float): Boolean {
        val g = sqrt((x * x + y * y + z * z).toDouble()) / 9.80665
        if (g < thresholdG || atMs - lastPeakMs < 150) return false
        lastPeakMs = atMs
        if (atMs < quietUntilMs) return false
        times.addLast(atMs)
        while (times.isNotEmpty() && atMs - times.first() > windowMs) times.removeFirst()
        if (times.size < peaks) return false
        times.clear()
        quiet(atMs)
        return true
    }

    /** Start the cooldown now: she just answered, or it just fired. */
    fun quiet(atMs: Long) {
        quietUntilMs = atMs + cooldownMs
        times.clear()
    }
}
