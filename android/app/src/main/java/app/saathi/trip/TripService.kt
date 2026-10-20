package app.saathi.trip

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Geocoder
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import app.saathi.alert.AlertManager
import app.saathi.alert.Notifier
import app.saathi.core.Destination
import app.saathi.core.Fingerprint
import app.saathi.core.Fix
import app.saathi.core.Mode
import app.saathi.core.PowerPress
import app.saathi.core.Reason
import app.saathi.core.SavedPlace
import app.saathi.core.Watcher
import app.saathi.core.vehicleNote
import app.saathi.data.Api
import app.saathi.data.Evidence
import app.saathi.data.RouteCache
import app.saathi.data.Store
import app.saathi.data.TripRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import app.saathi.widget.SaathiWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Runs while Saathi is guarding: owns the persistent notification, the power-button
 * trigger and, during a trip, the location feed and the watcher. Screen off is fine.
 */
class TripService : Service() {
    companion object {
        const val ACTION_GUARD = "app.saathi.GUARD"
        const val ACTION_START_TRIP = "app.saathi.START_TRIP"
        const val ACTION_STOP_TRIP = "app.saathi.STOP_TRIP"
        const val ACTION_HELP = "app.saathi.HELP"
        const val ACTION_OK = "app.saathi.OK"
        const val ACTION_SET_DEST = "app.saathi.SET_DEST"
        const val EXTRA_DEST = "dest"
        const val EXTRA_MODE = "mode"
        const val EXTRA_PLATE = "plate"
        const val EXTRA_APP = "app"
        const val EXTRA_DRIVER = "driver"
        const val EXTRA_REPLAY = "replay"
        const val EXTRA_SPEED = "speed"

        fun intent(ctx: Context, action: String) = Intent(ctx, TripService::class.java).setAction(action)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tripJob: Job? = null
    private var watcher: Watcher? = null
    private val fixes = mutableListOf<Fix>()
    private var tripStartedAt = 0L
    private val power = PowerPress()
    private var lastGeocodeMs = 0L
    private var planJob: Job? = null
    private var demoRoute = false

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            Log.i("Saathi", "screen ${i.action} at ${SystemClock.elapsedRealtime()}")
            if (power.onToggle(SystemClock.elapsedRealtime())) {
                Log.i("Saathi", "power-button trigger")
                AlertManager.raise(Reason.POWER_BUTTON)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Notifier.channels(this)
        AlertManager.init(this)
        AlertManager.onOkay = { watcher?.reset() }
        registerReceiver(screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
        })
        // Keep the notification and widget in step with the state.
        scope.launch {
            TripState.ui.map { listOf(it.onTrip, it.alertActive, it.told, it.destLabel, it.vehicleNote) }.distinctUntilChanged().collect {
                getSystemService(NotificationManager::class.java).notify(Notifier.GUARD_ID, Notifier.guard(this@TripService, TripState.now))
                runCatching { SaathiWidget().updateAll(this@TripService) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        power.needed = Store.settings(this).powerPresses
        TripState.update { it.copy(guarding = true) }
        when (intent?.action) {
            ACTION_START_TRIP -> startTrip(intent)
            ACTION_STOP_TRIP -> stopTrip()
            ACTION_HELP -> AlertManager.raise(Reason.NOTIFICATION)
            ACTION_OK -> AlertManager.imOkay()
            ACTION_SET_DEST -> setDestination(intent.getStringExtra(EXTRA_DEST))
        }
        return START_STICKY
    }

    private fun goForeground() {
        val n = Notifier.guard(this, TripState.now)
        try {
            startForeground(Notifier.GUARD_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (e: Exception) {
            Log.w("Saathi", "location foreground not allowed yet: ${e.message}")
            startForeground(Notifier.GUARD_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
    }

    private fun startTrip(i: Intent) {
        tripJob?.cancel()
        val mode = runCatching { Mode.valueOf(i.getStringExtra(EXTRA_MODE) ?: "WALK") }.getOrDefault(Mode.WALK)
        tripStartedAt = System.currentTimeMillis() / 1000
        fixes.clear()
        planJob?.cancel()
        watcher = Watcher(mode, emptyList(), durationS = null, startedAt = tripStartedAt)
        TripState.update {
            it.copy(onTrip = true, mode = mode, plate = i.getStringExtra(EXTRA_PLATE), app = i.getStringExtra(EXTRA_APP),
                driver = i.getStringExtra(EXTRA_DRIVER), events = emptyList(), plateCheck = null,
                startedAtMs = tripStartedAt * 1000, trail = emptyList(),
                fromLabel = null, destLabel = null, route = emptyList(), noRoute = false, vehicleNote = null)
        }
        i.getStringExtra(EXTRA_PLATE)?.let { plate -> scope.launch(Dispatchers.IO) { checkVehicle(plate) } }
        val replay = i.getStringExtra(EXTRA_REPLAY)
        demoRoute = replay != null && loadDemoRoute(replay)
        val source = if (replay != null) ReplaySource(this, replay, i.getIntExtra(EXTRA_SPEED, 30)) else FusedSource(this)
        tripJob = scope.launch {
            source.fixes().collect { fix -> onFix(fix) }
        }
    }

    /** ST-6: counts only, shown only to her, never a driver's name. */
    private fun checkVehicle(plate: String) {
        val f = Api.vehicle(this, plate) ?: return
        TripState.update { it.copy(vehicleNote = vehicleNote(f.rides, f.reports, f.flagged, f.sample)) }
        if (!f.flagged) return
        val text = "This vehicle has ${f.reports} reports from other women in the last 90 days." + if (f.sample) " (Sample data)" else ""
        val n = android.app.Notification.Builder(this, "alert")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Before you get in: $plate")
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText("$text You can continue, or cancel and rebook in your ride app."))
            .build()
        getSystemService(NotificationManager::class.java).notify(50, n)
        Log.i("Saathi", "flagged $plate: ${f.reports}")
    }

    private fun onFix(fix: Fix) {
        fixes += fix
        TripState.update { it.copy(lastFix = fix, lastFixAtMs = System.currentTimeMillis(), trail = if (it.onTrip) (it.trail + fix).takeLast(600) else it.trail) }
        maybeGeocode(fix)
        if (fixes.size == 1 && !demoRoute) planFrom(fix)
        val event = watcher?.add(fix) ?: return
        Log.i("Saathi", "watcher: ${event.kind} ${event.detail}")
        TripState.update { it.copy(events = it.events + event) }
        AlertManager.startCheckIn(event)
    }

    private fun maybeGeocode(fix: Fix) {
        val now = System.currentTimeMillis()
        if (now - lastGeocodeMs < 60_000 || !Geocoder.isPresent()) return
        lastGeocodeMs = now
        scope.launch(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                val a = Geocoder(this@TripService, Locale.ENGLISH).getFromLocation(fix.lat, fix.lng, 1)?.firstOrNull()
                    ?: return@launch
                val label = listOfNotNull(a.featureName?.takeIf { f -> f.any { it.isLetter() } && !f.contains("+") }, a.thoroughfare ?: a.subLocality)
                    .distinct().joinToString(", ").ifBlank { null }
                TripState.update { it.copy(place = Place(a.locality ?: a.subAdminArea, a.adminArea, a.countryCode, label)) }
            }
        }
    }

    // --- where the trip is headed, so the watcher can check for detours and late arrival

    /** First fix: guess the destination from her saved places and past trips. */
    private fun planFrom(at: Fix) {
        val places = Store.settings(this).places
        val from = Destination.origin(at, places)
        val past = Store.records(this).map { it.from to it.to }
        TripState.update { it.copy(fromLabel = from?.label) }
        Destination.guess(at, places, past)?.let { plan(at, from, it) }
    }

    /** She picked a destination from the trip notification, or "Not sure" (null). */
    private fun setDestination(label: String?) {
        if (!TripState.now.onTrip) return
        val to = Store.settings(this).places.firstOrNull { it.label == label }
        if (to == null) {
            planJob?.cancel()
            watcher?.plan(emptyList(), null, System.currentTimeMillis() / 1000)
            TripState.update { it.copy(destLabel = null, route = emptyList()) }
            return
        }
        val at = TripState.now.lastFix ?: return
        plan(at, null, to) // from where she is now, so the place-to-place cache doesn't apply
    }

    /** Route from the cache, or the backend; a stale cached copy beats nothing when offline. */
    private fun plan(at: Fix, from: SavedPlace?, to: SavedPlace) {
        val mode = TripState.now.mode ?: Mode.WALK
        val key = from?.let { Destination.cacheKey(it.label, to.label, mode) }
        TripState.update { it.copy(destLabel = to.label, route = emptyList(), noRoute = false) }
        planJob?.cancel()
        planJob = scope.launch(Dispatchers.IO) {
            val fresh = key?.let { RouteCache.get(this@TripService, it) }
                ?: Api.route(from?.fix ?: at, to.fix, mode.name)
                    ?.let { RouteCache.Entry(it.points, it.durationS, System.currentTimeMillis() / 1000) }
                    ?.also { e -> key?.let { RouteCache.put(this@TripService, it, e) } }
            val r = fresh ?: key?.let { RouteCache.stale(this@TripService, it) }
            if (r == null) {
                TripState.update { it.copy(noRoute = true) }
                return@launch
            }
            Log.i("Saathi", "route to ${to.label}: ${r.points.size} points, ${r.durationS} s")
            val start = if (from != null) tripStartedAt else System.currentTimeMillis() / 1000
            watcher?.plan(r.points, r.durationS, start)
            TripState.update { it.copy(route = r.points) }
        }
    }

    @Serializable private data class DemoPt(val lat: Double, val lng: Double)
    @Serializable private data class DemoRoute(val to: String, val duration_s: Long, val points: List<DemoPt>)

    /** Demo replays come with the route the trip should have taken. */
    private fun loadDemoRoute(replay: String): Boolean = runCatching {
        val text = assets.open("tracks/$replay.route.json").use { it.reader().readText() }
        val d = Json { ignoreUnknownKeys = true }.decodeFromString<DemoRoute>(text)
        val pts = d.points.map { Fix(it.lat, it.lng, 0) }
        watcher?.plan(pts, d.duration_s, tripStartedAt)
        TripState.update { it.copy(destLabel = d.to, route = pts) }
        true
    }.getOrDefault(false)

    private fun stopTrip() {
        tripJob?.cancel(); tripJob = null
        planJob?.cancel(); planJob = null
        val ui = TripState.now
        if (ui.onTrip) saveRecord(ui)
        watcher = null
        TripState.update { it.copy(onTrip = false, mode = null, plate = null, app = null, driver = null,
            fromLabel = null, destLabel = null, route = emptyList(), noRoute = false, vehicleNote = null) }
    }

    private fun saveRecord(ui: UiState) {
        val now = System.currentTimeMillis() / 1000
        val log = fixes.joinToString("\n") { "${it.t},${it.lat},${it.lng}" }.toByteArray()
        val evidence = mutableListOf(Evidence("location log", Fingerprint.sha256(log), now))
        AlertManager.recordingFile()?.takeIf { it.exists() }?.let {
            evidence += Evidence("audio ${it.name}", Fingerprint.sha256(it.readBytes()), now)
        }
        val caseId = "SA-" + UUID.randomUUID().toString().take(8).uppercase()
        // One ride in this vehicle for the next woman's count, only with her consent. Concerns come from her own answer later.
        val plate = ui.plate
        if (plate != null && Store.settings(this).communityConsent) {
            scope.launch(Dispatchers.IO) { Api.ride(this@TripService, plate, tripStartedAt, caseId) }
        }
        val saved = Store.settings(this).places.map { it.label }
        Store.saveRecord(this, TripRecord(
            caseId = caseId,
            createdAt = tripStartedAt, mode = ui.mode?.name ?: "WALK", app = ui.app, plate = ui.plate,
            events = ui.events.map { it.detail }, alerts = AlertManager.takeAlertLog(),
            plateCheck = ui.plateCheck, evidence = evidence,
            lat = fixes.lastOrNull()?.lat, lng = fixes.lastOrNull()?.lng, endedAt = now,
            from = ui.fromLabel, to = ui.destLabel?.takeIf { it in saved },
        ))
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screen) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
