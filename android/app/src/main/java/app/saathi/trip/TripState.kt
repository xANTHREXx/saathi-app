package app.saathi.trip

import app.saathi.core.Fix
import app.saathi.core.Mode
import app.saathi.core.TripEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class Place(val city: String? = null, val state: String? = null, val countryCode: String? = null, val label: String? = null)

data class UiState(
    val guarding: Boolean = false,
    val onTrip: Boolean = false,
    val mode: Mode? = null,
    val app: String? = null,
    val plate: String? = null,
    val driver: String? = null,
    val events: List<TripEvent> = emptyList(),
    val lastFix: Fix? = null,
    val lastFixAtMs: Long = 0,
    val place: Place = Place(),
    val cityOverride: String? = null,
    val checkInEndsAtMs: Long? = null,
    val alertActive: Boolean = false,
    /** Speak for me's "Don't text" window: when it closes and the alert goes out. Null when none is pending. */
    val pendingAlertEndsAtMs: Long? = null,
    val told: List<String> = emptyList(),
    val smsFailed: List<String> = emptyList(),
    val recording: Boolean = false,
    val plateCheck: String? = null,
    /** When the current trip started, for the watched-minutes thread. */
    val startedAtMs: Long = 0,
    /** This trip's fixes, newest last, capped, for the map line. */
    val trail: List<Fix> = emptyList(),
    /** Saved place the trip started at, and where it's headed (guessed or picked). */
    val fromLabel: String? = null,
    val destLabel: String? = null,
    /** The expected route the watcher checks against; empty when unknown. */
    val route: List<Fix> = emptyList(),
    /** True once a route was asked for and none came (offline, no key, bus or train). */
    val noRoute: Boolean = false,
    /** About this vehicle, from other Saathi users: shown only to her, as counts. */
    val vehicleNote: String? = null,
    /** A practice check-in is running or just ended. It never sends anything. */
    val practice: Boolean = false,
    /** The practice ran out without an answer. */
    val practiceOver: Boolean = false,
)

/** One shared state for the service, the screens, the widget. */
object TripState {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui
    fun update(f: (UiState) -> UiState) = _ui.update(f)
    val now get() = _ui.value

    fun city(): String? = now.cityOverride ?: now.place.city
}
