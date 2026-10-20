package app.saathi.ui.setup

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.saathi.core.Fix
import app.saathi.core.SavedPlace
import app.saathi.data.Api
import app.saathi.trip.Geocode
import app.saathi.trip.LocationOnce
import app.saathi.ui.theme.Saathi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val OTHER = "Other"
private val DEFAULT_LABELS = listOf("Home", "Work", "College")

private fun labelIcon(label: String): ImageVector = when (label.trim().lowercase()) {
    "home" -> Icons.Rounded.Home
    "work" -> Icons.Rounded.Work
    "college" -> Icons.Rounded.School
    else -> Icons.Rounded.Place
}

/** The label offered next: the first of Home, Work and College not saved yet, else Other. */
private fun nextLabel(places: List<SavedPlace>): String =
    DEFAULT_LABELS.firstOrNull { d -> places.none { it.label.equals(d, ignoreCase = true) } } ?: OTHER

private fun hasLocationPermission(ctx: android.content.Context): Boolean {
    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    return has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)
}

/** Step 3: the heading, then [PlacesEditor]. */
@Composable
internal fun PlacesStep(
    places: List<SavedPlace>,
    onChange: (List<SavedPlace>) -> Unit,
) {
    val c = Saathi.colors
    Column(Modifier.fillMaxWidth()) {
        Text("Places you go often", style = MaterialTheme.typography.headlineMedium)
        Text(
            "With two places, Saathi knows where a trip from one is going and notices detours. You can skip this.",
            style = MaterialTheme.typography.bodyLarge, color = c.fg2, modifier = Modifier.padding(top = 8.dp),
        )
        PlacesEditor(places, onChange, Modifier.padding(top = 24.dp))
    }
}

