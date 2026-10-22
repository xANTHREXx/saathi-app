package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OkayGateTest {
    @Test
    fun aSentAlertOnALockedSecurePhoneNeedsAnUnlock() {
        assertEquals(OkayPath.UNLOCK_FIRST, okayPath(alertActive = true, keyguardLocked = true, deviceSecure = true))
    }

    @Test
    fun aSentAlertOnAnUnlockedPhoneGoesStraightThrough() {
        assertEquals(OkayPath.DIRECT, okayPath(alertActive = true, keyguardLocked = false, deviceSecure = true))
    }

    @Test
    fun aPhoneWithNoScreenLockHasNothingToUnlock() {
        assertEquals(OkayPath.DIRECT, okayPath(alertActive = true, keyguardLocked = true, deviceSecure = false))
        assertEquals(OkayPath.DIRECT, okayPath(alertActive = true, keyguardLocked = false, deviceSecure = false))
    }

    @Test
    fun beforeAnythingWasSentItIsAlwaysOneTap() {
        for (locked in listOf(true, false)) for (secure in listOf(true, false)) {
            assertEquals(OkayPath.DIRECT, okayPath(alertActive = false, keyguardLocked = locked, deviceSecure = secure))
        }
    }
}
