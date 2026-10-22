package app.saathi.core

/** A rectangle in pixels. Used instead of android.graphics.Rect, which is only a stub in unit tests. */
data class Region(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f

    fun holds(x: Float, y: Float) = x in left..right && y in top..bottom
}

/** One line of text the recogniser found, and where it sits in the upright picture. */
class TextLine(val text: String, val box: Region?)

/**
 * The plate-shaped guide on the live camera, and the part of the picture that counts. The camera sees more than the
 * guide shows, and a street has other vehicles in it, so only text inside the guide is read: another vehicle's plate
 * must never be taken for the one she is checking. The overlay is drawn from [frame] and the reading is limited by
 * [readRegion], which is built from the same [frame], so the two cannot drift apart.
 */
object PlateGuide {
    // Indian plates run from about 4:1 (one line) to about 2:1 (the two-line kind on autos and bikes); one frame fits both.
    const val RATIO = 2.2f

    /** How much of the view's width the frame takes, unless the view is so short that its height limits it first. */
    private const val WIDTH_OF_VIEW = 0.88f
    private const val HEIGHT_OF_VIEW = 0.7f

    // Read a little beyond the drawn frame, so a plate that looks inside it on screen is never turned away.
    private const val READ_MARGIN = 0.10f

    /** The drawn frame in a view of this size, centred. */
    fun frame(viewW: Float, viewH: Float): Region {
        val w = minOf(viewW * WIDTH_OF_VIEW, viewH * HEIGHT_OF_VIEW * RATIO)
        val h = w / RATIO
        val left = (viewW - w) / 2f
        val top = (viewH - h) / 2f
        return Region(left, top, left + w, top + h)
    }

    /** Size of the picture once turned upright, which is how the recogniser reports where text is. */
    fun uprightSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
        if (rotationDegrees % 180 == 0) width to height else height to width

    /**
     * The part of the upright picture to read, in its pixels: the guide frame plus [READ_MARGIN] on every side.
     * The view shows the picture the way PreviewView does with FILL_CENTER (scaled to cover, the overflow cropped
     * evenly), and the preview and the analysis share one aspect ratio, so the picture is placed in the view first
     * and the frame is mapped back onto it. Null while the view has no size yet.
     */
    fun readRegion(viewW: Float, viewH: Float, imageW: Float, imageH: Float): Region? {
        if (viewW <= 0f || viewH <= 0f || imageW <= 0f || imageH <= 0f) return null
        val scale = maxOf(viewW / imageW, viewH / imageH)
        val offsetX = (viewW - imageW * scale) / 2f
        val offsetY = (viewH - imageH * scale) / 2f
        val f = frame(viewW, viewH)
        val marginX = f.width * READ_MARGIN
        val marginY = f.height * READ_MARGIN
        return Region(
            ((f.left - marginX - offsetX) / scale).coerceAtLeast(0f),
            ((f.top - marginY - offsetY) / scale).coerceAtLeast(0f),
            ((f.right + marginX - offsetX) / scale).coerceAtMost(imageW),
            ((f.bottom + marginY - offsetY) / scale).coerceAtMost(imageH),
        )
    }

    /**
     * The text of the lines whose centre lies inside [region], one per line, in reading order: top to bottom, and
     * left to right along a row. The two lines of an auto's plate come out in the right order whatever order they
     * were found in. A line with no position can't be placed, so it is left out.
     */
    fun textInside(lines: List<TextLine>, region: Region): String {
        val inside = lines.mapNotNull { line ->
            line.box?.takeIf { region.holds(it.centerX, it.centerY) }?.let { line to it }
        }.sortedBy { (_, box) -> box.centerY }
        // Lines whose centres sit within half a line height of the row's first line share that row.
        val rows = mutableListOf<MutableList<Pair<TextLine, Region>>>()
        for (item in inside) {
            val row = rows.lastOrNull()
            val first = row?.first()?.second
            if (row != null && first != null && item.second.centerY - first.centerY < first.height / 2f) row += item else rows += mutableListOf(item)
        }
        return rows.flatMap { row -> row.sortedBy { (_, box) -> box.left } }.joinToString("\n") { (line, _) -> line.text }
    }
}
