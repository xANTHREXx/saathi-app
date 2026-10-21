package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemTest {
    @Test
    fun xiaomiFamily() {
        assertEquals(Oem.XIAOMI, Oem.from("Xiaomi", "Xiaomi"))
        assertEquals(Oem.XIAOMI, Oem.from("Xiaomi", "Redmi"))
        assertEquals(Oem.XIAOMI, Oem.from("Xiaomi", "POCO"))
    }

    @Test
    fun vivoFamily() {
        assertEquals(Oem.VIVO, Oem.from("vivo", "vivo"))
        assertEquals(Oem.VIVO, Oem.from("vivo", "iQOO"))
    }

    @Test
    fun oppoRealmeAndOneplusAreTheirOwn() {
        assertEquals(Oem.OPPO, Oem.from("OPPO", "OPPO"))
        assertEquals(Oem.REALME, Oem.from("realme", "realme"))
        assertEquals(Oem.ONEPLUS, Oem.from("OnePlus", "OnePlus"))
    }

    @Test
    fun brandWinsWhenTheManufacturerIsTheParent() {
        assertEquals(Oem.REALME, Oem.from("OPPO", "realme"))
        assertEquals(Oem.XIAOMI, Oem.from("unknown", "Redmi"))
    }

    @Test
    fun samsungAndHuaweiAndHonor() {
        assertEquals(Oem.SAMSUNG, Oem.from("samsung", "samsung"))
        assertEquals(Oem.HUAWEI, Oem.from("HUAWEI", "HUAWEI"))
        assertEquals(Oem.HUAWEI, Oem.from("HONOR", "HONOR"))
    }

    @Test
    fun stockLikePhonesNeedNoCard() {
        for ((m, b) in listOf("motorola" to "motorola", "Google" to "google", "HMD Global" to "Nokia", null to null, "" to "")) {
            assertEquals(Oem.OTHER, Oem.from(m, b))
        }
        assertFalse(Oem.OTHER.needsCard)
        assertTrue(Oem.XIAOMI.needsCard)
    }

    @Test
    fun spacesAndCaseDontMatter() = assertEquals(Oem.XIAOMI, Oem.from("  XIAOMI ", null))

    @Test
    fun everyMakerListsScreensToTryAndOtherListsNone() {
        Oem.values().filter { it != Oem.OTHER }.forEach { assertTrue("$it has targets", it.targets.isNotEmpty()) }
        assertTrue(Oem.OTHER.targets.isEmpty())
        assertEquals(Oem.OPPO.targets, Oem.REALME.targets)
    }
}
