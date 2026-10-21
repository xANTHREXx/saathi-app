package app.saathi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.saathi.R
import app.saathi.data.Store
import app.saathi.trip.TripService
import app.saathi.trip.UiState
import app.saathi.ui.theme.Saathi

/** On a trip: where Saathi thinks she's going, and a way to change it. */
@Composable
fun DestinationRow(s: UiState) {
    val ctx = LocalContext.current
    val c = Saathi.colors
    val places = remember { Store.settings(ctx).places }
    var open by remember { mutableStateOf(false) }
    val dest = s.destLabel?.let { ctx.placeLabel(it) }
    if (places.isEmpty() && s.destLabel == null) return
    fun pick(label: String?) {
        open = false
        ctx.startForegroundService(TripService.intent(ctx, TripService.ACTION_SET_DEST).putExtra(TripService.EXTRA_DEST, label))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(if (s.route.isNotEmpty()) c.ok else c.fg3, CircleShape))
        Text(
            when {
                dest == null -> stringResource(R.string.trip_dest_unsure)
                s.route.isEmpty() && s.noRoute -> stringResource(R.string.trip_dest_no_route, dest)
                s.route.isEmpty() -> stringResource(R.string.trip_dest_finding, dest)
                else -> stringResource(R.string.trip_dest_watching, dest)
            },
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.fg2,
        )
        Box {
            TextButton(onClick = { open = true }) { Text(stringResource(if (s.destLabel == null) R.string.trip_dest_pick else R.string.trip_dest_change)) }
            DropdownMenu(open, { open = false }) {
                places.forEach { p -> DropdownMenuItem(text = { Text(ctx.placeLabel(p.label)) }, onClick = { pick(p.label) }) }
                DropdownMenuItem(text = { Text(stringResource(R.string.trip_dest_not_sure)) }, onClick = { pick(null) })
            }
        }
    }
    s.vehicleNote?.let { Text(ctx.vehicleNoteText(it), style = MaterialTheme.typography.bodySmall, color = c.fg2, modifier = Modifier.padding(start = 16.dp)) }
}
