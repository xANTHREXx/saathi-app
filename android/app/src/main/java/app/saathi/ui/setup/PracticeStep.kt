package app.saathi.ui.setup

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.saathi.R
import app.saathi.alert.AlertManager
import app.saathi.trip.TripState
import app.saathi.ui.CheckInActivity
import app.saathi.ui.theme.Saathi
import kotlinx.coroutines.delay

/** Seconds between tapping Start and the practice check-in ringing. */
private const val WAIT_S = 5

internal enum class PracticePhase { IDLE, COUNTDOWN, RUNNING, DONE }

/**
 * Where the practice is. The practice itself lives in [AlertManager] and never sends anything;
 * this only watches it so [SetupFlow] can pick the button and the step can pick the picture.
 */
internal class PracticeState(
    val phase: PracticePhase,
    val secondsToStart: Int,
    val start: () -> Unit,
    /** Stops a pending or running practice and goes back to idle. */
    val reset: () -> Unit,
)

@Composable
internal fun rememberPractice(): PracticeState {
    val ctx = LocalContext.current
    val ui by TripState.ui.collectAsState()
    var started by rememberSaveable { mutableStateOf(false) }
    var seen by rememberSaveable { mutableStateOf(false) }
    var done by rememberSaveable { mutableStateOf(false) }
    var opened by rememberSaveable { mutableStateOf(false) }
    var left by remember { mutableIntStateOf(WAIT_S) }
    var resumed by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { resumed = false }

    // The lead-in count. If the practice never rang (another check-in was running), fall back to idle.
    LaunchedEffect(started) {
        if (!started) return@LaunchedEffect
        for (i in WAIT_S downTo 1) { left = i; delay(1000) }
        delay(3000)
        if (!TripState.now.practice && !seen) started = false
    }

    // Answered or ran out: either way the practice is finished.
    LaunchedEffect(started, ui.practice, ui.practiceOver) {
        if (!started || done) return@LaunchedEffect
        if (ui.practice) seen = true
        if (seen && (!ui.practice || ui.practiceOver)) done = true
    }

    // Still looking at this screen when it rings: open the check-in screen. Locked, the notification does it.
    LaunchedEffect(started, ui.practice, ui.checkInEndsAtMs, resumed) {
        if (started && !opened && resumed && ui.practice && ui.checkInEndsAtMs != null) {
            opened = true
            ctx.startActivity(Intent(ctx, CheckInActivity::class.java))
        }
    }

    val phase = when {
        done -> PracticePhase.DONE
        started && ui.practice -> PracticePhase.RUNNING
        started -> PracticePhase.COUNTDOWN
        else -> PracticePhase.IDLE
    }
    return PracticeState(
        phase = phase,
        secondsToStart = left,
        start = {
            AlertManager.init(ctx)
            started = true; seen = false; done = false; opened = false; left = WAIT_S
            AlertManager.startPractice(WAIT_S * 1000L)
        },
        reset = {
            AlertManager.init(ctx)
            AlertManager.endPractice()
            started = false; seen = false; done = false; opened = false
        },
    )
}

/** The last setup step: one safe check-in so the first real one is familiar. */
@Composable
internal fun PracticeStep(practice: PracticeState) {
    val c = Saathi.colors
    Column {
        Text(stringResource(R.string.practice_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.practice_body),
            style = MaterialTheme.typography.bodyLarge, color = c.fg2, modifier = Modifier.padding(top = 12.dp),
        )
        Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PracticeFact(Icons.Rounded.VolumeUp, stringResource(R.string.practice_fact_rings))
            PracticeFact(Icons.Rounded.Lock, stringResource(R.string.practice_fact_lock))
            PracticeFact(Icons.Rounded.Check, stringResource(R.string.practice_fact_tap))
        }
        when (practice.phase) {
            PracticePhase.IDLE -> Unit
            PracticePhase.COUNTDOWN, PracticePhase.RUNNING -> Column(
                Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (practice.phase == PracticePhase.COUNTDOWN) {
                    Text("${practice.secondsToStart}", style = MaterialTheme.typography.displayLarge)
                    Text(stringResource(R.string.practice_lock_now), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
                    Text(stringResource(R.string.practice_will_check), style = MaterialTheme.typography.bodyLarge, color = c.fg2,
                        modifier = Modifier.padding(top = 6.dp))
                } else {
                    Text(stringResource(R.string.checkin_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.practice_tap_okay), style = MaterialTheme.typography.bodyLarge, color = c.fg2,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
            PracticePhase.DONE -> Column(Modifier.padding(top = 32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = c.ok, modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.practice_done), style = MaterialTheme.typography.titleMedium)
                }
                Text(stringResource(R.string.practice_done_body),
                    style = MaterialTheme.typography.bodyMedium, color = c.fg2, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun PracticeFact(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
