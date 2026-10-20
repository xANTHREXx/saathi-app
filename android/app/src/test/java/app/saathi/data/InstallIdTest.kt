package app.saathi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class InstallIdTest {
    @Test
    fun generatesValidUuidFormat() {
        val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        val generated = InstallId.newUuid()
        assertTrue("Generated ID should match UUID pattern: $generated", uuidRegex.matches(generated))
    }
}
