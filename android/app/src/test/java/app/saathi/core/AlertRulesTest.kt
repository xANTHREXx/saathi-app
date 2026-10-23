package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertRulesTest {
    @Test
    fun defaultRulesWithoutGestures() {
        val rules = alertRules(powerPresses = 0, earphoneOn = false)
        assertEquals(listOf(Trigger.HOLD, Trigger.TILE, Trigger.SPEAK_FOR_ME), rules.youAsk)
        assertEquals(listOf(Trigger.LONG_STOP, Trigger.OFF_ROUTE, Trigger.LATE, Trigger.CHECK_ON_ME), rules.saathiAsks)
    }

    @Test
    fun rulesWithGesturesEnabled() {
        val rules = alertRules(powerPresses = 3, earphoneOn = true)
        assertEquals(
            listOf(Trigger.HOLD, Trigger.TILE, Trigger.SPEAK_FOR_ME, Trigger.POWER_BUTTON, Trigger.EARPHONE),
            rules.youAsk,
        )
        assertEquals(listOf(Trigger.LONG_STOP, Trigger.OFF_ROUTE, Trigger.LATE, Trigger.CHECK_ON_ME), rules.saathiAsks)
    }
}
