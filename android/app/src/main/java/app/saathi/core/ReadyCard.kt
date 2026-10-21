package app.saathi.core

/**
 * Who "Tell ... you added them" names: up to two first names, then a count. Kept as parts, not a
 * sentence, so each language can word the join itself.
 */
data class ToldNames(val first: String, val second: String? = null, val others: Int = 0)

/** "Chinu" / "Chinu and Amma" / "Chinu and 2 others"; null when no contact has a name to show. */
fun toldNames(contacts: List<Contact>): ToldNames? {
    val firsts = contacts.mapNotNull { it.name.trim().substringBefore(' ').takeIf { n -> n.isNotEmpty() } }
    return when (firsts.size) {
        0 -> null
        1 -> ToldNames(firsts[0])
        2 -> ToldNames(firsts[0], firsts[1])
        else -> ToldNames(firsts[0], others = firsts.size - 1)
    }
}

/** The numbers for one text to everyone, the way SMS apps read a `smsto:` address. */
fun smsRecipients(contacts: List<Contact>): String =
    contacts.map { it.phone.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(";")

/**
 * Whether Home shows "Two more things": not on a trip or in an alert, someone to tell, not waved
 * away, and at least one of the two still open.
 */
fun readyCardVisible(
    onTrip: Boolean, alertActive: Boolean, hasContacts: Boolean,
    dismissed: Boolean, toldDone: Boolean, tileDone: Boolean,
): Boolean = !onTrip && !alertActive && hasContacts && !dismissed && !(toldDone && tileDone)
