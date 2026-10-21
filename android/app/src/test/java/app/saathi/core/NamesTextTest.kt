package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NamesTextTest {
    @Test
    fun keepsNamesInOrder() = assertEquals(listOf("Amma", "Riya"), cleanNames(listOf("Amma", "Riya")))

    @Test
    fun noNamesIsEmpty() = assertEquals(emptyList<String>(), cleanNames(emptyList()))

    @Test
    fun blankNamesAreSkippedAndOthersTrimmed() {
        assertEquals(emptyList<String>(), cleanNames(listOf(" ", "")))
        assertEquals(listOf("Amma", "Riya"), cleanNames(listOf("Amma", " ", " Riya ")))
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
