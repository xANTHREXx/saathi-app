package app.saathi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.saathi.R
import app.saathi.data.RunState
import app.saathi.trip.openBackgroundSettings
import app.saathi.ui.theme.Saathi
import app.saathi.core.StoppedDay
import app.saathi.core.stoppedDay

/**
 * Home: the phone stopped Saathi in the middle of a trip, once per time it happened. Alum, not madder:
 * nothing was sent, but she needs to know Saathi wasn't watching. Shows nothing when there is nothing to say.
 */
@Composable
internal fun StoppedNotice() {
    val ctx = LocalContext.current
    val c = Saathi.colors
    var stoppedAt by remember { mutableStateOf(RunState.unseenStop(ctx)) }
    val at = stoppedAt ?: return
    fun seen() { RunState.markStopSeen(ctx); stoppedAt = null }

    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(c.alum.copy(alpha = 0.16f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.padding(top = 7.dp).size(8.dp).background(c.alum, CircleShape))
            // Today, "yesterday at 9:42 pm", or "on 21 Oct at 9:42 pm", so an old stop isn't mistaken for a recent one.
            val time = ctx.clockTime(at)
            Text(
                when (stoppedDay(at, System.currentTimeMillis())) {
                    StoppedDay.TODAY -> stringResource(R.string.stopped_text, time)
                    StoppedDay.YESTERDAY -> stringResource(R.string.stopped_text_yesterday, time)
                    StoppedDay.EARLIER -> stringResource(R.string.stopped_text_date, ctx.shortDate(at), time)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { openBackgroundSettings(ctx); seen() }, Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.stopped_fix))
            }
            TextButton(onClick = { seen() }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.stopped_dismiss)) }
        }
    }
}
