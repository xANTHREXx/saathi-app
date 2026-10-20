package app.saathi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    private val prefix = "https://github.com/xANTHREXx/saathi-app/releases/download/"
    private val goodSha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    private fun release(url: String = prefix + "v0.5/saathi.apk", sha: String = goodSha) = Api.Release(
        versionCode = 5,
        versionName = "0.5",
        apkUrl = url,
        apkSha256 = sha,
        changelog = "New features",
        isMandatory = false
    )

    @Test
    fun updateIsAvailableWhenReleaseVersionIsGreater() {
        val release = release()
        assertTrue(AppUpdateManager.isUpdateAvailable(currentVersionCode = 4, release = release))
        assertFalse(AppUpdateManager.isUpdateAvailable(currentVersionCode = 5, release = release))
        assertFalse(AppUpdateManager.isUpdateAvailable(currentVersionCode = 6, release = release))
        assertFalse(AppUpdateManager.isUpdateAvailable(currentVersionCode = 4, release = null))
    }

    @Test
    fun sha256HexOfKnownInput() {
        // SHA-256("abc")
        assertEquals(goodSha, AppUpdateManager.sha256Hex("abc".byteInputStream()))
        // SHA-256("")
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", AppUpdateManager.sha256Hex(ByteArray(0).inputStream()))
    }

    @Test
    fun sha256HexStreamsLargerThanItsBuffer() {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        assertEquals(expected, AppUpdateManager.sha256Hex(data.inputStream()))
    }

    @Test
    fun isValidSha256AcceptsOnly64LowercaseHex() {
        assertTrue(AppUpdateManager.isValidSha256(goodSha))
        assertFalse(AppUpdateManager.isValidSha256(""))
        assertFalse(AppUpdateManager.isValidSha256(goodSha.uppercase()))
        assertFalse(AppUpdateManager.isValidSha256(goodSha.dropLast(1)))
        assertFalse(AppUpdateManager.isValidSha256(goodSha + "a"))
        assertFalse(AppUpdateManager.isValidSha256(goodSha.dropLast(1) + "g"))
        assertFalse(AppUpdateManager.isValidSha256(" " + goodSha.dropLast(1)))
    }

    @Test
    fun isTrustedUrlNeedsHttpsAndThePrefix() {
        assertTrue(AppUpdateManager.isTrustedUrl(prefix + "v0.2/saathi.apk", prefix))
        assertFalse(AppUpdateManager.isTrustedUrl(prefix, prefix))
        assertFalse(AppUpdateManager.isTrustedUrl("https://evil.example/saathi.apk", prefix))
        assertFalse(AppUpdateManager.isTrustedUrl("https://github.com/someone-else/saathi-app/releases/download/v1/a.apk", prefix))
        assertFalse(AppUpdateManager.isTrustedUrl("", prefix))
        // A prefix that isn't https is never trusted, so a bad local.properties can't open the door.
        assertFalse(AppUpdateManager.isTrustedUrl("http://example.com/a.apk", "http://example.com/"))
        assertFalse(AppUpdateManager.isTrustedUrl("https://example.com/a.apk", ""))
    }

    @Test
    fun rejectionReasonFlagsBadShaAndBadUrl() {
        assertNull(AppUpdateManager.rejectionReason(release(), prefix))
        assertNotNull(AppUpdateManager.rejectionReason(release(sha = ""), prefix))
        assertNotNull(AppUpdateManager.rejectionReason(release(sha = goodSha.uppercase()), prefix))
        assertNotNull(AppUpdateManager.rejectionReason(release(url = "https://evil.example/x.apk"), prefix))
    }
}
