package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleNoteTest {
    @Test fun countsRides() {
        assertEquals("13 rides with Saathi users · none raised a concern", vehicleNote(13, 0, flagged = false, sample = false))
        assertEquals("1 ride with Saathi users · none raised a concern", vehicleNote(1, 0, flagged = false, sample = false))
    }

    @Test fun neverSaysSafe() {
        for (n in listOf(1, 2, 13, 500)) for (s in listOf(true, false)) {
            assertFalse(vehicleNote(n, 0, false, s)!!.contains("safe", ignoreCase = true))
        }
    }

    @Test fun doesNotClaimNoneWhenSomeReported() {
        assertEquals("13 rides with Saathi users", vehicleNote(13, 2, flagged = false, sample = false))
    }

    @Test fun silentWhenNothingOrFlagged() {
        assertNull(vehicleNote(0, 0, flagged = false, sample = false))
        assertNull(vehicleNote(9, 0, flagged = true, sample = false))
    }
}
