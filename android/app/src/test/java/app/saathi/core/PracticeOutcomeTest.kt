package app.saathi.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeOutcomeTest {
    @Test
    fun recordsFirstCompletionWhenAnswered() {
        assertTrue(practiceOutcome(answered = true, alreadyDone = false))
    }

    @Test
    fun recordsFirstCompletionWhenTimedOut() {
        assertTrue(practiceOutcome(answered = false, alreadyDone = false))
    }

    @Test
    fun ignoresSubsequentCompletions() {
        assertFalse(practiceOutcome(answered = true, alreadyDone = true))
        assertFalse(practiceOutcome(answered = false, alreadyDone = true))
    }
}
