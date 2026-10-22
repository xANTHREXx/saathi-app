package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateTest {
    @Test
    fun normaliseStripsSpacesAndDashes() {
        assertEquals("KA05AB1234", Plate.normalise(" ka-05 ab 1234 "))
    }

    @Test
    fun plateVariantsMatch() {
        assertTrue(Plate.matches("KA05AB1234", "KA 05 AB 1234"))
        assertTrue(Plate.matches("KA05AB1234", "ka-05-ab-1234"))
    }

    @Test
    fun plateOcrConfusionsMatch() {
        assertTrue(Plate.matches("KA05AB1234", "KAO5AB1234"))
        assertTrue(Plate.matches("KA05AB1234", "KA05AB I234"))
    }

    @Test
    fun plateDifferentCharacterDoesNotMatch() {
        assertFalse(Plate.matches("KA05AB1234", "KA05AB1Z34"))
    }

    @Test
    fun plateDifferentNumber() {
        assertFalse(Plate.matches("KA05AB1234", "KA05XY9876"))
    }

    @Test
    fun findsPlateInNotificationText() {
        assertEquals("KA05AB1234", Plate.find("Ravi is arriving in your Auto KA 05 AB 1234 · 3 min"))
        assertEquals(null, Plate.find("Your driver is on the way"))
    }

    @Test
    fun findsPlateSplitOverTwoLines() {
        assertEquals("KA05AB1234", Plate.find("KA 05\nAB 1234"))
        assertEquals("KA05AB1234", Plate.find("KA 05\r\nAB 1234\r\n"))
    }

    @Test
    fun ignoresIndAndStraySymbols() {
        assertEquals("KA05AB1234", Plate.find("IND\nKA 05 AB 1234"))
        assertEquals("MH12DE1433", Plate.find("IND MH 12 DE 1433 |"))
        assertEquals("DL01CD5678", Plate.find("» DL-01 · CD ~ 5678 «"))
    }

    @Test
    fun correctsLettersReadAsDigitsAndBack() {
        // Letter positions
        assertEquals("KA05AB1234", Plate.find("KA05A81234")) // B as 8
        assertEquals("KA05SB1234", Plate.find("KA055B1234")) // S as 5
        assertEquals("KA05IB1234", Plate.find("KA051B1234")) // I as 1
        assertEquals("KA05OB1234", Plate.find("KA050B1234")) // O as 0
        // Digit positions
        assertEquals("KA05AB1234", Plate.find("KAO5AB1234")) // O as 0
        assertEquals("KA05AB1234", Plate.find("KA05ABI234")) // I as 1
        assertEquals("KA05AB1234", Plate.find("KA0SAB1234")) // S as 5
        assertEquals("KA05AB1834", Plate.find("KA05AB1B34")) // B as 8
        assertEquals("KA05AB1234", Plate.find("KA05AB123 4"))
        assertEquals("KA05AB1260", Plate.find("KA05AB12G0")) // G as 6
    }

    @Test
    fun prefersTheReadNeedingFewestCorrections() {
        assertEquals("MH12AB3456", Plate.find("KAOSAB1Z3 MH12AB3456"))
    }

    @Test
    fun rejectsUnknownStateCode() {
        assertNull(Plate.find("XX05AB1234"))
        assertNull(Plate.find("QQ 12 AB 3456"))
    }

    @Test
    fun readsBharatSeries() {
        assertEquals("22BH1234AA", Plate.find("22 BH 1234 AA"))
        assertEquals("22BH1234A", Plate.find("22BH1234A"))
        assertEquals("22BH1234AA", Plate.find("IND\n22 8H 1234\nAA"))
    }

    @Test
    fun findsPlateInASentence() {
        assertEquals("KA05AB1234", Plate.find("Please confirm that my auto is KA 05 AB 1234, thanks"))
    }

    @Test
    fun junkIsNotAPlate() {
        assertNull(Plate.find(""))
        assertNull(Plate.find("IND"))
        assertNull(Plate.find("SBI ATM 24x7 Open Now"))
        assertNull(Plate.find("1234 5678 9012"))
        assertNull(Plate.find("Your driver is on the way\nRavi - 4.8 stars"))
    }
}

class PlateCheckNoteTest {
    @Test
    fun readsAMatch() = org.junit.Assert.assertEquals(PlateCheck(true, "KA05AB1234", null), parsePlateCheck("match: KA05AB1234"))

    @Test
    fun readsAMismatch() = org.junit.Assert.assertEquals(
        PlateCheck(false, "KA05XY9876", "KA05AB1234"), parsePlateCheck("mismatch: saw KA05XY9876, booked KA05AB1234"),
    )

    @Test
    fun unknownTextIsNull() = org.junit.Assert.assertNull(parsePlateCheck("something else"))
}
