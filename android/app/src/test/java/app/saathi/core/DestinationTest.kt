package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationTest {
    private val home = SavedPlace("Home", 12.9166, 77.6101)
    private val work = SavedPlace("Work", 12.9716, 77.5946)
    private val college = SavedPlace("College", 12.9352, 77.6245)
    private fun near(p: SavedPlace) = Fix(p.lat + 0.001, p.lng, 0) // about 110 m away

    @Test fun twoPlacesMeansTheOtherOne() {
        assertEquals(work, Destination.guess(near(home), listOf(home, work), emptyList()))
        assertEquals(home, Destination.guess(near(work), listOf(home, work), emptyList()))
    }

    @Test fun notAtASavedPlaceMeansNoGuess() {
        assertNull(Destination.guess(Fix(13.05, 77.70, 0), listOf(home, work), emptyList()))
    }

    @Test fun morePlacesUseHistory() {
        val places = listOf(home, work, college)
        assertNull(Destination.guess(near(home), places, emptyList()))
        assertNull(Destination.guess(near(home), places, listOf("Home" to "College")))
        val past = listOf("Home" to "College", "Home" to "Work", "Home" to "College", "Work" to "Home")
        assertEquals(college, Destination.guess(near(home), places, past))
    }

    @Test fun originIsNearestWithin300m() {
        assertEquals(home, Destination.origin(near(home), listOf(home, work)))
        assertNull(Destination.origin(Fix(home.lat + 0.004, home.lng, 0), listOf(home)))
    }
}
