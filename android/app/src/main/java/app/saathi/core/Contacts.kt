package app.saathi.core

/** Turns what she typed into an E.164-style number, or null if it can't be one. */
fun normalisePhone(raw: String): String? {
    val s = raw.filterNot { it in " -.()" }
    fun isDigits(t: String) = t.isNotEmpty() && t.all { it in '0'..'9' }
    fun indianMobile(ten: String) = ten.length == 10 && ten[0] in '6'..'9'
    return when {
        s.startsWith("+") -> {
            val d = s.substring(1)
            if (isDigits(d) && d.length in 8..15) s else null
        }
        s.startsWith("00") -> {
            val d = s.substring(2)
            if (isDigits(d) && d.length in 8..15) "+$d" else null
        }
        !isDigits(s) -> null
        s.length == 4 -> s
        s.length == 11 && s[0] == '0' && indianMobile(s.takeLast(10)) -> "+91" + s.takeLast(10)
        s.length == 10 && indianMobile(s) -> "+91$s"
        s.length == 12 && s.startsWith("91") && indianMobile(s.takeLast(10)) -> "+$s"
        else -> null
    }
}

private val INDIAN_E164 = Regex("""^\+91(\d{5})(\d{5})$""")

/** Display form: "+919765250388" becomes "+91 97652 50388". Anything else is unchanged. */
fun formatPhone(e164: String): String {
    val m = INDIAN_E164.matchEntire(e164) ?: return e164
    return "+91 ${m.groupValues[1]} ${m.groupValues[2]}"
}

/** One-line note on when a contact is texted. */
fun cityNote(city: String): String =
    if (city.trim().equals(ANYWHERE, ignoreCase = true)) "Texted wherever you are"
    else "Texted when you're in ${city.trim()}"

enum class SetupField { YOUR_NAME, CONTACT_NAME, CONTACT_PHONE, CONTACTS }

data class SetupProblem(val field: SetupField, val message: String)

sealed interface PendingResult {
    data class Ok(val contacts: List<Contact>) : PendingResult
    data class Problem(val problem: SetupProblem) : PendingResult
}

/** Folds a contact she typed but hasn't added yet into [contacts], or says what is wrong with it. */
fun addPending(contacts: List<Contact>, name: String, phone: String, city: String): PendingResult {
    val n = name.trim()
    if (n.isEmpty() && phone.isBlank()) return PendingResult.Ok(contacts)
    if (phone.isBlank()) {
        return PendingResult.Problem(
            SetupProblem(SetupField.CONTACT_PHONE, "Add a phone number for $n."),
        )
    }
    val normalised = normalisePhone(phone)
        ?: return PendingResult.Problem(
            SetupProblem(
                SetupField.CONTACT_PHONE,
                "This number looks incomplete. Add the country code, like +91.",
            ),
        )
    if (n.isEmpty()) {
        return PendingResult.Problem(
            SetupProblem(SetupField.CONTACT_NAME, "Add a name for this number."),
        )
    }
    if (contacts.any { it.phone == normalised }) return PendingResult.Ok(contacts)
    return PendingResult.Ok(contacts + Contact(n, normalised, city.trim().ifBlank { ANYWHERE }))
}

/** What stops setup from finishing, or null if it is ready. */
fun setupProblem(yourName: String, contacts: List<Contact>): SetupProblem? = when {
    yourName.isBlank() ->
        SetupProblem(SetupField.YOUR_NAME, "Add your first name, so contacts know who the alert is from.")
    contacts.isEmpty() ->
        SetupProblem(SetupField.CONTACTS, "Add at least one person to text.")
    else -> null
}
