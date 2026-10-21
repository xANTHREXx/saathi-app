package app.saathi.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import app.saathi.core.stoppedAt

/**
 * What Saathi remembers about its own running, in plain SharedPreferences so the service can write it
 * cheaply and the app can read it after a restart. Kept apart from [Settings]: Home saves a whole
 * Settings copy on every change, which would overwrite a flag written from here.
 *
 * - A heartbeat from the service every minute, with whether a trip was running and whether it was ended on purpose.
 * - Whether she said she changed her phone maker's background settings (Android can't tell us).
 * - The last time the phone stopped Saathi mid-trip, and whether she has seen it.
 */
object RunState {
    private const val FILE = "saathi_run_state"
    private const val BEAT_AT = "beat_at"
    private const val BEAT_ON_TRIP = "beat_on_trip"
    private const val CLEAN_STOP = "clean_stop"
    private const val BACKGROUND_DONE = "background_done"
    private const val STOPPED_AT = "stopped_at"
    private const val STOPPED_SEEN = "stopped_seen"
    private const val CHECK_AT = "check_at"
    private const val CHECK_MIN = "check_min"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The service is alive. [onTrip] is whether a trip is running right now. */
    fun beat(ctx: Context, onTrip: Boolean, nowMs: Long = System.currentTimeMillis()) {
        prefs(ctx).edit().putLong(BEAT_AT, nowMs).putBoolean(BEAT_ON_TRIP, onTrip).putBoolean(CLEAN_STOP, false).apply()
    }

    /** The trip was ended on purpose, so the silence after it is not a kill. */
    fun cleanStop(ctx: Context, nowMs: Long = System.currentTimeMillis()) {
        prefs(ctx).edit().putLong(BEAT_AT, nowMs).putBoolean(BEAT_ON_TRIP, false).putBoolean(CLEAN_STOP, true).apply()
    }

    /**
     * Called once when the app's process starts: if the last pulse says a trip was running and it was
     * never ended, and Android doesn't say she closed the app herself, remember when it went quiet.
     */
    fun checkForStop(ctx: Context, nowMs: Long = System.currentTimeMillis()) {
        val p = prefs(ctx)
        val at = stoppedAt(
            lastHeartbeatMs = p.getLong(BEAT_AT, 0L), wasOnTrip = p.getBoolean(BEAT_ON_TRIP, false),
            cleanStop = p.getBoolean(CLEAN_STOP, false), nowMs = nowMs, exitedByUser = lastExitWasHers(ctx),
        )
        // Whatever the answer, this trip is dealt with: a later restart must not report it again.
        val edit = p.edit().putBoolean(BEAT_ON_TRIP, false)
        if (at != null) edit.putLong(STOPPED_AT, at).putBoolean(STOPPED_SEEN, false)
        edit.apply()
    }

    /** True if Android says she ended the last process (swiped it away, force-stopped it); null when unknown. */
    private fun lastExitWasHers(ctx: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            val last = ctx.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(ctx.packageName, 0, 1).firstOrNull() ?: return@runCatching null
            last.reason == ApplicationExitInfo.REASON_USER_REQUESTED || last.reason == ApplicationExitInfo.REASON_USER_STOPPED
        }.getOrNull()
    }

    /** When the phone stopped Saathi mid-trip, if she hasn't been told yet. */
    fun unseenStop(ctx: Context): Long? = prefs(ctx).let { p ->
        p.getLong(STOPPED_AT, 0L).takeIf { it > 0 && !p.getBoolean(STOPPED_SEEN, false) }
    }

    fun markStopSeen(ctx: Context) { prefs(ctx).edit().putBoolean(STOPPED_SEEN, true).apply() }

    /**
     * The "Check on me" timer, kept here so a service Android restarts still fires it: when it is due
     * and its length in minutes, or null when none is set.
     */
    fun check(ctx: Context): Pair<Long, Int>? = prefs(ctx).let { p ->
        p.getLong(CHECK_AT, 0L).takeIf { it > 0 }?.let { it to p.getInt(CHECK_MIN, 0) }
    }

    fun setCheck(ctx: Context, atMs: Long?, minutes: Int = 0) {
        prefs(ctx).edit().apply { if (atMs == null) remove(CHECK_AT).remove(CHECK_MIN) else putLong(CHECK_AT, atMs).putInt(CHECK_MIN, minutes) }.apply()
    }

    /** She said she turned on her phone maker's background settings. */
    fun backgroundDone(ctx: Context): Boolean = prefs(ctx).getBoolean(BACKGROUND_DONE, false)

    fun setBackgroundDone(ctx: Context, done: Boolean) { prefs(ctx).edit().putBoolean(BACKGROUND_DONE, done).apply() }
}
