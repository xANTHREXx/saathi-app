package app.saathi.core

const val PRACTICE_CARD_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000L // 14 days
const val PRACTICE_CARD_SNOOZE_MS = 3L * 24 * 60 * 60 * 1000L // 3 days

/**
 * Governs whether the "Try a practice check-in" card appears on Home:
 * - True when practice has not yet been done,
 * - and current time hasn't passed 14 days since it was first seen (or it was never seen yet),
 * - and current time is at or beyond any snooze window.
 */
fun practiceCardVisible(
    practiceDone: Boolean,
    firstShownAt: Long,
    snoozedUntil: Long,
    nowMs: Long,
): Boolean {
    if (practiceDone) return false
    if (nowMs < snoozedUntil) return false
    if (firstShownAt != 0L && nowMs >= firstShownAt + PRACTICE_CARD_MAX_AGE_MS) return false
    return true
}
