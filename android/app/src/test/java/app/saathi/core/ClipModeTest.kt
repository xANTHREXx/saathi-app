package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipModeTest {
    @Test
    fun returnsPosterOnlyWhenScaleIsZero() {
        assertEquals(ClipMode.POSTER, clipMode(0f))
        assertEquals(ClipMode.PLAY, clipMode(1f))
        assertEquals(ClipMode.PLAY, clipMode(0.5f))
    }
}
