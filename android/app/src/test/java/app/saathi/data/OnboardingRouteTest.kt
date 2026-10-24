package app.saathi.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingRouteTest {
    @Test
    fun agreedSendsWhetherOrNotAsked() {
        assertEquals(OnboardingRoute.SEND, onboardingRoute(usageStats = true, consentAsked = true))
        assertEquals(OnboardingRoute.SEND, onboardingRoute(usageStats = true, consentAsked = false))
    }

    @Test
    fun notAskedYetHolds() {
        assertEquals(OnboardingRoute.HOLD, onboardingRoute(usageStats = false, consentAsked = false))
    }

    @Test
    fun declinedDrops() {
        assertEquals(OnboardingRoute.DROP, onboardingRoute(usageStats = false, consentAsked = true))
    }
}
