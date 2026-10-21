package app.saathi.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import app.saathi.R
import app.saathi.core.AreaNote
import app.saathi.core.EventKind
import app.saathi.core.Mode
import app.saathi.core.ProblemKind
import app.saathi.core.SetupProblem
import app.saathi.core.TileSubtitle
import app.saathi.core.TripEvent
import app.saathi.core.VehicleNote
import app.saathi.core.cleanNames
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Words and dates the screens, the notifications and the tile build from data. They read the app's current
// language, so the same code speaks English or Hindi. Pure logic stays in core/ and returns data.

/** The language the app is showing right now. */
internal val Context.appLocale: Locale get() = resources.configuration.locales[0]

internal fun Context.quantity(@PluralsRes id: Int, n: Int, vararg args: Any): String =
    resources.getQuantityString(id, n, *args)

/** "Amma" / "Amma and Riya" / "Amma, Riya and Chinu"; "your contacts" when there is nobody to name. */
internal fun Context.namesText(names: List<String>): String {
    val n = cleanNames(names)
    return when (n.size) {
        0 -> getString(R.string.names_none)
        1 -> n[0]
        else -> getString(R.string.names_pair, n.dropLast(1).joinToString(", "), n.last())
    }
}

/** "Amma was told" / "Amma and Riya were told". */
internal fun Context.toldText(told: List<String>): String {
    val n = cleanNames(told)
    return quantity(R.plurals.told_names, n.size.coerceAtLeast(1), namesText(n))
}

@StringRes
internal fun modeNameRes(mode: Mode): Int = when (mode) {
    Mode.AUTO -> R.string.mode_auto
    Mode.CAB -> R.string.mode_cab
    Mode.BIKE -> R.string.mode_bike
    Mode.BUS -> R.string.mode_bus
    Mode.TRAIN -> R.string.mode_train
    Mode.WALK -> R.string.mode_walk
}

/** "Watching your auto" / "Watching your Uber auto", for the trip header and the notification. */
internal fun Context.watchingText(mode: Mode?, app: String?): String {
    if (app == null) return getString(when (mode) {
        Mode.AUTO -> R.string.trip_watching_auto
        Mode.CAB -> R.string.trip_watching_cab
        Mode.BIKE -> R.string.trip_watching_bike
        Mode.BUS -> R.string.trip_watching_bus
        Mode.TRAIN -> R.string.trip_watching_train
        Mode.WALK -> R.string.trip_watching_walk
        null -> R.string.trip_watching_trip
    })
    return getString(when (mode) {
        Mode.AUTO -> R.string.trip_watching_auto_app
        Mode.CAB -> R.string.trip_watching_cab_app
        Mode.BIKE -> R.string.trip_watching_bike_app
        Mode.BUS -> R.string.trip_watching_bus_app
        Mode.TRAIN -> R.string.trip_watching_train_app
        Mode.WALK -> R.string.trip_watching_walk_app
        null -> R.string.trip_watching_trip_app
    }, app)
}

/**
 * What the watcher noticed, in her language, starting lowercase ("stopped 5 min"). Falls back to the stored English
 * [TripEvent.detail] for an event with no number (a record from before the number was kept).
 */
internal fun Context.eventText(e: TripEvent): String = when {
    e.kind == EventKind.SHAKE -> getString(R.string.event_shake) // the one event with no number
    e.value <= 0 -> e.detail
    e.kind == EventKind.LONG_STOP -> quantity(R.plurals.event_long_stop, e.value, e.value)
    e.kind == EventKind.OFF_ROUTE -> getString(R.string.event_off_route, e.value)
    e.kind == EventKind.LATE -> quantity(R.plurals.event_late, e.value, e.value)
    e.kind == EventKind.TIMER -> quantity(R.plurals.event_timer, e.value, e.value)
    else -> e.detail
}

internal fun String.capitalised(): String = replaceFirstChar { it.uppercase() }

internal fun Context.vehicleNoteText(n: VehicleNote): String =
    quantity(R.plurals.vehicle_rides, n.rides, n.rides) +
        (if (n.noneRaised) " · " + getString(R.string.vehicle_none_raised) else "") +
        (if (n.sample) " " + getString(R.string.vehicle_sample) else "")

/** The label kept with a sealed file ("location log", "audio rec-….m4a"), in her language. */
internal fun Context.evidenceName(name: String): String = when {
    name == "location log" -> getString(R.string.trips_evidence_location)
    name.startsWith("audio ") -> getString(R.string.trips_evidence_audio, name.removePrefix("audio "))
    else -> name
}

internal fun Context.areaNoteText(n: AreaNote): String =
    quantity(if (n.nightOnly) R.plurals.map_note_night else R.plurals.map_note, n.count, n.count, n.radiusM)

internal fun Context.problemText(p: SetupProblem): String = when (p.kind) {
    ProblemKind.PHONE_MISSING -> getString(R.string.problem_phone_missing, p.name)
    ProblemKind.PHONE_INCOMPLETE -> getString(R.string.problem_phone_incomplete)
    ProblemKind.NAME_MISSING -> getString(R.string.problem_name_missing)
    ProblemKind.YOUR_NAME_MISSING -> getString(R.string.problem_your_name)
    ProblemKind.NO_CONTACTS -> getString(R.string.problem_no_contacts)
}

internal fun Context.tileSubtitle(s: TileSubtitle, name: String?): String = when (s) {
    TileSubtitle.CONTACTS_TOLD -> getString(R.string.tile_contacts_told)
    TileSubtitle.TEXTING_SOON -> getString(R.string.tile_texting_soon)
    TileSubtitle.FINISH_SETUP -> getString(R.string.tile_finish_setup)
    TileSubtitle.TAP_TO_TEXT_NAME -> getString(R.string.tile_tap_text_name, name.orEmpty())
    TileSubtitle.TAP_TO_TEXT_CONTACTS -> getString(R.string.tile_tap_text_contacts)
}

/** Her saved places are stored under English keys (Home, Work, College, Other); show them in her language. */
internal fun Context.placeLabel(label: String): String = when (label.trim().lowercase()) {
    "home" -> getString(R.string.place_home)
    "work" -> getString(R.string.place_work)
    "college" -> getString(R.string.place_college)
    "other" -> getString(R.string.place_other)
    else -> label
}

/** "9:42 pm": her phone's 12 or 24 hour choice, in the app's language. */
internal fun Context.clockTime(ms: Long): String =
    DateFormat.getTimeFormat(this).format(Date(ms)).lowercase(appLocale)

/** "21 Oct" in the app's language. */
internal fun Context.shortDate(ms: Long): String =
    SimpleDateFormat(DateFormat.getBestDateTimePattern(appLocale, "dMMM"), appLocale).format(Date(ms))

/** "Wed 21 Oct, 9:42 pm" in the app's language. */
internal fun Context.dayAndTime(ms: Long): String =
    SimpleDateFormat(DateFormat.getBestDateTimePattern(appLocale, "EEEdMMMhmm"), appLocale).format(Date(ms))
