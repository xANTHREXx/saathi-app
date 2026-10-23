package app.saathi.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeCardRuleTest {

    @Test
    fun `visible on first sight when not done and not snoozed`() {
        // firstShownAt = 0 means it hasn't been recorded as shown yet
        val visible = practiceCardVisible(
            practiceDone = false,
            firstShownAt = 0L,
            snoozedUntil = 0L,
            nowMs = 100_000L,
        )
        assertTrue(visible)
    }

    @Test
    fun `hidden once practice is done`() {
        val visible = practiceCardVisible(
            practiceDone = true,
            firstShownAt = 100_000L,
            snoozedUntil = 0L,
            nowMs = 200_000L,
        )
        assertFalse(visible)
    }

    @Test
    fun `hidden while snoozed and visible again after snooze expires`() {
        val now = 100_000L
        val snoozedUntil = now + PRACTICE_CARD_SNOOZE_MS

        // While still within snooze period
        val duringSnooze = practiceCardVisible(
            practiceDone = false,
            firstShownAt = now - 1000L,
            snoozedUntil = snoozedUntil,
            nowMs = snoozedUntil - 1000L,
        )
        assertFalse(duringSnooze)

        // Exactly when snooze expires
        val whenSnoozeEnds = practiceCardVisible(
            practiceDone = false,
            firstShownAt = now - 1000L,
            snoozedUntil = snoozedUntil,
            nowMs = snoozedUntil,
        )
        assertTrue(whenSnoozeEnds)

        // After snooze expires
        val afterSnooze = practiceCardVisible(
            practiceDone = false,
            firstShownAt = now - 1000L,
            snoozedUntil = snoozedUntil,
            nowMs = snoozedUntil + 1000L,
        )
        assertTrue(afterSnooze)
    }

    @Test
    fun `hidden after 14 days from first shown`() {
        val firstShown = 1_000_000L
        val maxAge = PRACTICE_CARD_MAX_AGE_MS

        // Right before 14 days
        val beforeExpiry = practiceCardVisible(
            practiceDone = false,
            firstShownAt = firstShown,
            snoozedUntil = 0L,
            nowMs = firstShown + maxAge - 1000L,
        )
        assertTrue(beforeExpiry)

        // At 14 days
        val atExpiry = practiceCardVisible(
            practiceDone = false,
            firstShownAt = firstShown,
            snoozedUntil = 0L,
            nowMs = firstShown + maxAge,
        )
        assertFalse(atExpiry)

        // After 14 days
        val afterExpiry = practiceCardVisible(
            practiceDone = false,
            firstShownAt = firstShown,
            snoozedUntil = 0L,
            nowMs = firstShown + maxAge + 1000L,
        )
        assertFalse(afterExpiry)
    }
}
