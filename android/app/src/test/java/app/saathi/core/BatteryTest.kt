package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryTest {
    private fun run(w: BatteryWatch, vararg levels: Pair<Int, Boolean>) = levels.map { (pct, charging) -> w.onLevel(pct, charging) }

    @Test
    fun firesOnceAtFivePercent() {
        assertEquals(listOf(false, false, true, false, false),
            run(BatteryWatch(), 7 to false, 6 to false, 5 to false, 4 to false, 3 to false))
    }

    @Test
    fun notWhileCharging() {
        assertEquals(listOf(false, true), run(BatteryWatch(), 4 to true, 4 to false))
    }

    @Test
    fun resetArmsItForTheNextTrip() {
        val w = BatteryWatch()
        assertEquals(listOf(true, false), run(w, 5 to false, 4 to false))
        w.reset()
        assertEquals(listOf(true), run(w, 4 to false))
    }

    @Test
    fun unknownLevelIsIgnored() {
        assertEquals(listOf(false), run(BatteryWatch(), -1 to false))
    }
}
