package app.saathi.core

/**
 * Where a trip is probably headed, so the watcher can check for detours without her typing anything.
 * Guesses only when it's fairly sure: a wrong guess means a false "Are you okay?", which costs her trust.
 */
object Destination {
    const val NEAR_M = 300.0

    /** The saved place she's at, if any. */
    fun origin(at: Fix, places: List<SavedPlace>): SavedPlace? =
        places.map { it to Geo.distanceM(at, it.fix) }.filter { it.second <= NEAR_M }.minByOrNull { it.second }?.first

    /**
     * Leaving one of two saved places → the other one (home ↔ work). With more places, the place she has gone to
     * most often from here, if at least twice. Otherwise none, and she can pick one from the trip notification.
     * [past] holds (from, to) labels of earlier trips.
     */
    fun guess(at: Fix, places: List<SavedPlace>, past: List<Pair<String?, String?>>): SavedPlace? {
        val from = origin(at, places) ?: return null
        val others = places.filter { it.label != from.label }
        if (others.size == 1) return others.single()
        val counts = past.filter { it.first == from.label }.mapNotNull { it.second }.groupingBy { it }.eachCount()
        val (label, n) = counts.filterKeys { k -> others.any { it.label == k } }.maxByOrNull { it.value } ?: return null
        return if (n >= 2) others.first { it.label == label } else null
    }

    fun cacheKey(from: String, to: String, mode: Mode) = "$from→$to:${mode.name}"
}
