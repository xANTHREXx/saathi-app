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
    fun truncatesOversizedStackTrace() {
        val longString = "A".repeat(10000)
        val truncated = CrashTracker.truncateStackTrace(longString, 8000)
        assertEquals(8000, truncated.length)
    }
}
