package app.saathi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestDeviceTest {
    @Test fun debugBuildsAreTestPhonesUntilSaid() {
        assertTrue(TestDevice.resolve(null, debugBuild = true))
        assertFalse(TestDevice.resolve(null, debugBuild = false))
    }

    @Test fun aSavedChoiceBeatsTheBuildType() {
        assertFalse(TestDevice.resolve(false, debugBuild = true))
        assertTrue(TestDevice.resolve(true, debugBuild = false))
    }

    @Test fun onlyTheBackendHostGetsTheHeader() {
        assertTrue(TestDevice.applies("abc.supabase.co", "abc.supabase.co"))
        assertTrue(TestDevice.applies("ABC.supabase.co", "abc.supabase.co"))
        assertFalse(TestDevice.applies("github.com", "abc.supabase.co"))
        assertFalse(TestDevice.applies("abc.supabase.co", null))
    }

    @Test fun settingsDefaultToNeverSet() {
        assertEquals(null, Settings().testDevice)
    }
}
