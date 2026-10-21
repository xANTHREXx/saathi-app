package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadyCardTest {
    private fun c(name: String, phone: String = "+919876543210") = Contact(name, phone, ANYWHERE)

    @Test
    fun oneNameIsAlone() = assertEquals(ToldNames("Chinu"), toldNames(listOf(c("Chinu"))))

    @Test
    fun twoNamesAreNamedBoth() = assertEquals(ToldNames("Chinu", "Amma"), toldNames(listOf(c("Chinu"), c("Amma"))))

    @Test
    fun moreThanTwoCountTheRest() {
        assertEquals(ToldNames("Chinu", others = 2), toldNames(listOf(c("Chinu"), c("Amma"), c("Riya"))))
        assertEquals(ToldNames("Chinu", others = 3), toldNames(listOf(c("Chinu"), c("Amma"), c("Riya"), c("Dad"))))
    }

    @Test
    fun onlyFirstNamesAreUsed() =
        assertEquals(ToldNames("Chinu", "Amma"), toldNames(listOf(c("  Chinu Kumar "), c("Amma Devi"))))

    @Test
    fun blankNamesAreSkippedAndNobodyGivesNull() {
        assertEquals(ToldNames("Amma"), toldNames(listOf(c(" "), c("Amma"))))
        assertNull(toldNames(emptyList()))
        assertNull(toldNames(listOf(c(""), c("  "))))
    }

    @Test
    fun recipientsJoinWithSemicolons() {
        assertEquals("+919876543210;+919123456789", smsRecipients(listOf(c("A"), c("B", "+919123456789"))))
        assertEquals("+919876543210", smsRecipients(listOf(c("A"), c("A again"))))
        assertEquals("", smsRecipients(emptyList()))
    }

    private fun visible(
        onTrip: Boolean = false, alert: Boolean = false, contacts: Boolean = true,
        dismissed: Boolean = false, told: Boolean = false, tile: Boolean = false,
    ) = readyCardVisible(onTrip, alert, contacts, dismissed, told, tile)

    @Test
    fun showsWhenReadyAndBothOpen() = assertTrue(visible())

    @Test
    fun showsWhileEitherIsStillOpen() {
        assertTrue(visible(told = true))
        assertTrue(visible(tile = true))
    }

    @Test
    fun goesWhenBothAreDone() = assertFalse(visible(told = true, tile = true))

    @Test
    fun goesWhenDismissed() = assertFalse(visible(dismissed = true))

    @Test
    fun staysOutOfTheWayOfATripOrAnAlert() {
        assertFalse(visible(onTrip = true))
        assertFalse(visible(alert = true))
    }

    @Test
    fun needsSomeoneToTell() = assertFalse(visible(contacts = false))
}
