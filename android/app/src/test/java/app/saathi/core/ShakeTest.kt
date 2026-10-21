package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShakeTest {
    private val hard = 3.5f * 9.80665f // 3.5 g on one axis
    private fun jolts(d: ShakeDetector, vararg at: Long) = at.map { d.onSample(it, hard, 0f, 0f) }

    @Test
    fun threeHardJoltsInsideTheWindowFire() {
        assertEquals(listOf(false, false, true), jolts(ShakeDetector(), 0, 400, 800))
    }

    @Test
    fun joltsSpreadOutDoNot() {
        assertEquals(listOf(false, false, false), jolts(ShakeDetector(), 0, 1_000, 2_000))
    }

    @Test
    fun oneJoltOverSeveralSamplesCountsOnce() {
        assertEquals(listOf(false, false, false, false), jolts(ShakeDetector(), 0, 20, 40, 60))
    }

    @Test
    fun gravityAloneNeverFires() {
        val d = ShakeDetector()
        assertFalse((0L..3_000L step 20).any { d.onSample(it, 0f, 0f, 9.81f) })
    }

    @Test
    fun cooldownAfterFiring() {
        val d = ShakeDetector()
        assertEquals(listOf(false, false, true), jolts(d, 0, 400, 800))
        assertEquals(listOf(false, false, false), jolts(d, 10_000, 10_400, 10_800))
        assertEquals(listOf(false, false, true), jolts(d, 130_000, 130_400, 130_800))
    }

    @Test
    fun answeringStartsTheCooldown() {
        val d = ShakeDetector()
        d.quiet(0)
        assertEquals(listOf(false, false, false), jolts(d, 1_000, 1_400, 1_800))
    }
}
