package app.saathi.trigger

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import app.saathi.R
import app.saathi.alert.Alarm
import app.saathi.alert.AlertManager
import app.saathi.alert.Notifier
import app.saathi.core.HelpTileAction
import app.saathi.core.Reason
import app.saathi.core.helpTileAction
import app.saathi.core.helpTileLook
import app.saathi.core.isSetUp
import app.saathi.data.Store
import app.saathi.trip.TripService
import app.saathi.trip.TripState
import app.saathi.ui.MainActivity
import app.saathi.ui.tileSubtitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * "Saathi help" in Quick Settings: another way to ask for help, for phones where the power button
 * opens the camera. Works from the lock screen, so it never calls unlockAndRun. A tap starts the
 * same 5-second "Don't text" window as Speak for me; tapping again inside it stops it.
 */
class HelpTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        refresh()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
            s.launch {
                TripState.ui.map { (it.pendingAlertEndsAtMs != null) to it.alertActive }.distinctUntilChanged().collect { refresh() }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        AlertManager.init(applicationContext)
        val s = Store.settings(this)
        val setUp = isSetUp(s.onboarded, s.contacts)
        val ui = TripState.now
        when (helpTileAction(setUp, ui.pendingAlertEndsAtMs != null, ui.alertActive)) {
            HelpTileAction.OPEN_APP -> openApp()
            HelpTileAction.CANCEL_PENDING -> AlertManager.cancelPendingAlert()
            HelpTileAction.REFRESH_ONLY -> Unit // a sent alert is taken back with I'm okay in the app, never from here
            HelpTileAction.RAISE -> {
                // The alert path can run with only this process alive, except two things TripService normally
                // sets up: the notification channels, and a reason for the process to stay put during the 5 s.
                Notifier.channels(applicationContext)
                runCatching { startForegroundService(TripService.intent(this, TripService.ACTION_GUARD)) }
                    .onFailure { Log.w("Saathi", "tile: guard service not started: ${it.message}") }
                AlertManager.raiseAfter(Reason.QS_TILE, WINDOW_MS)
                Alarm(applicationContext).buzzOnce()
            }
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val s = Store.settings(this)
        val ui = TripState.now
        val look = helpTileLook(isSetUp(s.onboarded, s.contacts), s.contacts, ui.pendingAlertEndsAtMs != null, ui.alertActive)
        tile.label = getString(R.string.help_tile_label)
        tile.subtitle = tileSubtitle(look.subtitle, look.name)
        tile.state = if (look.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        const val WINDOW_MS = 5_000L
    }
}
