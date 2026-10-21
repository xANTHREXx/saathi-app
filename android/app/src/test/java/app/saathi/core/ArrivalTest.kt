package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ArrivalTest {
    private val home = Fix(12.97160, 77.59460, 0)
    // About 50 m and 400 m north of home.
    private fun near(t: Long) = Fix(12.97205, 77.59460, t)
    private fun far(t: Long) = Fix(12.97520, 77.59460, t)

    private fun run(a: Arrival, vararg fixes: Fix) = fixes.map { a.add(it, tripStartS = 0) }

    @Test
    fun twoFixesInsideArrive() {
        assertEquals(listOf(false, false, true), run(Arrival(home), far(200), near(300), near(310)))
    }

    @Test
    fun oneFixInsideIsNotEnough() {
        assertEquals(listOf(false, false, false), run(Arrival(home), near(300), far(310), near(320)))
    }

    @Test
    fun notInTheFirstTwoMinutes() {
        assertEquals(listOf(false, false, true), run(Arrival(home), near(30), near(60), near(130)))
    }

    @Test
    fun farAwayNeverArrives() {
        assertEquals(listOf(false, false, false), run(Arrival(home), far(300), far(310), far(320)))
    }
}
