package app.saathi.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val EMAIL = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}")
private val COORDS = Regex("-?\\d{1,3}\\.\\d{4,}\\s*,\\s*-?\\d{1,3}\\.\\d{4,}")
private val NUMBER = Regex("\\+?\\d(?:[ \\-+]?\\d){5,}")

/**
 * Removes what could identify her from text that leaves the phone: emails, coordinate pairs and
 * runs of 6 or more digits (phone numbers, even with spaces, dashes or +). Short numbers such as
 * the 123 in "File.kt:123" stay, so stack traces remain useful.
 */
fun scrub(s: String): String =
    s.replace(EMAIL, "[email]")
        .replace(COORDS, "[place]")
        .replace(NUMBER, "[number]")

/** The start of the hour (UTC) that [nowMs] falls in, as ISO "yyyy-MM-dd'T'HH:00:00'Z'". */
fun hourIso(nowMs: Long = System.currentTimeMillis()): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:00:00'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return fmt.format(Date(nowMs))
}
