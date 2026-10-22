package app.saathi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateGuideFrameTest {
    @Test
    fun theFrameIsCentredAndPlateShaped() {
        val f = PlateGuide.frame(360f, 640f)
        assertEquals(360f / 2, f.centerX, 0.01f)
        assertEquals(640f / 2, f.centerY, 0.01f)
        assertEquals(PlateGuide.RATIO, f.width / f.height, 0.001f)
        assertEquals(360f * 0.88f, f.width, 0.01f)
    }

    @Test
    fun aPhoneOnItsSideLimitsTheFrameByHeight() {
        val f = PlateGuide.frame(1000f, 400f)
        assertEquals(400f * 0.7f * PlateGuide.RATIO, f.width, 0.01f)
        assertEquals(PlateGuide.RATIO, f.width / f.height, 0.001f)
        assertTrue(f.top > 0f && f.bottom < 400f)
    }

    @Test
    fun rotatingTheCameraSwapsThePictureSidesOnlyForQuarterTurns() {
        assertEquals(1280 to 720, PlateGuide.uprightSize(1280, 720, 0))
        assertEquals(720 to 1280, PlateGuide.uprightSize(1280, 720, 90))
        assertEquals(1280 to 720, PlateGuide.uprightSize(1280, 720, 180))
        assertEquals(720 to 1280, PlateGuide.uprightSize(1280, 720, 270))
    }
}

class PlateReadRegionTest {
    @Test
    fun noRegionUntilTheViewHasASize() {
        assertNull(PlateGuide.readRegion(0f, 0f, 720f, 1280f))
        assertNull(PlateGuide.readRegion(360f, 640f, 0f, 0f))
    }

    @Test
    fun sameShapeViewMapsTheFramePlusItsMarginOntoThePicture() {
        // View 360x640 shows the 720x1280 picture whole, at half size. The frame is 316.8 x 144 at (21.6, 248).
        val r = PlateGuide.readRegion(360f, 640f, 720f, 1280f)!!
        assertEquals(640f, r.centerY, 0.5f)
        assertEquals(467.2f, r.top, 0.5f) // (248 - 14.4) / 0.5
        assertEquals(812.8f, r.bottom, 0.5f)
        // Wide enough to reach past both edges of the picture, so it is held to them.
        assertEquals(0f, r.left, 0.01f)
        assertEquals(720f, r.right, 0.01f)
    }

    @Test
    fun theRegionIsLargerThanTheDrawnFrame() {
        val r = PlateGuide.readRegion(360f, 640f, 720f, 1280f)!!
        val f = PlateGuide.frame(360f, 640f)
        assertTrue(r.height > f.height / 0.5f)
    }

    @Test
    fun aSquareViewCropsThePictureSidesAndTheRegionStaysCentred() {
        // The 720x1280 picture covers a 400x400 view by width, so the top and bottom are cropped off.
        val r = PlateGuide.readRegion(400f, 400f, 720f, 1280f)!!
        assertEquals(640f, r.centerY, 0.5f)
        assertEquals(360f, r.centerX, 0.5f)
        assertEquals(467.2f, r.top, 0.5f)
        assertEquals(812.8f, r.bottom, 0.5f)
    }

    @Test
    fun aWideViewCropsThePictureSoTheRegionIsSmallerThanTheWholeWidth() {
        // Phone on its side: 1000x400 view, 720x1280 picture scaled to 1000 wide, so most of its height is cropped.
        val r = PlateGuide.readRegion(1000f, 400f, 720f, 1280f)!!
        assertEquals(93.9f, r.left, 0.5f)
        assertEquals(626.1f, r.right, 0.5f)
        assertEquals(519.0f, r.top, 0.5f)
        assertEquals(761.0f, r.bottom, 0.5f)
    }

    @Test
    fun theRegionNeverLeavesThePicture() {
        for ((vw, vh) in listOf(360f to 640f, 400f to 400f, 1000f to 400f, 300f to 60f)) {
            val r = PlateGuide.readRegion(vw, vh, 720f, 1280f)!!
            assertTrue(r.left >= 0f && r.top >= 0f && r.right <= 720f && r.bottom <= 1280f)
        }
    }
}

class PlateTextInsideTest {
    private val region = Region(100f, 400f, 600f, 700f)
    private fun line(text: String, left: Float, top: Float, right: Float, bottom: Float) =
        TextLine(text, Region(left, top, right, bottom))

    @Test
    fun aLineWhoseCentreIsInsideIsKept() {
        assertEquals("KA05AB1234", PlateGuide.textInside(listOf(line("KA05AB1234", 200f, 500f, 400f, 560f)), region))
    }

    @Test
    fun aLineWhoseCentreIsOutsideIsDropped() {
        assertEquals("", PlateGuide.textInside(listOf(line("KA05AB1234", 700f, 500f, 900f, 560f)), region))
        assertEquals("", PlateGuide.textInside(listOf(line("KA05AB1234", 200f, 100f, 400f, 160f)), region))
    }

    @Test
    fun theCentreDecidesNotTheEdges() {
        // Both hang over the left edge; only the one centred inside the region counts.
        assertEquals("IN", PlateGuide.textInside(listOf(line("IN", 50f, 500f, 250f, 560f)), region))
        assertEquals("", PlateGuide.textInside(listOf(line("OUT", 20f, 500f, 160f, 560f)), region))
    }

    @Test
    fun aTwoLinePlateKeepsBothLinesInReadingOrder() {
        val top = line("KA 05", 200f, 480f, 400f, 540f)
        val bottom = line("AB 1234", 180f, 550f, 420f, 610f)
        // Found bottom line first; they still come out top to bottom, and the plate reads.
        val text = PlateGuide.textInside(listOf(bottom, top), region)
        assertEquals("KA 05\nAB 1234", text)
        assertEquals("KA05AB1234", Plate.find(text))
    }

    @Test
    fun linesOnOneRowReadLeftToRight() {
        val right = line("AB 1234", 300f, 490f, 500f, 550f)
        val left = line("KA 05", 150f, 494f, 290f, 554f)
        assertEquals("KA 05\nAB 1234", PlateGuide.textInside(listOf(right, left), region))
    }

    @Test
    fun anotherVehiclesPlateOutsideTheRegionIsNotRead() {
        val mine = line("KA05AB1234", 200f, 500f, 400f, 560f)
        val theirs = line("MH12DE5678", 750f, 520f, 950f, 580f)
        val text = PlateGuide.textInside(listOf(theirs, mine), region)
        assertEquals("KA05AB1234", Plate.find(text))
    }

    @Test
    fun withOnlyAnotherVehiclesPlateInViewNothingIsFound() {
        val theirs = line("MH12DE5678", 750f, 520f, 950f, 580f)
        assertNull(Plate.find(PlateGuide.textInside(listOf(theirs), region)))
    }

    @Test
    fun aLineWithNoPositionIsLeftOut() {
        assertEquals("", PlateGuide.textInside(listOf(TextLine("KA05AB1234", null)), region))
    }

    @Test
    fun noLinesGivesNoText() = assertEquals("", PlateGuide.textInside(emptyList(), region))
}
