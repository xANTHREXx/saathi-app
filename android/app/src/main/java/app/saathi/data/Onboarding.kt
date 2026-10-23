package app.saathi.data

import android.content.Context
import app.saathi.core.practiceOutcome

fun firstTime(seen: Set<String>, key: String): Boolean = !seen.contains(key)

object Onboarding {
    private const val PREFS_NAME = "saathi_onboarding"
    private const val KEY_SEEN = "seen_keys"
    private const val KEY_PRACTICE_DONE = "practice_done"
    private const val KEY_CARD_FIRST_SHOWN = "card_first_shown_at"
    private const val KEY_CARD_SNOOZED_UNTIL = "card_snoozed_until"
    private const val KEY_TIPS = "tips_shown"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True only the first time [key] is encountered per install. */
    fun once(ctx: Context, key: String): Boolean {
        val p = prefs(ctx)
        val seen = p.getStringSet(KEY_SEEN, emptySet()) ?: emptySet()
        if (firstTime(seen, key)) {
            p.edit().putStringSet(KEY_SEEN, seen + key).apply()
            return true
        }
        return false
    }

    fun practiceDone(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_PRACTICE_DONE, false)

    fun markPracticeDone(ctx: Context, answered: Boolean, from: String = "setup") {
        val already = practiceDone(ctx)
        if (practiceOutcome(answered, already)) {
            prefs(ctx).edit().putBoolean(KEY_PRACTICE_DONE, true).apply()
            TelemetryManager.trackOnboarding(ctx, "practice_done", mapOf("answered" to answered))
        }
    }

    fun cardFirstShownAt(ctx: Context): Long =
        prefs(ctx).getLong(KEY_CARD_FIRST_SHOWN, 0L)

    fun setCardFirstShownAt(ctx: Context, timestampMs: Long) {
        prefs(ctx).edit().putLong(KEY_CARD_FIRST_SHOWN, timestampMs).apply()
    }

    fun cardSnoozedUntil(ctx: Context): Long =
        prefs(ctx).getLong(KEY_CARD_SNOOZED_UNTIL, 0L)

    fun setCardSnoozedUntil(ctx: Context, timestampMs: Long) {
        prefs(ctx).edit().putLong(KEY_CARD_SNOOZED_UNTIL, timestampMs).apply()
    }

    fun tipShown(ctx: Context, tip: String): Boolean {
        val tips = prefs(ctx).getStringSet(KEY_TIPS, emptySet()) ?: emptySet()
        return tips.contains(tip)
    }

    fun markTip(ctx: Context, tip: String) {
        val p = prefs(ctx)
        val tips = p.getStringSet(KEY_TIPS, emptySet()) ?: emptySet()
        p.edit().putStringSet(KEY_TIPS, tips + tip).apply()
    }
}
