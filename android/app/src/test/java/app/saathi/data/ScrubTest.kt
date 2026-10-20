package app.saathi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScrubTest {
    @Test
    fun phoneWithSpacesBecomesNumber() {
        assertEquals("call [number] now", scrub("call +91 98765 43210 now"))
        assertEquals("[number]", scrub("98765-43210"))
        assertEquals("[number]", scrub("9876543210"))
    }

    @Test
    fun emailBecomesEmail() {
        assertEquals("sent to [email].", scrub("sent to priya.s+test@example.co.in."))
    }

    @Test
    fun coordinatePairBecomesPlace() {
        assertEquals("at [place]", scrub("at 12.971599, 77.594566"))
        assertEquals("at [place]", scrub("at 12.9716,77.5946"))
        assertEquals("at [place]", scrub("at -12.97160, -77.59460"))
    }

    @Test
    fun stackFrameLineNumbersStay() {
        val frame = "at app.saathi.Foo.bar(Foo.kt:123)"
        assertEquals(frame, scrub(frame))
        assertEquals("Foo.kt:123, Bar.kt:4567", scrub("Foo.kt:123, Bar.kt:4567"))
    }

    @Test
    fun shortDecimalsStay() {
        assertEquals("1.5, 2.5", scrub("1.5, 2.5"))
    }

    @Test
    fun crashRecordIsScrubbed() {
        val ex = IllegalStateException("bad number +91 98765 43210 for a@b.com at 12.9716, 77.5946")
        val r = CrashTracker.formatCrashRecord(ex)
        assertEquals("bad number [number] for [email] at [place]", r.message)
        assertFalse(r.stackTrace.contains("98765"))
        assertFalse(r.stackTrace.contains("a@b.com"))
    }
}
