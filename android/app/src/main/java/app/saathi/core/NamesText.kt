package app.saathi.core

/**
 * The names worth showing: trimmed, blanks dropped. The screens join them in her language ("Amma and Riya",
 * "Amma, Riya and Chinu"), and say "your contacts" when none are left.
 */
fun cleanNames(names: List<String>): List<String> = names.map { it.trim() }.filter { it.isNotEmpty() }

/** Whole seconds left in a window, rounded up so the last number shown is 1, never 0. */
fun secondsLeft(endsAtMs: Long, nowMs: Long): Int = ((endsAtMs - nowMs).coerceAtLeast(0) + 999).div(1000).toInt()
