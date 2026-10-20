package app.saathi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryManagerTest {
    @Test
    fun formatsEventRecordWithIsoTimestamp() {
        val event = TelemetryManager.createEventRecord("trip_started", mapOf("mode" to "auto"))
        assertEquals("trip_started", event.name)
        assertEquals("auto", event.properties["mode"])
        assertTrue(event.occurredAt.endsWith("Z"))
    }

    @Test
    fun capsQueueToMaxCapacityDroppingOldest() {
        val list = (1..250).map { i ->
            TelemetryEvent("event_$i", emptyMap(), "2026-10-21T00:00:00Z")
        }
        val capped = TelemetryManager.enforceCap(list, maxCapacity = 200)
        assertEquals(200, capped.size)
        assertEquals("event_51", capped.first().name)
        assertEquals("event_250", capped.last().name)
    }

    @Test
    fun serializesEventsToJsonArray() {
        val events = listOf(
            TelemetryEvent("test_event", mapOf("count" to 42, "flag" to true), "2026-10-21T00:00:00Z")
        )
        val json = TelemetryManager.serializeEvents(events)
        assertTrue(json.startsWith("["))
        assertTrue(json.contains("\"name\":\"test_event\""))
        assertTrue(json.contains("\"count\":42"))
        assertTrue(json.contains("\"flag\":true"))
    }
}
