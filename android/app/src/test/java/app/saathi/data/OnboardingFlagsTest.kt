package app.saathi.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlagsTest {
    @Test
    fun firstTimeReturnsTrueForUnseenKeyAndFalseForSeenOne() {
        val seen = setOf("setup_started", "tip_first_trip")
        assertTrue(firstTime(seen, "setup_finished"))
        assertFalse(firstTime(seen, "setup_started"))
    }
}
