package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class AlertTextTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val fix = Fix(12.91651, 77.61012, 1_760_801_520) // 18 Oct 2026, 21:02 IST region of time
    private val stop = TripEvent(EventKind.LONG_STOP, "stopped 5 min", fix.t)

    @Test
    fun alertTextNoResponse() {
        val s = alertText("Priya", Reason.NO_RESPONSE, "KA05AB1234", stop, "Indian Oil, Hosur Rd",
            fix, fixAgeS = 5, liveUrl = "https://saathi.run/l/abc123", zone = ist)
        listOf("Saathi alert", "Priya", "hasn't answered a check-in", "KA05AB1234", "stopped 5 min",
            "Indian Oil, Hosur Rd", "https://maps.google.com/?q=12.9", "https://saathi.run/l/abc123")
            .forEach { assertTrue("missing '$it' in: $s", s.contains(it)) }
        assertTrue(s.length <= 320)
        assertFalse(s.lowercase().contains("safe"))
    }

    @Test
    fun alertTextOfflineSaysFixTime() {
        val s = alertText("Priya", Reason.POWER_BUTTON, null, null, null, fix, fixAgeS = 420,
            liveUrl = null, zone = ist)
        assertTrue(s, s.contains("Location at"))
        assertTrue(s, s.contains("last known"))
        assertFalse(s, s.contains("Live:"))
        assertTrue(s, s.contains("asked for help"))
    }

    @Test
    fun quickSettingsTileReadsAsAskedForHelp() {
        val s = alertText("Priya", Reason.QS_TILE, null, null, null, null, 0, null, ist)
        assertTrue(s, s.startsWith("Saathi alert: Priya asked for help."))
    }

    @Test
    fun alertTextWithoutAnyFixStillSends() {
        val s = alertText("Priya", Reason.SPEAK_FOR_ME, null, null, null, null, 0, null, ist)
        assertTrue(s, s.startsWith("Saathi alert: Priya used Speak for me."))
    }

    @Test
    fun longPlaceIsDroppedToFit() {
        val place = "x".repeat(400)
        val s = alertText("Priya", Reason.NO_RESPONSE, "KA05AB1234", stop, place, fix, 5,
            "https://saathi.run/l/abc123", ist)
        assertTrue(s.length <= 320)
        assertTrue(s.contains("https://saathi.run/l/abc123"))
    }

    @Test
    fun batteryTextHasLevelAndMap() {
        val s = batteryText("Priya", 5, fix, fixAgeS = 30, zone = ist)
        assertTrue(s, s.startsWith("Saathi: Priya's phone battery is at 5% during a trip."))
        assertTrue(s, s.contains("https://maps.google.com/?q=12.9"))
        assertTrue(s.length <= 320)
        assertFalse(s.lowercase().contains("safe"))
    }

    @Test
    fun offTextSaysWhichKindOfOff() {
        val off = offText("Priya", batteryDied = false, fix, 30, ist)
        val dead = offText("Priya", batteryDied = true, fix, 30, ist)
        assertTrue(off, off.startsWith("Saathi: Priya's phone was switched off during a trip."))
        assertTrue(dead, dead.startsWith("Saathi: Priya's phone ran out of battery during a trip."))
        listOf(off, dead).forEach {
            assertTrue(it, it.contains("https://maps.google.com/?q="))
            assertTrue(it.length <= 320)
            assertFalse(it.lowercase().contains("safe"))
        }
    }

    @Test
    fun offTextWithoutAFixStillSends() {
        assertEquals("Saathi: Priya's phone was switched off during a trip.", offText("Priya", false, null, 0, ist))
    }

    @Test
    fun arrivedTextNamesThePlaceAndTime() {
        assertEquals("Saathi: Priya reached home at 9:02 PM.", arrivedText("Priya", "Home", fix.t, ist))
        assertEquals("Saathi: Priya reached Mausi's flat at 9:02 PM.", arrivedText("Priya", "Mausi's flat", fix.t, ist))
        assertEquals("Saathi: Priya reached a saved place at 9:02 PM.", arrivedText("Priya", "Other", fix.t, ist))
        assertFalse(arrivedText("Priya", "Home", fix.t, ist).lowercase().contains("safe"))
    }

    @Test
    fun okTextExact() {
        assertEquals("Saathi: false alarm, Priya is fine.", okText("Priya"))
    }
}
