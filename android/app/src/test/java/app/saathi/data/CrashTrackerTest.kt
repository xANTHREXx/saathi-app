package app.saathi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashTrackerTest {
    @Test
    fun formatsExceptionCorrectly() {
        val ex = IllegalStateException("Test exception message")
        val record = CrashTracker.formatCrashRecord(ex)
        assertEquals("IllegalStateException", record.exceptionType)
        assertEquals("Test exception message", record.message)
        assertTrue(record.stackTrace.contains("IllegalStateException"))
        assertTrue(record.crashedAt.isNotBlank())
    }

    @Test
    fun crashTimeIsRoundedDownToTheHour() {
        // 2026-10-21T13:47:59Z
        val record = CrashTracker.formatCrashRecord(IllegalStateException("x"), nowMs = 1792590479000L)
        assertEquals("2026-10-21T13:00:00Z", record.crashedAt)
    }

    @Test
    fun truncatesOversizedStackTrace() {
        val longString = "A".repeat(10000)
        val truncated = CrashTracker.truncateStackTrace(longString, 8000)
        assertEquals(8000, truncated.length)
    }
}
