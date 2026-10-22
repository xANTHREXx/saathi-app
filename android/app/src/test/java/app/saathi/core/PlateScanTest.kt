package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateLockTest {
    private val plate = "KA05AB1234"

    @Test
    fun oneReadIsNotEnough() = assertNull(PlateLock().offer(plate, 0))

    @Test
    fun theSamePlateTwiceWithinTheWindowLocksOn() {
        val lock = PlateLock()
        assertNull(lock.offer(plate, 0))
        assertEquals(plate, lock.offer(plate, 900))
    }

    @Test
    fun exactlyTheWindowApartStillCounts() {
        val lock = PlateLock(windowMs = 1_500)
        assertNull(lock.offer(plate, 0))
        assertEquals(plate, lock.offer(plate, 1_500))
    }

    @Test
    fun twoReadsFurtherApartThanTheWindowDoNot() {
        val lock = PlateLock(windowMs = 1_500)
        assertNull(lock.offer(plate, 0))
        assertNull(lock.offer(plate, 1_501))
    }

    @Test
    fun theWindowSlidesSoLaterPairsStillLock() {
        val lock = PlateLock(windowMs = 1_500)
        assertNull(lock.offer(plate, 0))
        assertNull(lock.offer(plate, 1_800))
        assertEquals(plate, lock.offer(plate, 2_400))
    }

    @Test
    fun aDifferentPlateDoesNotLockWithTheFirst() {
        val lock = PlateLock()
        assertNull(lock.offer("KA05AB1234", 0))
        assertNull(lock.offer("KA05AB1284", 300))
        assertNull(lock.offer("MH12DE5678", 600))
    }

    @Test
    fun aMisreadBetweenTwoGoodReadsDoesNotStopTheLock() {
        val lock = PlateLock()
        assertNull(lock.offer("KA05AB1234", 0))
        assertNull(lock.offer("KA05AB1284", 300))
        assertNull(lock.offer(null, 600))
        assertEquals("KA05AB1234", lock.offer("KA05AB1234", 900))
    }

    @Test
    fun framesWithNoPlateAddNothing() {
        val lock = PlateLock()
        assertNull(lock.offer(null, 0))
        assertNull(lock.offer(plate, 100))
        assertNull(lock.offer(null, 200))
        assertNull(lock.offer(null, 300))
        assertEquals(plate, lock.offer(plate, 400))
    }

    @Test
    fun iAndOneAreTheSamePlateAcrossFrames() {
        val lock = PlateLock()
        assertNull(lock.offer("KA01AI1234", 0))
        assertEquals("KA01A11234", lock.offer("KA01A11234", 200))
    }

    @Test
    fun itLocksOnceAndThenStaysQuiet() {
        val lock = PlateLock()
        lock.offer(plate, 0)
        assertEquals(plate, lock.offer(plate, 100))
        assertNull(lock.offer(plate, 200))
        assertNull(lock.offer(plate, 300))
    }

    @Test
    fun threeReadsNeededWhenAskedFor() {
        val lock = PlateLock(needed = 3)
        assertNull(lock.offer(plate, 0))
        assertNull(lock.offer(plate, 200))
        assertEquals(plate, lock.offer(plate, 400))
    }
}

class ScanAttemptTest {
    /** Frames every 300 ms from [from] to [to]. */
    private fun scan(a: ScanAttempt, from: Long, to: Long) {
        var t = from
        while (t <= to) { a.frame(t); t += 300 }
    }

    @Test
    fun scanningForFiveSecondsThenGivingUpIsAFailure() {
        val a = ScanAttempt()
        scan(a, 0, 5_100)
        assertTrue(a.gaveUp())
    }

    @Test
    fun givingUpSoonerThanFiveSecondsReportsNothing() {
        val a = ScanAttempt()
        scan(a, 0, 4_500)
        assertFalse(a.gaveUp())
    }

    @Test
    fun exactlyFiveSecondsCounts() {
        val a = ScanAttempt()
        a.frame(0)
        a.frame(2_500)
        a.frame(5_000)
        assertTrue(a.gaveUp())
    }

    @Test
    fun aScreenThatNeverScannedReportsNothing() {
        val a = ScanAttempt()
        assertFalse(a.gaveUp())
        assertFalse(a.lockedOn())
    }

    @Test
    fun aLockOnIsASuccessOnceEvenAfterQuickFrames() {
        val a = ScanAttempt()
        scan(a, 0, 600)
        assertTrue(a.lockedOn())
        assertFalse(a.lockedOn())
    }

    @Test
    fun afterALockOnLeavingIsNotAlsoAFailure() {
        val a = ScanAttempt()
        scan(a, 0, 6_000)
        assertTrue(a.lockedOn())
        assertFalse(a.gaveUp())
    }

    @Test
    fun givingUpIsReportedOnlyOnce() {
        val a = ScanAttempt()
        scan(a, 0, 6_000)
        assertTrue(a.gaveUp())
        assertFalse(a.gaveUp())
    }

    @Test
    fun timeWithTheCameraStoppedIsNotScanning() {
        val a = ScanAttempt()
        scan(a, 0, 1_200)
        a.frame(61_200) // came back a minute later
        a.frame(61_500)
        assertFalse(a.gaveUp())
    }

    @Test
    fun aRetakeStartsAFreshCount() {
        val a = ScanAttempt()
        scan(a, 0, 3_000)
        assertTrue(a.lockedOn())
        scan(a, 10_000, 12_000)
        assertFalse(a.gaveUp())
    }
}
