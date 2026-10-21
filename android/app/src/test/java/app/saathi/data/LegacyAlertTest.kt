package app.saathi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyAlertTest {
    @Test fun readsAnAlertLine() {
        assertEquals(AlertLogEntry(1792604360, listOf("Chinu")), legacyAlertEntry("NO_RESPONSE at 1792604360: told Chinu"))
    }

    @Test fun readsSeveralNamesAndRetries() {
        assertEquals(AlertLogEntry(1792604400, listOf("Chinu", "Amma"), retry = true),
            legacyAlertEntry("RETRY at 1792604400: told Chinu, Amma"))
    }

    @Test fun nobodyToldIsAnEmptyList() {
        assertEquals(AlertLogEntry(1792604360, emptyList()), legacyAlertEntry("HOLD at 1792604360: told "))
    }

    @Test fun anythingElseIsNotRead() {
        assertNull(legacyAlertEntry("stopped 5 min"))
    }
}
