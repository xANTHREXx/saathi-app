package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NamesTextTest {
    @Test
    fun oneName() = assertEquals("Chinu", namesSentence(listOf("Chinu")))

    @Test
    fun twoNames() = assertEquals("Amma and Riya", namesSentence(listOf("Amma", "Riya")))

    @Test
    fun threeNames() = assertEquals("Amma, Riya and Chinu", namesSentence(listOf("Amma", "Riya", "Chinu")))

    @Test
    fun noNamesSaysYourContacts() = assertEquals("your contacts", namesSentence(emptyList()))

    @Test
    fun blankNamesAreSkipped() {
        assertEquals("your contacts", namesSentence(listOf(" ", "")))
        assertEquals("Amma and Riya", namesSentence(listOf("Amma", " ", "Riya")))
    }

    @Test
    fun secondsRoundUpAndNeverShowZeroBeforeTheEnd() {
        assertEquals(5, secondsLeft(5_000, 0))
        assertEquals(5, secondsLeft(5_000, 1))
        assertEquals(1, secondsLeft(5_000, 4_999))
        assertEquals(0, secondsLeft(5_000, 5_000))
        assertEquals(0, secondsLeft(5_000, 9_000))
    }
}
