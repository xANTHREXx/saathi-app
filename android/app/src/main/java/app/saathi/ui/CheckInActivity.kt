package app.saathi.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.saathi.R
import androidx.compose.ui.unit.sp
import app.saathi.alert.AlertManager
import app.saathi.alert.Notifier
import app.saathi.trip.TripState
import app.saathi.ui.theme.Saathi
import app.saathi.ui.theme.SaathiTheme
import app.saathi.ui.theme.Star
import kotlinx.coroutines.delay

/** Full screen over the lock screen: the check-in, and after an alert, who was told. Always night. */
class CheckInActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        AlertManager.init(this)
        setContent { SaathiTheme(dark = true) { CheckIn(onOk = { AlertManager.imOkay(); finish() }, onClose = { finish() }, onDone = { AlertManager.endPractice(); finish() }) } }
    }
}

private const val WINDOW_S = 10f

@Composable
private fun CheckIn(onOk: () -> Unit, onClose: () -> Unit, onDone: () -> Unit) {
    val s by TripState.ui.collectAsState()
    val c = Saathi.colors
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(100) } }
    val ends = s.checkInEndsAtMs

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        when {
            s.practice && s.practiceOver && ends == null -> {
                Spacer(Modifier.weight(1f))
                Column(Modifier.padding(horizontal = 6.dp)) {
                    Text(stringResource(R.string.checkin_practice_over), style = MaterialTheme.typography.displaySmall)
                    Text(stringResource(R.string.checkin_practice_over_body),
                        style = MaterialTheme.typography.bodyLarge, color = c.fg2, modifier = Modifier.padding(top = 12.dp))
                    Text(stringResource(R.string.checkin_nothing_sent), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                }
                Spacer(Modifier.weight(1f))
                Button(onDone, Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(20.dp)) {
                    Text(stringResource(R.string.action_done), style = MaterialTheme.typography.titleMedium)
                }
            }
            ends != null -> {
                val leftMs = (ends - now).coerceAtLeast(0)
                val practice = s.practice
                val reason = s.events.lastOrNull()?.let { ctx.eventText(it).capitalised() }
                Column(Modifier.padding(horizontal = 6.dp)) {
                    if (practice) Pill(Icons.Rounded.School, stringResource(R.string.checkin_pill_practice), c.alum)
                    else reason?.let { Pill(Icons.Rounded.PauseCircle, it, c.alum) }
                    Text(stringResource(R.string.checkin_title), style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(top = 18.dp))
                    Text(stringResource(if (practice) R.string.checkin_body_practice else R.string.checkin_body),
                        style = MaterialTheme.typography.bodyLarge, color = c.fg2, modifier = Modifier.padding(top = 12.dp))
                    StalledThread(Modifier.padding(top = 24.dp))
                }
                // The countdown owns the middle of the screen: this is the one moment that asks loudly.
                Spacer(Modifier.weight(1f))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Countdown(leftMs / 1000f / WINDOW_S, ((leftMs + 999) / 1000).toInt())
                    Text(stringResource(if (practice) R.string.checkin_seconds_practice else R.string.checkin_seconds), style = MaterialTheme.typography.bodyLarge, color = c.fg2,
                        modifier = Modifier.padding(top = 14.dp))
                }
                Spacer(Modifier.weight(1f))
                OkayPlate(height = 108, onClick = onOk)
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                    Fact(Icons.Rounded.VolumeUp, stringResource(R.string.checkin_fact_alarm)); Fact(Icons.Rounded.Vibration, stringResource(R.string.checkin_fact_vibrating))
                    Fact(Icons.Rounded.Lock, stringResource(R.string.checkin_fact_lock))
                }
            }
            s.alertActive -> {
                Column(Modifier.padding(horizontal = 6.dp)) {
                    Box(Modifier.size(56.dp).background(c.help, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Text(stringResource(if (s.told.isEmpty()) R.string.told_telling else R.string.raised_told),
                        style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 20.dp))
                    if (s.told.isNotEmpty()) Text(s.told.joinToString(", "), style = MaterialTheme.typography.headlineSmall,
                        color = c.fg2, modifier = Modifier.padding(top = 6.dp))
                    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Fact(Icons.Rounded.Check, stringResource(R.string.told_link), big = true)
                        if (s.recording) Fact(Icons.Rounded.Mic, stringResource(R.string.checkin_recording), big = true)
                        if (s.smsFailed.isNotEmpty()) Text(stringResource(R.string.told_failed_retrying, ctx.namesText(s.smsFailed)),
                            style = MaterialTheme.typography.bodyMedium, color = c.onHelpContainer)
                        Text(stringResource(R.string.no_112_full), style = MaterialTheme.typography.labelMedium, color = c.alum)
                    }
                }
                Spacer(Modifier.weight(1f))
                OkayPlate(height = 96, onClick = onOk)
                // Tonal, beside Close: the okay plate stays the one reversed plate on this screen.
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = { runCatching { ctx.startActivity(Notifier.dial112()) } },
                        Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(Icons.Rounded.Call, null, Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.call_112))
                    }
                    FilledTonalButton(onClose, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(20.dp)) { Text(stringResource(R.string.action_close)) }
                }
            }
            else -> {
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.checkin_none), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 6.dp))
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClose, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(20.dp)) { Text(stringResource(R.string.action_close)) }
            }
        }
    }
}

/** The trip thread, stopped: stamped stars, then the newest ones fading in alum. */
@Composable
private fun StalledThread(modifier: Modifier = Modifier) {
    val c = Saathi.colors
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(10) { Star(c.starOn, 12.dp) }
            Star(c.alum, 12.dp); Star(c.alum.copy(alpha = 0.55f), 12.dp); Star(c.alum.copy(alpha = 0.25f), 12.dp)
        }
        Text(stringResource(R.string.checkin_stalled), style = MaterialTheme.typography.bodySmall, color = c.fg2, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Countdown(fraction: Float, seconds: Int) {
    val c = Saathi.colors
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 12.dp.toPx()
            drawArc(track, 0f, 360f, false, style = Stroke(w))
            drawArc(c.alum, -90f, 360f * fraction.coerceIn(0f, 1f), false, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("$seconds", style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp, lineHeight = 76.sp))
    }
}

@Composable
private fun Pill(icon: ImageVector, text: String, color: Color) {
    Row(
        Modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String, big: Boolean = false) {
    val c = Saathi.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (big) 10.dp else 4.dp)) {
        Icon(icon, null, tint = if (big) MaterialTheme.colorScheme.primary else c.fg3, modifier = Modifier.size(if (big) 20.dp else 16.dp))
        Text(text, style = if (big) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelSmall, color = if (big) MaterialTheme.colorScheme.onBackground else c.fg2)
    }
}
