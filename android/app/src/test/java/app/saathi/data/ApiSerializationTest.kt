package app.saathi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiSerializationTest {
    @Test
    fun parseReleaseReturnsNullWhenLatestIsNull() {
        val json = """{"latest": null}"""
        assertNull(Api.parseRelease(json))
    }

    @Test
    fun parseReleaseParsesValidReleaseJson() {
        val json = """
        {
            "latest": {
                "versionCode": 2,
                "versionName": "0.2",
                "apkUrl": "https://github.com/example/saathi.apk",
                "changelog": "Fixes and improvements",
                "isMandatory": true
            }
        }
        """.trimIndent()
        val rel = Api.parseRelease(json)
        assertNotNull(rel)
        assertEquals(2, rel?.versionCode)
        assertEquals("0.2", rel?.versionName)
        assertEquals("https://github.com/example/saathi.apk", rel?.apkUrl)
        assertEquals("Fixes and improvements", rel?.changelog)
        assertEquals(true, rel?.isMandatory)
    }

    @Test
    fun formatTelemetryBatchBuildsValidJson() {
        val body = Api.buildTelemetryJson(
            installId = "test-uuid-123",
            versionCode = 1,
            versionName = "0.1",
            osVersion = 34,
            deviceModel = "Pixel 7",
            crashesJson = """[{"exceptionType":"NullPointerException","message":"oops","stackTrace":"at Foo.kt:10","crashedAt":"2026-10-21T00:00:00Z"}]""",
            eventsJson = """[{"name":"trip_started","properties":{"mode":"uber"},"occurredAt":"2026-10-21T00:00:00Z"}]"""
        )
        assertTrue(body.contains("\"installId\":\"test-uuid-123\""))
        assertTrue(body.contains("\"crashes\":["))
        assertTrue(body.contains("\"events\":["))
    }
}
