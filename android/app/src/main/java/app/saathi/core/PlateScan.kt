package app.saathi.core

/**
 * Decides when a live scan has found the plate. One misread frame must not pass for a plate, so it takes the same plate
 * (O/0 and I/1 count as the same, see [Plate.matches]) read in [needed] frames no more than [windowMs] apart.
 * Not thread-safe: feed it from one thread, the one the OCR results arrive on.
 */
class PlateLock(private val windowMs: Long = 1_500, private val needed: Int = 2) {
    private class Read(val plate: String, val atMs: Long)

    private val recent = ArrayDeque<Read>()
    private var locked = false

    /** One frame's result at [nowMs] (a clock that only moves forward): the plate it showed, or null. Returns the plate once it locks on. */
    fun offer(plate: String?, nowMs: Long): String? {
        if (locked) return null
        while (recent.isNotEmpty() && nowMs - recent.first().atMs > windowMs) recent.removeFirst()
        if (plate == null) return null
        recent.addLast(Read(plate, nowMs))
        if (recent.count { Plate.matches(it.plate, plate) } < needed) return null
        locked = true
        return plate
    }
}

/**
 * One go at scanning, kept only to count it for `plate_scanned`: a lock-on is a success, and leaving or typing after
 * [GIVE_UP_AFTER_MS] of real scanning is a failure. Quicker exits and screens that never scanned (no camera, no
 * permission) report nothing. Each answer is true once, then the attempt is spent.
 */
class ScanAttempt {
    companion object {
        const val GIVE_UP_AFTER_MS = 5_000L

        // Frames come well under a second apart. A longer silence means the camera wasn't running (app in the
        // background, screen off), and that time isn't scanning.
        private const val MAX_FRAME_GAP_MS = 3_000L
    }

    private var scannedMs = 0L
    private var lastFrameMs: Long? = null

    /** A frame was read at [nowMs]. The first one starts the count. */
    fun frame(nowMs: Long) {
        lastFrameMs?.let { last -> if (nowMs - last <= MAX_FRAME_GAP_MS) scannedMs += nowMs - last }
        lastFrameMs = nowMs
    }

    /** The scan locked on a plate: true if a success should be reported now. */
    fun lockedOn(): Boolean = (lastFrameMs != null).also { reset() }

    /** She typed the plate or left: true if a failure should be reported now. */
    fun gaveUp(): Boolean = (lastFrameMs != null && scannedMs >= GIVE_UP_AFTER_MS).also { reset() }

    private fun reset() {
        scannedMs = 0
        lastFrameMs = null
    }
}
