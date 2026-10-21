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
    // State code, district number, optional series, four digits: "KA 05 AB 1234".
    private val indian = Regex("""\b([A-Z]{2})[\s-]?(\d{1,2})[\s-]?([A-Z]{0,3})[\s-]?(\d{4})\b""")

    fun normalise(s: String): String = s.uppercase().filter { it.isLetterOrDigit() }

    /** O and 0, I and 1 are the same here, so a camera misread doesn't raise a false mismatch. */
    fun matches(booked: String, seen: String): Boolean = canonical(booked) == canonical(seen)

    /** First Indian plate in free text, such as a ride-app notification. Works offline. */
    fun find(text: String): String? =
        indian.find(text.uppercase())?.groupValues?.drop(1)?.joinToString("")

    private fun canonical(s: String) = normalise(s).replace('O', '0').replace('I', '1')
}
