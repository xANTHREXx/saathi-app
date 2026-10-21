package app.saathi.alert

import android.app.Notification
import app.saathi.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.saathi.core.TripEvent
import app.saathi.data.Store
import app.saathi.trip.TripService
import app.saathi.trip.UiState
import app.saathi.ui.CheckInActivity
import app.saathi.ui.MainActivity
import app.saathi.ui.SpeakForMeActivity
import app.saathi.ui.eventText
import app.saathi.ui.quantity
import app.saathi.ui.watchingText

object Notifier {
    const val GUARD_ID = 1
    const val CHECKIN_ID = 2
    const val ALERT_ID = 3
    private const val GUARD = "guard"
    private const val CHECKIN = "checkin"
    private const val ALERT = "alert"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(GUARD, ctx.getString(R.string.notif_channel_guard), NotificationManager.IMPORTANCE_LOW).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        nm.createNotificationChannel(NotificationChannel(CHECKIN, ctx.getString(R.string.notif_channel_checkin), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        nm.createNotificationChannel(NotificationChannel(ALERT, ctx.getString(R.string.notif_channel_alert), NotificationManager.IMPORTANCE_HIGH).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        nm.createNotificationChannel(NotificationChannel("demo_ride", "Demo ride-app notifications", NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Demo only: posts a notification worded like a ride app's, which the ride watcher picks up. */
    fun demoRide(ctx: Context, title: String, text: String) {
        val n = Notification.Builder(ctx, "demo_ride")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(title).setContentText(text)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(60 + (title.hashCode() and 0xF), n)
    }

    /** The persistent notification: usable from the lock screen. */
    fun guard(ctx: Context, s: UiState): Notification {
        val title = when {
            s.alertActive -> ctx.getString(R.string.notif_guard_alert, s.told.joinToString(", "))
            s.onTrip -> ctx.watchingText(s.mode, s.app) + (s.plate?.let { ", $it" } ?: "")
            else -> ctx.getString(R.string.notif_guard_ready)
        }
        // The power button is only promised when it is on.
        val powerOn = !s.onTrip && Store.settings(ctx).powerPresses > 0
        val b = Notification.Builder(ctx, GUARD)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(title)
            .setContentText(ctx.getString(if (powerOn) R.string.guard_text_power else R.string.guard_text_lock))
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(activity(ctx, MainActivity::class.java, 10))
            .addAction(action(ctx, ctx.getString(R.string.action_help), service(ctx, TripService.ACTION_HELP, 11)))
            .addAction(action(ctx, ctx.getString(R.string.tile_speak), activity(ctx, SpeakForMeActivity::class.java, 12)))
        if (s.onTrip) b.addAction(action(ctx, ctx.getString(R.string.notif_action_stop), service(ctx, TripService.ACTION_STOP_TRIP, 13)))
        return b.build()
    }

    /** [event] is what the watcher noticed; null for a practice. */
    fun showCheckIn(ctx: Context, event: TripEvent?, practice: Boolean = false) {
        val full = activity(ctx, CheckInActivity::class.java, 20)
        val n = Notification.Builder(ctx, CHECKIN)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(ctx.getString(if (practice) R.string.notif_checkin_practice_title else R.string.checkin_title))
            .setContentText(
                if (practice) ctx.getString(R.string.notif_checkin_practice_text)
                else ctx.quantity(R.plurals.notif_checkin_text, AlertManager.CHECKIN_S.toInt(),
                    event?.let { ctx.eventText(it) }.orEmpty(), AlertManager.CHECKIN_S.toInt()),
            )
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(action(ctx, ctx.getString(R.string.im_okay), service(ctx, TripService.ACTION_OK, 21)))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(CHECKIN_ID, n)
    }

    fun showAlerted(ctx: Context, told: List<String>, failed: List<String>) {
        val text = buildList {
            add(if (told.isEmpty()) ctx.getString(R.string.notif_alert_none) else ctx.getString(R.string.notif_alert_told, told.joinToString(", ")))
            if (failed.isNotEmpty()) add(ctx.getString(R.string.notif_alert_failed, failed.joinToString(", ")))
            add(ctx.getString(R.string.notif_alert_no_112))
        }.joinToString(" ")
        val n = Notification.Builder(ctx, ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(ctx.getString(if (told.isEmpty()) R.string.alert_none_reached else R.string.raised_told))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(activity(ctx, CheckInActivity::class.java, 30))
            .addAction(action(ctx, ctx.getString(R.string.im_okay), service(ctx, TripService.ACTION_OK, 31)))
            .addAction(action(ctx, ctx.getString(R.string.call_112), PendingIntent.getActivity(ctx, 32, dial112(),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ALERT_ID, n)
    }

    /** Opens the dialer with 112 filled in; she presses call. Apps can't place emergency calls themselves. */
    fun dial112(): Intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun cancel(ctx: Context, id: Int) = ctx.getSystemService(NotificationManager::class.java).cancel(id)

    private fun action(ctx: Context, title: String, pi: PendingIntent) =
        Notification.Action.Builder(null, title, pi).build()

    private fun activity(ctx: Context, cls: Class<*>, code: Int): PendingIntent =
        PendingIntent.getActivity(ctx, code, Intent(ctx, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun service(ctx: Context, act: String, code: Int): PendingIntent =
        PendingIntent.getForegroundService(ctx, code, Intent(ctx, TripService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
