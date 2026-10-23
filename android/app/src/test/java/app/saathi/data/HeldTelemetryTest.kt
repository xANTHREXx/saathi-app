package app.saathi.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HeldTelemetryTest {
    @Test
    fun pruneDropsOlderThan30Days() {
        val nowMs = 1792590479000L // arbitrary fixed time
        val oneDayOld = TelemetryEvent("recent", emptyMap(), hourIso(nowMs - 1L * 24 * 3600 * 1000))
        val thirtyOneDaysOld = TelemetryEvent("old", emptyMap(), hourIso(nowMs - 31L * 24 * 3600 * 1000))

        val pruned = TelemetryManager.pruneHeld(listOf(thirtyOneDaysOld, oneDayOld), nowMs = nowMs)
        assertEquals(1, pruned.size)
        assertEquals("recent", pruned.first().name)
    }

    @Test
    fun pruneKeepsAllRecent() {
        val nowMs = 1792590479000L
        val ev1 = TelemetryEvent("e1", emptyMap(), hourIso(nowMs - 2L * 24 * 3600 * 1000))
        val ev2 = TelemetryEvent("e2", emptyMap(), hourIso(nowMs - 10L * 24 * 3600 * 1000))

        val pruned = TelemetryManager.pruneHeld(listOf(ev1, ev2), nowMs = nowMs)
        assertEquals(2, pruned.size)
    }
}
