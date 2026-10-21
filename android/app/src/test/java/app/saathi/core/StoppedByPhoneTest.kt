package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoppedByPhoneTest {
    private val beat = 1_000_000_000L
    private val later = beat + STOPPED_GAP_MS + 1

    private fun check(
        last: Long = beat, onTrip: Boolean = true, clean: Boolean = false, now: Long = later, byUser: Boolean? = false,
    ) = stoppedAt(last, onTrip, clean, now, byUser)

    @Test
    fun aTripThatWentQuietWithoutAnEndIsAKill() = assertEquals(beat, check())

    @Test
    fun anUnknownExitReasonStillCounts() = assertEquals(beat, check(byUser = null))

    @Test
    fun notOnATripIsNothingToReport() = assertNull(check(onTrip = false))

    @Test
    fun aCleanStopIsNotAKill() = assertNull(check(clean = true))

    @Test
    fun aRecentPulseIsNotYetAKill() {
        assertNull(check(now = beat + 60_000))
        assertNull(check(now = beat + STOPPED_GAP_MS)) // exactly three minutes: not more than
    }

    @Test
    fun shePutTheAppAwayHerself() = assertNull(check(byUser = true))

    @Test
    fun noPulseEverMeansNothing() = assertNull(check(last = 0L))
}

class StoppedDayTest {
    private val zone = java.time.ZoneId.of("Asia/Kolkata")
    private fun ms(date: String, time: String) =
        java.time.LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    @Test
    fun laterTheSameDayIsToday() =
        assertEquals(StoppedDay.TODAY, stoppedDay(ms("2026-10-21", "09:42:00"), ms("2026-10-21", "23:10:00"), zone))

    @Test
    fun theDayBeforeIsYesterdayEvenAcrossMidnight() =
        assertEquals(StoppedDay.YESTERDAY, stoppedDay(ms("2026-10-20", "21:42:00"), ms("2026-10-21", "00:05:00"), zone))

    @Test
    fun olderThanThatIsEarlier() =
        assertEquals(StoppedDay.EARLIER, stoppedDay(ms("2026-10-19", "21:42:00"), ms("2026-10-21", "08:00:00"), zone))

    @Test
    fun theDateFollowsHerTimeZoneNotUtc() {
        // 20:00 UTC on the 20th is 01:30 on the 21st in India: today, not yesterday.
        val at = java.time.Instant.parse("2026-10-20T20:00:00Z").toEpochMilli()
        assertEquals(StoppedDay.TODAY, stoppedDay(at, ms("2026-10-21", "10:00:00"), zone))
    }
}
