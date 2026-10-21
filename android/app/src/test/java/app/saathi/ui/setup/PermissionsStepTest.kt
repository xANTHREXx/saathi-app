package app.saathi.ui.setup

import app.saathi.core.Oem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsStepTest {
    private fun allGrantedExceptKeep(done: Boolean) =
        Perm.values().associateWith { it != Perm.KEEP_RUNNING || done }

    @Test
    fun stockPhonesNeverSeeTheMakerCard() {
        assertFalse(Perm.KEEP_RUNNING in visiblePerms(sdk = 34, oem = Oem.OTHER))
        assertNull(firstMissingRequired(allGrantedExceptKeep(false), sdk = 34, oem = Oem.OTHER))
    }

    @Test
    fun makerPhonesSeeItAfterTheBatteryCard() {
        val shown = visiblePerms(sdk = 34, oem = Oem.XIAOMI)
        assertTrue(shown.indexOf(Perm.KEEP_RUNNING) == shown.indexOf(Perm.BACKGROUND) + 1)
    }

    @Test
    fun itHoldsUpSetupUntilSheSaysItIsDone() {
        assertEquals(Perm.KEEP_RUNNING, firstMissingRequired(allGrantedExceptKeep(false), sdk = 34, oem = Oem.VIVO))
        assertNull(firstMissingRequired(allGrantedExceptKeep(true), sdk = 34, oem = Oem.VIVO))
    }
}
