package app.saathi.core

/** A plate check, read back from the note kept on the trip ("match: KA05AB1234" or "mismatch: saw X, booked Y"). */
data class PlateCheck(val matched: Boolean, val seen: String, val booked: String?)

private val MISMATCH_NOTE = Regex("""^mismatch: saw (.*), booked (.*)$""")

fun parsePlateCheck(note: String): PlateCheck? {
    MISMATCH_NOTE.matchEntire(note)?.let { return PlateCheck(false, it.groupValues[1], it.groupValues[2]) }
    if (note.startsWith("match: ")) return PlateCheck(true, note.removePrefix("match: "), null)
    return null
}

object Plate {
    // Registration codes in use. Without this check, any four letters-and-digits in OCR noise would pass for a plate.
    private val STATES = setOf(
        "AN", "AP", "AR", "AS", "BR", "CG", "CH", "DD", "DL", "DN", "GA", "GJ", "HP", "HR", "JH", "JK", "KA", "KL", "LA", "LD",
        "MH", "ML", "MN", "MP", "MZ", "NL", "OD", "OR", "PB", "PY", "RJ", "SK", "TN", "TR", "TS", "UK", "UP", "WB", "UA",
    )

    // What a camera typically misreads: a digit standing where a letter belongs, and the other way round.
    private val AS_LETTER = mapOf('0' to 'O', '1' to 'I', '2' to 'Z', '5' to 'S', '8' to 'B', '6' to 'G')
    private val AS_DIGIT = mapOf('O' to '0', 'D' to '0', 'Q' to '0', 'I' to '1', 'L' to '1', 'T' to '1', 'Z' to '2', 'S' to '5', 'B' to '8', 'G' to '6')

    private const val MAX_FIXES = 3
    private const val MIN_REAL_DIGITS = 3

    // 'L' is a letter position, 'D' a digit position. Standard: state, district (1-2), series (0-3), number (4).
    // Bharat series: year (2), "BH", number (4), series (1-2).
    private val SHAPES: List<Pair<String, Boolean>> =
        (2 downTo 1).flatMap { d -> (2..3).plus(listOf(1, 0)).map { s -> "LL" + "D".repeat(d) + "L".repeat(s) + "DDDD" to false } } +
            (2 downTo 1).map { s -> "DDLLDDDD" + "L".repeat(s) to true }

    private val NOT_ALNUM = Regex("[^A-Z0-9]+")

    private class Fit(val plate: String, val cost: Int, val start: Int)

    fun normalise(s: String): String = s.uppercase().filter { it.isLetterOrDigit() }

    /** O and 0, I and 1 are the same here, so a camera misread doesn't raise a false mismatch. */
    fun matches(booked: String, seen: String): Boolean = canonical(booked) == canonical(seen)

    /**
     * The Indian plate in free text: a ride-app notification, or whatever OCR made of a photo. Works offline.
     * Tolerates OCR confusions (O/0, I/1, S/5, B/8...), stray symbols and a plate split over two lines, and returns
     * the compact form ("KA05AB1234"). When several things look like plates, the one needing fewest corrections wins.
     */
    fun find(text: String): String? {
        val lines = text.uppercase().lines()
            .map { line -> line.split(NOT_ALNUM).filter { it.isNotEmpty() && it != "IND" } }
            .filter { it.isNotEmpty() }
        // Each line alone first, then all of them joined: "KA 05" / "AB 1234" only reads as a plate once joined.
        val sources = (lines + listOf(lines.flatten())).map { Source(it) }
        return sources.flatMapIndexed { i, s -> best(s)?.let { listOf(i to it) } ?: emptyList() }
            .minWithOrNull(compareBy({ it.second.cost }, { it.first }, { it.second.start }))?.second?.plate
    }

    /** The words of a text run together, remembering where one word ended and the next began. */
    private class Source(words: List<String>) {
        val chars = words.joinToString("")
        val gapBefore = BooleanArray(chars.length).also { gap ->
            var at = 0
            for (w in words) { if (at < gap.size) gap[at] = true; at += w.length }
        }
    }

    private fun best(s: Source): Fit? {
        var found: Fit? = null
        for (start in s.chars.indices) for ((shape, bharat) in SHAPES) {
            val fit = fit(s, start, shape, bharat) ?: continue
            val cur = found
            if (cur == null || fit.cost < cur.cost || (fit.cost == cur.cost && fit.start == cur.start && fit.plate.length > cur.plate.length)) found = fit
        }
        return found
    }

    /** Reads [shape] at [start], correcting each character to the kind its position needs; null if it can't be a plate. */
    private fun fit(src: Source, start: Int, shape: String, bharat: Boolean): Fit? {
        val s = src.chars
        if (start + shape.length > s.length) return null
        val out = StringBuilder()
        var fixes = 0
        var realDigits = 0
        for ((i, kind) in shape.withIndex()) {
            val ch = s[start + i]
            if (kind == 'L') {
                if (ch.isDigit()) { out.append(AS_LETTER[ch] ?: return null); fixes++ } else out.append(ch)
            } else {
                if (ch.isDigit()) { out.append(ch); realDigits++ } else { out.append(AS_DIGIT[ch] ?: return null); fixes++ }
            }
        }
        val plate = out.toString()
        // There is no district 0, which also stops "KA0SAB1234" reading as district 0, series SAB.
        val ok = if (bharat) plate.substring(2, 4) == "BH" else plate.substring(0, 2) in STATES &&
            plate.substring(2, 2 + shape.drop(2).takeWhile { it == 'D' }.length).any { it != '0' }
        if (!ok || fixes > MAX_FIXES || realDigits < MIN_REAL_DIGITS) return null
        // A digit run straight on from the number means this read stopped short ("KA05A8123|4" rather than "KA05AB1234").
        val next = start + shape.length
        val stoppedShort = next < s.length && s[next].isDigit() && !src.gapBefore[next]
        return Fit(plate, if (stoppedShort) fixes + 1 else fixes, start)
    }

    private fun canonical(s: String) = normalise(s).replace('O', '0').replace('I', '1')
}
