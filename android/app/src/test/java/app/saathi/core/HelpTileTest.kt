package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpTileTest {
    private val mum = Contact("Mum", "+919800000001", "Pune")
    private val dad = Contact("Dad", "+919800000002", "Pune")

    @Test
    fun setUpNeedsOnboardingAndAContact() {
        assertTrue(isSetUp(true, listOf(mum)))
        assertFalse(isSetUp(false, listOf(mum)))
        assertFalse(isSetUp(true, emptyList()))
    }

    @Test
    fun idleNamesTheOnlyContact() {
        assertEquals(HelpTileLook(false, "Tap to text Mum"), helpTileLook(true, listOf(mum), pending = false, alertActive = false))
    }

    @Test
    fun idleWithSeveralContactsSaysYourContacts() {
        assertEquals("Tap to text your contacts", helpTileLook(true, listOf(mum, dad), false, false).subtitle)
    }

    @Test
    fun pendingIsActiveAndTapStops() {
        assertEquals(HelpTileLook(true, "Texting in 5 s · tap to stop"), helpTileLook(true, listOf(mum), pending = true, alertActive = false))
    }

    @Test
    fun alertActiveIsActiveAndWinsOverPending() {
        assertEquals(HelpTileLook(true, "Contacts told"), helpTileLook(true, listOf(mum), pending = true, alertActive = true))
    }

    @Test
    fun notSetUpAsksToFinish() {
        assertEquals(HelpTileLook(false, "Finish setup first"), helpTileLook(false, emptyList(), false, false))
    }

    @Test
    fun clickRaisesWhenIdleAndSetUp() {
        assertEquals(HelpTileAction.RAISE, helpTileAction(setUp = true, pending = false, alertActive = false))
    }

    @Test
    fun clickOpensAppWhenNotSetUp() {
        assertEquals(HelpTileAction.OPEN_APP, helpTileAction(setUp = false, pending = false, alertActive = false))
    }

    @Test
    fun clickCancelsAPendingAlert() {
        assertEquals(HelpTileAction.CANCEL_PENDING, helpTileAction(setUp = true, pending = true, alertActive = false))
        assertEquals(HelpTileAction.CANCEL_PENDING, helpTileAction(setUp = false, pending = true, alertActive = false))
    }

    @Test
    fun clickNeverUndoesASentAlert() {
        assertEquals(HelpTileAction.REFRESH_ONLY, helpTileAction(setUp = true, pending = false, alertActive = true))
        assertEquals(HelpTileAction.REFRESH_ONLY, helpTileAction(setUp = true, pending = true, alertActive = true))
    }
}
