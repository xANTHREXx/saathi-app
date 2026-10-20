package app.saathi.ui

import app.saathi.trip.UiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePromptTest {
    @Test
    fun promptShowsOnlyWhenNothingHelpRelatedIsRunning() {
        assertFalse(updatePromptBlocked(UiState()))
        assertTrue(updatePromptBlocked(UiState(onTrip = true)))
        assertTrue(updatePromptBlocked(UiState(alertActive = true)))
        assertTrue(updatePromptBlocked(UiState(checkInEndsAtMs = 1L)))
        assertTrue(updatePromptBlocked(UiState(practice = true)))
    }
}
