package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressTextTest {
    @Test
    fun joinsPartsInOrder() {
        assertEquals("12th Main, Indiranagar, Bengaluru", addressLine(listOf("12th Main", " Indiranagar ", "Bengaluru")))
    }

    @Test
    fun skipsPlusCodes() {
        assertEquals("Indiranagar, Bengaluru", addressLine(listOf("7J4V+2X", "Indiranagar", "Bengaluru")))
    }

    @Test
    fun skipsNumericOnlyAndBlanks() {
        assertEquals("Indiranagar", addressLine(listOf("42", null, "  ", "Indiranagar")))
    }

    @Test
    fun skipsRepeatsIgnoringCase() {
        assertEquals("Indiranagar, Bengaluru", addressLine(listOf("Indiranagar", "indiranagar", "Bengaluru", "BENGALURU")))
    }

    @Test
    fun nothingLeftIsNull() {
        assertNull(addressLine(listOf(null, "", "  ", "560038", "7J4V+2X")))
        assertNull(addressLine(emptyList()))
    }
}
