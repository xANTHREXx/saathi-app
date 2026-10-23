package app.saathi.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.saathi.BuildConfig
import app.saathi.R
import app.saathi.data.Settings
import app.saathi.trip.TripService
import app.saathi.trip.UiState
import app.saathi.ui.theme.Saathi

/** Timer lengths she can pick. Debug builds add 1 minute, for testing. */
private val CHECK_CHOICES = (if (BuildConfig.DEBUG) listOf(1) else emptyList()) + listOf(10, 20, 30, 45)

private fun checkIntent(ctx: Context, action: String) = TripService.intent(ctx, action)

/**
 * "Check on me": for a walk or a wait no ride app knows about. One tap on a length starts it; when it runs
 * out, the usual "Are you okay?" check-in, and silence after that alerts. Tonal, not alum: nothing is wrong yet.
 * Hidden during a check-in or alert, when the screen has something more important to say.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun CheckOnMe(s: UiState, settings: Settings, onChange: (Settings) -> Unit) {
    val ctx = LocalContext.current
    val c = Saathi.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val at = s.checkAtMs
    // The lengths appear at the bottom of Home's scroll, under the fade above the pinned help row: scroll them into view
    // once they are laid out.
    val choices = remember { BringIntoViewRequester() }
    LaunchedEffect(open) {
        if (open) {
            withFrameNanos { }
            choices.bringIntoView()
        }
    }

    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow).border(1.dp, c.hair, MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (at != null) {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, Modifier.size(20.dp), tint = c.fg2)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.check_running, ctx.clockTime(at)), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { ctx.startForegroundService(checkIntent(ctx, TripService.ACTION_CHECK_ADD)) },
                    Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.check_add)) }
                TextButton(
                    onClick = { ctx.startForegroundService(checkIntent(ctx, TripService.ACTION_CHECK_CANCEL)) },
                    Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.check_cancel)) }
            }
        } else {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.check_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.check_sub), style = MaterialTheme.typography.bodySmall, color = c.fg2)
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { open = !open }, Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Timer, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.check_set))
                }
            }
            if (open) Column(Modifier.bringIntoViewRequester(choices)) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CHECK_CHOICES.forEach { m ->
                        FilterChip(
                            selected = settings.checkMinutes == m,
                            onClick = {
                                ctx.startForegroundService(checkIntent(ctx, TripService.ACTION_CHECK_START).putExtra(TripService.EXTRA_MINUTES, m))
                                if (settings.checkMinutes != m) onChange(settings.copy(checkMinutes = m))
                                open = false
                            },
                            label = { Text(pluralStringResource(R.plurals.check_minutes, m, m)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp)) // so the chips clear the fade above the pinned help row
            }
        }
    }
}
