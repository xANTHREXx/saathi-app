package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleNoteTest {
    @Test fun countsRides() {
        assertEquals(VehicleNote(13, noneRaised = true, sample = false), vehicleNote(13, 0, flagged = false, sample = false))
        assertEquals(VehicleNote(1, noneRaised = true, sample = false), vehicleNote(1, 0, flagged = false, sample = false))
    }

    @Test fun carriesTheSampleFlag() {
        assertEquals(VehicleNote(4, noneRaised = true, sample = true), vehicleNote(4, 0, flagged = false, sample = true))
    }

    @Test fun neverClaimsNoneWhenSomeReported() {
        assertFalse(vehicleNote(13, 2, flagged = false, sample = false)!!.noneRaised)
    }

    @Test fun silentWhenNothingOrFlagged() {
        assertNull(vehicleNote(0, 0, flagged = false, sample = false))
        assertNull(vehicleNote(9, 0, flagged = true, sample = false))
    }
}
