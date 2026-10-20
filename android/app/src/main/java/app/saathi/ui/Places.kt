package app.saathi.ui

import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.saathi.core.SavedPlace
import app.saathi.data.Store
import app.saathi.trip.LocationOnce
import app.saathi.trip.TripService
import app.saathi.trip.UiState
import app.saathi.ui.theme.Saathi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Places she goes often, set once. With two of them, Saathi knows where a trip from one is headed
 * and can notice a detour or a late arrival without her typing anything.
 */
@Composable
fun SavedPlacesEditor(places: SnapshotStateList<SavedPlace>) {
    val ctx = LocalContext.current
    val c = Saathi.colors
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf("Home") }
    var address by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun add(lat: Double, lng: Double) {
        val l = label.trim().ifBlank { "Place ${places.size + 1}" }
        places.removeAll { it.label.equals(l, ignoreCase = true) }
        places += SavedPlace(l, lat, lng)
        address = ""; error = null
        label = listOf("Home", "Work", "College").firstOrNull { d -> places.none { it.label == d } } ?: ""
    }

    places.forEachIndexed { i, p ->
        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.label, style = MaterialTheme.typography.titleSmall)
                Text("%.4f, %.4f".format(Locale.ENGLISH, p.lat, p.lng), style = MaterialTheme.typography.bodySmall, color = c.fg2)
            }
            OutlinedButton(onClick = { places.removeAt(i) }) { Text("Remove") }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Home", "Work", "College").forEach { l -> FilterChip(selected = label == l, onClick = { label = l }, label = { Text(l) }) }
    }
    OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name of the place") })
    FilledTonalButton(enabled = !busy, onClick = {
        busy = true
        scope.launch {
            val f = LocationOnce.get(ctx)
            busy = false
            if (f == null) error = "Couldn't get your location. Allow location below, or type the address." else add(f.lat, f.lng)
        }
    }) { Text("Use where I am now") }
    OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("…or type the address") })
    OutlinedButton(enabled = !busy && address.isNotBlank(), onClick = {
        busy = true
        scope.launch {
            val hit = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(ctx, Locale.ENGLISH).getFromLocationName(address, 1)?.firstOrNull()
                }.getOrNull()
            }
            busy = false
            if (hit == null) error = "Couldn't find that address. Try adding the area and city." else add(hit.latitude, hit.longitude)
        }
    }) { Text("Find address") }
    error?.let { Text(it, color = c.help, style = MaterialTheme.typography.bodyMedium) }
}

/** On a trip: where Saathi thinks she's going, and a way to change it. */
@Composable
fun DestinationRow(s: UiState) {
    val ctx = LocalContext.current
    val c = Saathi.colors
    val places = remember { Store.settings(ctx).places }
    var open by remember { mutableStateOf(false) }
    if (places.isEmpty() && s.destLabel == null) return
    fun pick(label: String?) {
        open = false
        ctx.startForegroundService(TripService.intent(ctx, TripService.ACTION_SET_DEST).putExtra(TripService.EXTRA_DEST, label))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(if (s.route.isNotEmpty()) c.ok else c.fg3, CircleShape))
        Text(
            when {
                s.destLabel == null -> "Not sure where you're going: watching for long stops"
                s.route.isEmpty() && s.noRoute -> "Going to ${s.destLabel} · no route found, watching for long stops"
                s.route.isEmpty() -> "Going to ${s.destLabel} · finding the route"
                else -> "Going to ${s.destLabel} · watching for detours"
            },
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.fg2,
        )
        Box {
            TextButton(onClick = { open = true }) { Text(if (s.destLabel == null) "Pick" else "Change") }
            DropdownMenu(open, { open = false }) {
                places.forEach { p -> DropdownMenuItem(text = { Text(p.label) }, onClick = { pick(p.label) }) }
                DropdownMenuItem(text = { Text("Not sure") }, onClick = { pick(null) })
            }
        }
    }
    s.vehicleNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.fg2, modifier = Modifier.padding(start = 16.dp)) }
}
