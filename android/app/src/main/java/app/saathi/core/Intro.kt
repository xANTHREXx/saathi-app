package app.saathi.core

/** The key a contact is remembered by once introduced: the normalised number, or what she typed if it won't normalise. */
fun introKey(c: Contact): String = normalisePhone(c.phone) ?: c.phone.trim()

/**
 * Contacts who haven't yet had the "I've added you to Saathi" text. [introduced] holds [introKey]s, so editing
 * a contact's name doesn't text them again, and the same number typed two ways counts once.
 */
fun toIntroduce(contacts: List<Contact>, introduced: List<String>): List<Contact> {
    val done = introduced.toSet()
    return contacts.filter { it.phone.isNotBlank() }.distinctBy { introKey(it) }.filterNot { introKey(it) in done }
}
