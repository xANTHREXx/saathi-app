package app.saathi.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.saathi.R
import app.saathi.alert.Alarm
import app.saathi.alert.AlertManager
import app.saathi.alert.Notifier
import app.saathi.core.Reason
import app.saathi.core.isSetUp
import app.saathi.core.recipients
import app.saathi.data.Store
import app.saathi.trip.TripService
import app.saathi.trip.TripState
import app.saathi.ui.theme.Saathi
import app.saathi.ui.theme.SaathiTheme

/**
 * "Saathi Help": its own launcher entry, so a phone's back-tap gesture (Pixel Quick Tap, Samsung RegiStar),
 * the long-press shortcut on Saathi's icon, or a home-screen icon can ask for help. Opening it is the gesture:
 * the same 5 s undo as the help tile, then the alert. Works over the lock screen.
 */
class HelpNowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        AlertManager.init(this)
        val s = Store.settings(this)
        if (!isSetUp(s.onboarded, s.contacts)) {
            // Nobody to text yet: finishing setup is the only useful thing this can open.
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        Notifier.channels(this)
        runCatching { startForegroundService(TripService.intent(this, TripService.ACTION_GUARD)) }
        // Not on a recreate (rotation), or "Don't text" would re-arm it; and never a second alert over one already going.
        if (savedInstanceState == null) raise()
        val who = namesText(recipients(s.contacts, TripState.city()).map { it.name })
        setContent { SaathiTheme(dark = true) { HelpNow(who, onClose = { finish() }) } }
    }

    /** Back-tapped again while this screen is still open: that is asking again. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        raise()
    }

    private fun raise() {
        val ui = TripState.now
        if (ui.alertActive || ui.pendingAlertEndsAtMs != null) return
        AlertManager.raiseAfter(Reason.SHORTCUT, UNDO_WINDOW_MS)
        Alarm(this).buzzOnce()
    }
}

@Composable
private fun HelpNow(who: String, onClose: () -> Unit) {
    val s by TripState.ui.collectAsState()
    val ctx = LocalContext.current
    var notSent by rememberSaveable { mutableStateOf(false) }
    val pendingEnds = s.pendingAlertEndsAtMs
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(stringResource(R.string.help_now_title), style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp))
        when {
            s.alertActive -> HelpToldBanner(s.told)
            pendingEnds != null -> PendingHelpBanner(who, pendingEnds, onDontText = { if (AlertManager.cancelPendingAlert()) notSent = true })
            notSent -> Text(stringResource(R.string.help_now_not_sent), style = MaterialTheme.typography.bodyLarge, color = Saathi.colors.fg2)
            else -> HelpToldBanner(s.told) // the instant between the window closing and the alert starting
        }
        Spacer(Modifier.weight(1f))
        if (s.alertActive) {
            OkayPlate(height = 96, onClick = rememberConfirmOkay(onSent = onClose))
            UnlockFirstNote()
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.alertActive) FilledTonalButton(
                onClick = { runCatching { ctx.startActivity(Notifier.dial112()) } },
                Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(20.dp),
            ) { Text(stringResource(R.string.call_112)) }
            FilledTonalButton(onClose, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(20.dp)) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}
