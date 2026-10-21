package app.saathi.ui

import android.app.StatusBarManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileRequestTest {
    @Test
    fun addedOrAlreadyThereCountsAsDone() {
        assertTrue(tileRequestDone(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED))
        assertTrue(tileRequestDone(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED))
    }

    @Test
    fun declinedOrFailedDoesNot() {
        assertFalse(tileRequestDone(StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED))
        assertFalse(tileRequestDone(StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND))
    }
}
