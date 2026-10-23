package app.saathi.core

/**
 * Returns whether to record a practice outcome.
 * Only the first completion per install (answered or timed out) is recorded.
 */
fun practiceOutcome(answered: Boolean, alreadyDone: Boolean): Boolean = !alreadyDone
