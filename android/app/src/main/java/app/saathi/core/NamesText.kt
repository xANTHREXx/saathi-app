package app.saathi.core

/** "Chinu" / "Amma and Riya" / "Amma, Riya and Chinu"; "your contacts" when there is nobody to name. */
fun namesSentence(names: List<String>): String {
    val n = names.map { it.trim() }.filter { it.isNotEmpty() }
    return when (n.size) {
        0 -> "your contacts"
        1 -> n[0]
        else -> n.dropLast(1).joinToString(", ") + " and " + n.last()
    }
}

/** Whole seconds left in a window, rounded up so the last number shown is 1, never 0. */
fun secondsLeft(endsAtMs: Long, nowMs: Long): Int = ((endsAtMs - nowMs).coerceAtLeast(0) + 999).div(1000).toInt()