/**
 * Saved places and the Add a place editor, without any heading. Used by the setup step and by Settings.
 * The editor keeps its own state (label, search text, suggestions);
 * the only thing it shares with its caller is the saved list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlacesEditor(
    places: List<SavedPlace>,
    onChange: (List<SavedPlace>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val c = Saathi.colors
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    // Read through these inside coroutines, so a slow lookup never saves over a newer list.
    val latestPlaces by rememberUpdatedState(places)
    val latestOnChange by rememberUpdatedState(onChange)

    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var otherName by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val selected = picked ?: nextLabel(places)

    var nameProblem by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<Api.PlaceHit>>(emptyList()) }
    var locationAllowed by remember { mutableStateOf(hasLocationPermission(ctx)) }
    var near by remember { mutableStateOf<Fix?>(null) }

    // Her last known position, only once location is allowed. Used to rank suggestions near her.
    LaunchedEffect(locationAllowed) {
        if (locationAllowed) {
            near = withContext(Dispatchers.IO) { runCatching { LocationOnce.get(ctx, 1500) }.getOrNull() }
        }
    }

    // Suggestions while she types, after a short pause.
    LaunchedEffect(query) {
        val text = query.trim()
        if (text.length < 3) {
            hits = emptyList(); searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(300)
        val found = withContext(Dispatchers.IO) { Api.places(text, near).take(5) }
        hits = found
        searching = false
    }

    /** The label to save under, or null (with a message) when "Other" has no name yet. */
    fun labelToSave(): String? {
        if (selected != OTHER) return selected
        val name = otherName.trim()
        if (name.isEmpty()) { nameProblem = "Give this place a name first."; return null }
        return name
    }

    fun save(label: String, lat: Double, lng: Double, address: String, fromLocation: Boolean) {
        val kept = latestPlaces.filterNot { it.label.equals(label, ignoreCase = true) }
        val next = kept + SavedPlace(label, lat, lng, address, fromLocation)
        latestOnChange(next)
        query = ""; otherName = ""; problem = null; nameProblem = null
        picked = null
        keyboard?.hide()
    }

    fun useCurrentLocation() {
        val label = labelToSave() ?: return
        problem = null
        locating = true
        scope.launch {
            val fix = runCatching { LocationOnce.get(ctx) }.getOrNull()
            if (fix == null) {
                locating = false
                problem = "Couldn't find where you are. Try again outside, or search instead."
                return@launch
            }
            val line = withContext(Dispatchers.IO) {
                runCatching { Geocode.lookup(ctx, fix.lat, fix.lng)?.let { Geocode.placeLine(it) } }.getOrNull()
            }
            locating = false
            save(label, fix.lat, fix.lng, line ?: "Near your current location", fromLocation = true)
        }
    }

    // After she allows location from the row below, carry straight on.
    var locateAfterGrant by remember { mutableStateOf(false) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) {
            locationAllowed = true
            locateAfterGrant = true
        } else {
            problem = "Allow location to use where you are, or search instead."
        }
    }
    LaunchedEffect(locateAfterGrant) {
        if (locateAfterGrant) { locateAfterGrant = false; useCurrentLocation() }
    }

    fun onCurrentLocationTap() {
        if (locating) return
        if (labelToSave() == null) return
        problem = null
        if (hasLocationPermission(ctx)) {
            locationAllowed = true
            useCurrentLocation()
        } else {
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    Column(modifier.fillMaxWidth()) {
        if (places.isNotEmpty()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                places.forEachIndexed { i, p ->
                    PlaceRow(p, onRemove = { onChange(places.filterIndexed { j, _ -> j != i }) })
                }
            }
        }

        Text(
            "Add a place", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = if (places.isNotEmpty()) 24.dp else 0.dp),
        )

        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (DEFAULT_LABELS + OTHER).forEach { l ->
                FilterChip(
                    selected = selected == l,
                    onClick = { picked = l; nameProblem = null },
                    label = { Text(l) },
                    leadingIcon = { Icon(labelIcon(l), null, Modifier.size(18.dp)) },
                )
            }
        }

        if (selected == OTHER) {
            OutlinedTextField(
                otherName, { otherName = it; nameProblem = null },
                Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Name this place") },
                placeholder = { Text("Gym") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                supportingText = nameProblem?.let { msg -> { ProblemText(msg) } },
            )
        }

        OutlinedTextField(
            query, { query = it; problem = null },
            Modifier.fillMaxWidth().padding(top = 12.dp),
            placeholder = { Text("Search an address or place") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            supportingText = {
                Text(
                    "Addresses you type are looked up with OpenRouteService.",
                    style = MaterialTheme.typography.bodySmall, color = c.fg2,
                )
            },
        )

        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            SuggestionRow(onClick = { onCurrentLocationTap() }) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (locating) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.MyLocation, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Text("Use my current location", style = MaterialTheme.typography.bodyLarge)
            }

            if (query.trim().length >= 3) {
                hits.forEach { hit ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
                    SuggestionRow(onClick = {
                        val label = labelToSave() ?: return@SuggestionRow
                        val address = listOf(hit.label, hit.sub).filter { it.isNotBlank() }.joinToString(", ")
                        save(label, hit.lat, hit.lng, address, fromLocation = false)
                    }) {
                        Icon(Icons.Rounded.Place, null, Modifier.size(24.dp), tint = c.fg2)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(hit.label, style = MaterialTheme.typography.bodyLarge)
                            if (hit.sub.isNotBlank()) {
                                Text(hit.sub, style = MaterialTheme.typography.bodySmall, color = c.fg2)
                            }
                        }
                    }
                }
                if (hits.isEmpty()) {
                    Text(
                        if (searching) "Searching…" else "No matches. Try a landmark or area name.",
                        style = MaterialTheme.typography.bodySmall, color = c.fg2,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                    )
                }
            }
        }
        problem?.let { ProblemText(it, Modifier.padding(top = 8.dp)) }
    }
}

/** One tappable line under the search field: at least 56dp, grows with the font. */
@Composable
private fun SuggestionRow(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun PlaceRow(place: SavedPlace, onRemove: () -> Unit) {
    val c = Saathi.colors
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(labelIcon(place.label), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(place.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    place.address ?: "Saved place",
                    style = MaterialTheme.typography.bodySmall, color = c.fg2,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (place.fromLocation) {
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.MyLocation, null, Modifier.size(14.dp), tint = c.fg2)
                        Spacer(Modifier.size(4.dp))
                        Text("From your location", style = MaterialTheme.typography.labelSmall, color = c.fg2)
                    }
                }
            }
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove ${place.label}") }
        }
    }
}
