package app.saathi.core

/** Joins address parts into one readable line: skips blanks, plus codes ("7J4V+2X") and bare numbers, and repeats. */
fun addressLine(parts: List<String?>): String? {
    val kept = mutableListOf<String>()
    for (raw in parts) {
        val p = raw?.trim() ?: continue
        if (p.isEmpty() || p.contains('+') || p.none { it.isLetter() }) continue
        if (kept.any { it.equals(p, ignoreCase = true) }) continue
        kept += p
    }
    return kept.joinToString(", ").ifEmpty { null }
}
