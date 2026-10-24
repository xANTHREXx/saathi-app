package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AlertRulesTest {
    private val always = listOf(
        Trigger.HOLD, Trigger.TILE, Trigger.WIDGET, Trigger.SHORTCUT,
        Trigger.NOTIFICATION, Trigger.COMPANION, Trigger.PLATE_MISMATCH,
    )
    private val saathi = listOf(Trigger.LONG_STOP, Trigger.OFF_ROUTE, Trigger.LATE, Trigger.CHECK_ON_ME)
    private val plain = listOf(PlainText.BATTERY_LOW, PlainText.PHONE_OFF, PlainText.ARRIVED, PlainText.IM_OKAY)

    @Test
    fun defaultRulesWithoutGestures() {
        val rules = alertRules(powerPresses = 0, earphoneOn = false)
        assertEquals(always, rules.youAsk)
        assertEquals(saathi, rules.saathiAsks)
        assertEquals(plain, rules.plainTexts)
    }

    @Test
    fun rulesWithGesturesEnabled() {
        val rules = alertRules(powerPresses = 3, earphoneOn = true)
        assertEquals(always + listOf(Trigger.POWER_BUTTON, Trigger.EARPHONE), rules.youAsk)
        assertEquals(saathi, rules.saathiAsks)
        assertEquals(plain, rules.plainTexts)
    }

    @Test
    fun earphoneOnlyAddsEarphone() {
        val rules = alertRules(powerPresses = 0, earphoneOn = true)
        assertEquals(always + Trigger.EARPHONE, rules.youAsk)
        assertFalse(Trigger.POWER_BUTTON in rules.youAsk)
    }
}
