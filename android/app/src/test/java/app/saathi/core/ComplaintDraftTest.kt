package app.saathi.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplaintDraftTest {
    private val facts = ComplaintDraft.Facts(
        caseId = "SA-1A2B3C4D", startedAt = 1_760_801_520, endedAt = 1_760_803_320, mode = "AUTO", app = "Uber",
        plate = "KA05AB1234", plateCheck = "mismatch: saw KA05XY9876, booked KA05AB1234",
        events = listOf("stopped 12 min", "600 m off route"), lat = 12.91651, lng = 77.61012,
        evidence = listOf("location log" to "ab".repeat(32)),
    )

    @Test fun usesOnlyTheRecord() {
        val d = ComplaintDraft.draft(facts)
        assertTrue(d.contains("vehicle number KA05AB1234"))
        assertTrue(d.contains("booked through Uber"))
        assertTrue(d.contains("stopped 12 min; 600 m off route"))
        assertTrue(d.contains("saw KA05XY9876, booked KA05AB1234"))
        assertTrue(d.contains("ab".repeat(32)))
        assertTrue(d.contains("Zero FIR"))
        assertTrue(d.contains("18 October 2025"))
    }

    @Test fun leavesUnknownsInBrackets() {
        val d = ComplaintDraft.draft(facts.copy(app = null, plate = null, plateCheck = null, events = emptyList(), lat = null, lng = null))
        assertTrue(d.contains("[your full name]"))
        assertTrue(d.contains("[describe in your own words"))
        assertFalse(d.contains("vehicle number"))
        assertFalse(d.contains("trip record shows"))
    }
}
