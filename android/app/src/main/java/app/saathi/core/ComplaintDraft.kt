package app.saathi.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A first-person complaint she can edit and file herself, built only from what the trip record shows.
 * Anything the record doesn't know is left in [brackets]; nothing is invented.
 */
object ComplaintDraft {
    data class Facts(
        val caseId: String,
        val startedAt: Long,
        val endedAt: Long?,
        val mode: String,
        val app: String?,
        val plate: String?,
        val plateCheck: String?,
        val events: List<String>,
        val lat: Double?,
        val lng: Double?,
        val evidence: List<Pair<String, String>>,
    )

    private val day = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun draft(f: Facts, zone: ZoneId = ZoneId.of("Asia/Kolkata")): String {
        val start = Instant.ofEpochSecond(f.startedAt).atZone(zone)
        val end = f.endedAt?.let { Instant.ofEpochSecond(it).atZone(zone) }
        val vehicle = when (f.mode.uppercase()) {
            "AUTO" -> "an auto-rickshaw"; "CAB" -> "a cab"; "BIKE" -> "a bike taxi"; "BUS" -> "a bus"; "TRAIN" -> "a train"
            else -> "my journey on foot"
        }
        return buildString {
            appendLine("To,")
            appendLine("The Station House Officer,")
            appendLine("[Police station name and city]")
            appendLine()
            appendLine("Subject: Complaint about an incident on ${day.format(start)} while travelling in $vehicle")
            appendLine()
            appendLine("Sir/Madam,")
            appendLine()
            append("I, [your full name], [age], residing at [your address], phone [your number], wish to report the following. ")
            append("On ${day.format(start)}, at about ${time.format(start).lowercase()}, I was travelling in $vehicle")
            f.app?.let { append(" booked through $it") }
            f.plate?.let { append(", vehicle number $it") }
            appendLine(".")
            if (f.plateCheck?.startsWith("mismatch") == true) {
                appendLine()
                appendLine("The vehicle that picked me up did not have the number plate shown in my booking (${f.plateCheck.removePrefix("mismatch: ")}).")
            }
            if (f.events.isNotEmpty()) {
                appendLine()
                appendLine("My phone's trip record shows: ${f.events.joinToString("; ")}.")
            }
            end?.let { e ->
                append("The trip record ends at ${time.format(e).lowercase()}")
                if (f.lat != null && f.lng != null) append(", near %.5f, %.5f".format(Locale.ENGLISH, f.lat, f.lng))
                appendLine(".")
            }
            appendLine()
            appendLine("What happened: [describe in your own words what happened, who was involved, and anyone who saw it].")
            appendLine()
            appendLine("I request you to register my complaint and take action as per law. If this happened outside this station's area, I request you to register a Zero FIR and transfer it.")
            if (f.evidence.isNotEmpty()) {
                appendLine()
                appendLine("Evidence I can provide (trip record ${f.caseId}; the SHA-256 fingerprints show the files were not changed):")
                f.evidence.forEach { (name, sha) -> appendLine("- $name: $sha") }
            }
            appendLine()
            appendLine("Yours faithfully,")
            appendLine("[Your name]")
            append("[Date]")
        }
    }
}
