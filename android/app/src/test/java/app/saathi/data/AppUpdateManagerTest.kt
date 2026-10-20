package app.saathi.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun updateIsAvailableWhenReleaseVersionIsGreater() {
        val release = Api.Release(
            versionCode = 5,
            versionName = "0.5",
            apkUrl = "https://example.com/saathi.apk",
            changelog = "New features",
            isMandatory = false
        )
        assertTrue(AppUpdateManager.isUpdateAvailable(currentVersionCode = 4, release = release))
        assertFalse(AppUpdateManager.isUpdateAvailable(currentVersionCode = 5, release = release))
        assertFalse(AppUpdateManager.isUpdateAvailable(currentVersionCode = 6, release = release))
        assertFalse(AppUpdateManager.isUpdateAvailable(currentVersionCode = 4, release = null))
    }
}
