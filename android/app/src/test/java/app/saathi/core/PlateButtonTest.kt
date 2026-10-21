package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlateButtonTest {
    @Test
    fun hailedAutoOrCabWithNoPlateCanRecordOne() {
        assertEquals(PlateButton.RECORD, plateButton(Mode.AUTO, null, null))
        assertEquals(PlateButton.RECORD, plateButton(Mode.CAB, null, null))
    }

    @Test
    fun hailedTripWithAPlateOffersNothing() {
        assertEquals(PlateButton.NONE, plateButton(Mode.AUTO, "KA05AB1234", null))
        assertEquals(PlateButton.NONE, plateButton(Mode.CAB, "KA05AB1234", null))
    }

    @Test
    fun rideAppTripWithABookedPlateComparesIt() {
        assertEquals(PlateButton.COMPARE, plateButton(Mode.AUTO, "KA05AB1234", "Uber"))
        assertEquals(PlateButton.COMPARE, plateButton(Mode.CAB, "KA05AB1234", "Ola"))
    }

    @Test
    fun rideAppTripWithNoPlateYetOffersNothing() {
        assertEquals(PlateButton.NONE, plateButton(Mode.CAB, null, "Uber"))
    }

    @Test
    fun otherModesWithNoPlateOfferNothing() {
        for (m in listOf(Mode.WALK, Mode.BUS, Mode.TRAIN, Mode.BIKE)) assertEquals(PlateButton.NONE, plateButton(m, null, null))
    }
}
