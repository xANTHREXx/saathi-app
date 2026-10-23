package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class AlertPreviewTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    @Test
    fun previewAlertWithName() {
        val s = previewAlert("Priya", ist)
        assertTrue("Expected to start with 'Saathi alert: Priya' but was: $s", s.startsWith("Saathi alert: Priya"))
        assertTrue("Expected to contain vehicle plate 'KA05AB1234' but was: $s", s.contains("KA05AB1234"))
    }

    @Test
    fun previewAlertFallsBackToDefaultWhenBlank() {
        val s = previewAlert("", ist)
        assertTrue("Expected to start with 'Saathi alert: Your contact' but was: $s", s.startsWith("Saathi alert: Your contact"))
        assertTrue("Expected to contain vehicle plate 'KA05AB1234' but was: $s", s.contains("KA05AB1234"))
    }
}
