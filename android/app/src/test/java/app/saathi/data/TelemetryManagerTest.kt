package app.saathi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryManagerTest {
    private fun ev(n: Int) = TelemetryEvent("event_$n", emptyMap(), "2026-10-21T00:00:00Z")

    @Test
    fun formatsEventRecordWithIsoTimestamp() {
        val event = TelemetryManager.createEventRecord("trip_started", mapOf("mode" to "auto"))
        assertEquals("trip_started", event.name)
        assertEquals("auto", event.properties["mode"])
        assertTrue(event.occurredAt.endsWith("Z"))
        assertTrue(event.occurredAt.matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:00:00Z""")))
    }

    @Test
    fun eventTimeIsRoundedDownToTheHour() {
        // 2026-10-21T13:47:59Z
        val ms = 1792590479000L
        val event = TelemetryManager.createEventRecord("x", emptyMap(), nowMs = ms)
        assertEquals("2026-10-21T13:00:00Z", event.occurredAt)
    }

    @Test
    fun capsQueueToMaxCapacityDroppingOldest() {
        val list = (1..250).map { ev(it) }
        val capped = TelemetryManager.enforceCap(list, maxCapacity = 200)
        assertEquals(200, capped.size)
        assertEquals("event_51", capped.first().name)
        assertEquals("event_250", capped.last().name)
    }

    @Test
    fun appendKeepsNewestWhenFull() {
        val full = (1..200).map { ev(it) }
        val next = TelemetryManager.appendCapped(full, ev(201), 200)
        assertEquals(200, next.size)
        assertEquals("event_2", next.first().name)
        assertEquals("event_201", next.last().name)
    }

    @Test
    fun successfulSendKeepsEventsAddedDuringFlush() {
        val sent = (1..10).map { ev(it) }
        val now = sent + ev(11) + ev(12)
        val rest = TelemetryManager.removeSent(now, sent)
        assertEquals(listOf("event_11", "event_12"), rest.map { it.name })
    }

    @Test
    fun removeSentWhenCapTrimmedTheFrontMeanwhile() {
        val sent = (1..10).map { ev(it) }
        // events 1..3 were trimmed by the cap while the send was in flight
        val now = (4..10).map { ev(it) } + ev(11)
        assertEquals(listOf("event_11"), TelemetryManager.removeSent(now, sent).map { it.name })
    }

    @Test
    fun removeSentWithNothingMatchingChangesNothing() {
        val sent = (1..3).map { ev(it) }
        val now = (50..52).map { ev(it) }
        assertEquals(now, TelemetryManager.removeSent(now, sent))
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

    @Test
    fun queueFileRoundTrips() {
        val events = listOf(
            TelemetryEvent("a", mapOf("n" to 3L, "ok" to false, "mode" to "WALK", "digits" to "123"), "2026-10-21T00:00:00Z"),
            ev(2),
        )
        assertEquals(events, TelemetryManager.parseEvents(TelemetryManager.serializeEvents(events)))
    }
}
