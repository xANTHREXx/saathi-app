package app.saathi.trip

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Geocoder
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.IBinder
import android.view.KeyEvent
import android.os.SystemClock
import android.util.Log
import app.saathi.R
import app.saathi.alert.Alarm
import app.saathi.alert.AlertManager
import app.saathi.alert.Notifier
import app.saathi.alert.SmsSender
import app.saathi.core.Arrival
import app.saathi.core.BATTERY_DEAD_PCT
import app.saathi.core.BatteryWatch
import app.saathi.core.Destination
import app.saathi.core.EventKind
import app.saathi.core.Fingerprint
import app.saathi.core.Fix
import app.saathi.core.Mode
import app.saathi.core.PowerPress
import app.saathi.core.Reason
import app.saathi.core.SavedPlace
import app.saathi.core.ShakeDetector
import app.saathi.core.TripEvent
import app.saathi.core.Watcher
import app.saathi.core.arrivedText
import app.saathi.core.batteryText
import app.saathi.core.offText
import app.saathi.core.recipients
import app.saathi.core.vehicleNote
import app.saathi.data.Api
import app.saathi.data.Evidence
import app.saathi.data.RouteCache
import app.saathi.data.RunState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
        const val ACTION_CHECK_START = "app.saathi.CHECK_START"
        const val ACTION_CHECK_ADD = "app.saathi.CHECK_ADD"
        const val ACTION_CHECK_CANCEL = "app.saathi.CHECK_CANCEL"
        /** "Don't text" on the earphone-button notification. */
        const val ACTION_CANCEL_PENDING = "app.saathi.CANCEL_PENDING"
        private const val HEADSET_UNDO_MS = 5_000L
        const val EXTRA_MINUTES = "minutes"
        /** What "Add 10 min" adds. */
        const val CHECK_ADD_MIN = 10
        const val EXTRA_DEST = "dest"
        const val EXTRA_MODE = "mode"
        const val EXTRA_PLATE = "plate"
        const val EXTRA_APP = "app"
        const val EXTRA_DRIVER = "driver"
        const val EXTRA_REPLAY = "replay"
        const val EXTRA_SPEED = "speed"

        private const val HEARTBEAT_MS = 60_000L
        /** Wired earphones send HEADSETHOOK; Bluetooth ones usually PLAY_PAUSE, or PLAY and PAUSE in turn. */
        private val HEADSET_KEYS = setOf(KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE)

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

    private val lowBattery = BatteryWatch()
    @Volatile private var batteryPct = -1

    /** Sticky, so it also arrives once at registration with the current level. */
    private val batteryLevel = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return
            batteryPct = level * 100 / scale
            val charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (!watching() || !Store.settings(c).dyingPhoneText) return
            if (lowBattery.onLevel(batteryPct, charging)) {
                val pct = batteryPct
                scope.launch(Dispatchers.IO) { textContacts("battery $pct%") { name, fix, age -> batteryText(name, pct, fix, age) } }
            }
        }
    }

    /** The phone is going down mid-trip: a few seconds to say so, so the text is sent right here. */
    private val shuttingDown = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!watching() || !Store.settings(c).dyingPhoneText) return
            val died = batteryPct in 0..BATTERY_DEAD_PCT
            textContacts(if (died) "battery died" else "switched off") { name, fix, age -> offText(name, died, fix, age) }
        }
    }

    /** Whether a silence from this phone would mean something to her contacts. */
    private fun watching() = TripState.now.onTrip || TripState.now.checkAtMs != null

    // --- trip-only gestures she turns on in Settings: the earphone button (help) and a hard shake (check-in)

    private var media: MediaSession? = null
    private val headsetPresses = PowerPress(needed = 3, windowS = 2.0)
    private val shake = ShakeDetector()

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (!shake.onSample(SystemClock.elapsedRealtime(), e.values[0], e.values[1], e.values[2])) return
            val ui = TripState.now
            if (!ui.onTrip || ui.alertActive || ui.checkInEndsAtMs != null) return
            val event = TripEvent(EventKind.SHAKE, "phone shaken hard", System.currentTimeMillis() / 1000)
            Log.i("Saathi", "shake: check-in")
            TripState.update { it.copy(events = it.events + event) }
            app.saathi.data.TelemetryManager.track(this@TripService, "checkin_triggered", mapOf("reason" to event.kind.name))
            AlertManager.startCheckIn(event)
        }
        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
    }

    private fun startGestures() {
        val s = Store.settings(this)
        if (s.shakeCheckIn) getSystemService(SensorManager::class.java)?.let { sm ->
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(shakeListener, it, SensorManager.SENSOR_DELAY_GAME) }
        }
        if (s.headsetHelp && media == null) startHeadset()
    }

    private fun stopGestures() {
        runCatching { getSystemService(SensorManager::class.java)?.unregisterListener(shakeListener) }
        media?.run { isActive = false; release() }
        media = null
    }

    /**
     * Android sends earphone presses to the app that played audio last. So Saathi holds a media session that
     * says "playing" and plays a moment of silence, without asking for audio focus, so her music isn't paused.
     * If she then plays music, her music app takes the button back: this works while nothing else is playing.
     */
    private fun startHeadset() {
        val session = MediaSession(this, "SaathiHelp")
        session.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(intent: Intent): Boolean {
                @Suppress("DEPRECATION") val e = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                if (e.keyCode !in HEADSET_KEYS) return false
                if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 &&
                    headsetPresses.onToggle(SystemClock.elapsedRealtime())) headsetHelp()
                return true // ours while the session holds the button, so no other app acts on these presses
            }
        })
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
            .setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        session.isActive = true
        media = session
        scope.launch(Dispatchers.IO) { playSilence() }
    }

    private fun playSilence() = runCatching {
        val rate = 8_000
        val pcm = ShortArray(rate / 4) // a quarter second
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
        t.write(pcm, 0, pcm.size)
        t.play()
        Thread.sleep(400)
        t.stop(); t.release()
    }.onFailure { Log.w("Saathi", "headset: silence not played: ${it.message}") }

    /** Three presses: the same 5 s undo as the help tile, from a notification she can reach without the screen. */
    private fun headsetHelp() {
        val ui = TripState.now
        if (ui.alertActive || ui.pendingAlertEndsAtMs != null) return
        Log.i("Saathi", "earphone button: help")
        AlertManager.raiseAfter(Reason.HEADSET, HEADSET_UNDO_MS)
        Alarm(this).buzzOnce()
        Notifier.showPending(this, HEADSET_UNDO_MS)
    }

    // --- "Check on me": a check-in at a time she picks, for walks and waits no ride app knows about

    private var checkJob: Job? = null

    private fun startCheck(minutes: Int) {
        if (minutes <= 0) return
        if (!TripState.now.onTrip) lowBattery.reset() // a timer on its own counts as a trip for the low-battery text
        setCheck(System.currentTimeMillis() + minutes * 60_000L, minutes)
    }

    private fun addToCheck() {
        val at = TripState.now.checkAtMs ?: return
        setCheck(at + CHECK_ADD_MIN * 60_000L, TripState.now.checkMinutes + CHECK_ADD_MIN)
    }

    private fun cancelCheck() {
        checkJob?.cancel(); checkJob = null
        TripState.update { it.copy(checkAtMs = null, checkMinutes = 0) }
        RunState.setCheck(this, null)
    }

    /** Waits until [atMs], then the usual check-in: silence after it alerts, as on a trip. A time already past fires now. */
    private fun setCheck(atMs: Long, minutes: Int) {
        TripState.update { it.copy(checkAtMs = atMs, checkMinutes = minutes) }
        RunState.setCheck(this, atMs, minutes)
        checkJob?.cancel()
        checkJob = scope.launch {
            delay((atMs - System.currentTimeMillis()).coerceAtLeast(0))
            if (TripState.now.checkAtMs != atMs) return@launch // moved or cancelled meanwhile
            TripState.update { it.copy(checkAtMs = null, checkMinutes = 0) }
            RunState.setCheck(this@TripService, null)
            val event = TripEvent(EventKind.TIMER, "her $minutes-minute check ran out", System.currentTimeMillis() / 1000, minutes)
            Log.i("Saathi", "check timer: ${event.detail}")
            TripState.update { it.copy(events = it.events + event) }
            app.saathi.data.TelemetryManager.track(this@TripService, "checkin_triggered", mapOf("reason" to event.kind.name))
            AlertManager.startCheckIn(event)
        }
    }

    /**
     * A plain text to her contacts for the city she is in, with her last location. Not an alert: no alarm,
     * no live page, nothing for her to undo.
     */
    private fun textContacts(what: String, build: (name: String, fix: Fix?, fixAgeS: Long) -> String) {
        val s = Store.settings(this)
        val ui = TripState.now
        val fixAge = if (ui.lastFixAtMs == 0L) 0 else (System.currentTimeMillis() - ui.lastFixAtMs) / 1000
        val text = build(s.name.ifBlank { "She" }, ui.lastFix?.copy(t = ui.lastFixAtMs / 1000), fixAge)
        val r = SmsSender.send(this, recipients(s.contacts, TripState.city()), text)
        Log.i("Saathi", "$what: told ${r.sent.joinToString()}")
    }

    override fun onCreate() {
        super.onCreate()
        Notifier.channels(this)
        AlertManager.init(this)
        AlertManager.onOkay = { watcher?.reset(); shake.quiet(SystemClock.elapsedRealtime()) }
        registerReceiver(screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
        })
        registerReceiver(batteryLevel, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        registerReceiver(shuttingDown, IntentFilter(Intent.ACTION_SHUTDOWN))
        // A timer set before Android restarted the service still fires.
        RunState.check(this)?.let { (at, minutes) -> setCheck(at, minutes) }
        // A pulse every minute, so a phone that kills Saathi mid-trip leaves a trace (see RunState).
        scope.launch {
            while (true) {
                RunState.beat(this@TripService, TripState.now.onTrip)
                delay(HEARTBEAT_MS)
            }
        }
        // Keep the notification and widget in step with the state.
        scope.launch {
            TripState.ui.map { listOf(it.onTrip, it.alertActive, it.told, it.destLabel, it.vehicleNote, it.checkAtMs) }.distinctUntilChanged().collect {
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
            ACTION_CHECK_START -> startCheck(intent.getIntExtra(EXTRA_MINUTES, 0))
            ACTION_CHECK_ADD -> addToCheck()
            ACTION_CHECK_CANCEL -> cancelCheck()
            ACTION_CANCEL_PENDING -> { AlertManager.cancelPendingAlert(); Notifier.cancel(this, Notifier.PENDING_ID) }
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
        lowBattery.reset()
        arrival = null
        planJob?.cancel()
        watcher = Watcher(mode, emptyList(), durationS = null, startedAt = tripStartedAt)
        TripState.update {
            it.copy(onTrip = true, mode = mode, plate = i.getStringExtra(EXTRA_PLATE), app = i.getStringExtra(EXTRA_APP),
                driver = i.getStringExtra(EXTRA_DRIVER), events = emptyList(), plateCheck = null,
                startedAtMs = tripStartedAt * 1000, trail = emptyList(),
                fromLabel = null, destLabel = null, route = emptyList(), noRoute = false, vehicleNote = null)
        }
        RunState.beat(this, onTrip = true) // don't wait for the next pulse to say a trip is running
        stopGestures(); startGestures() // a new trip re-reads her gesture settings
        i.getStringExtra(EXTRA_PLATE)?.let { plate -> scope.launch(Dispatchers.IO) { checkVehicle(plate) } }
        app.saathi.data.TelemetryManager.track(this, "trip_started", mapOf(
            "mode" to mode.name,
            "auto_detected" to (i.getStringExtra(EXTRA_APP) != null)
        ))
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
        val text = resources.getQuantityString(R.plurals.vehicle_flag_text, f.reports, f.reports) +
            if (f.sample) " " + getString(R.string.vehicle_sample) else ""
        val n = android.app.Notification.Builder(this, "alert")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.vehicle_notif_title, plate))
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText("$text ${getString(R.string.vehicle_flag_more)}"))
            .build()
        getSystemService(NotificationManager::class.java).notify(50, n)
        Log.i("Saathi", "flagged $plate: ${f.reports}")
    }

    private fun onFix(fix: Fix) {
        fixes += fix
        TripState.update { it.copy(lastFix = fix, lastFixAtMs = System.currentTimeMillis(), trail = if (it.onTrip) (it.trail + fix).takeLast(600) else it.trail) }
        maybeGeocode(fix)
        if (fixes.size == 1 && !demoRoute) planFrom(fix)
        // Never mid check-in or alert: arriving doesn't answer "Are you okay?".
        val ui = TripState.now
        if (ui.checkInEndsAtMs == null && !ui.alertActive && ui.pendingAlertEndsAtMs == null &&
            arrival?.add(fix, fixes.first().t) == true) {
            arrive()
            return
        }
        val event = watcher?.add(fix) ?: return
        Log.i("Saathi", "watcher: ${event.kind} ${event.detail}")
        TripState.update { it.copy(events = it.events + event) }
        app.saathi.data.TelemetryManager.track(this, "checkin_triggered", mapOf("reason" to event.kind.name))
        AlertManager.startCheckIn(event)
    }

    private fun maybeGeocode(fix: Fix) {
        val now = System.currentTimeMillis()
        if (now - lastGeocodeMs < 60_000 || !Geocoder.isPresent()) return
        lastGeocodeMs = now
        scope.launch(Dispatchers.IO) {
            runCatching {
                val a = Geocode.lookup(this@TripService, fix.lat, fix.lng) ?: return@launch
                val label = Geocode.shortLabel(a)
                TripState.update { it.copy(place = Place(a.locality ?: a.subAdminArea, a.adminArea, a.countryCode, label)) }
            }
        }
    }

    // --- where the trip is headed, so the watcher can check for detours and late arrival

    /** Set once the trip knows where it's headed. Null for "Not sure", when the trip runs until she ends it. */
    @Volatile private var arrival: Arrival? = null

    /** She reached the place the trip was headed: the trip ends as if she tapped End, so no long-stop check-in at home. */
    private fun arrive() {
        val label = TripState.now.destLabel ?: return
        Log.i("Saathi", "arrived at $label")
        if (Store.settings(this).arrivalText) {
            val at = System.currentTimeMillis() / 1000
            scope.launch(Dispatchers.IO) { textContacts("arrived") { name, _, _ -> arrivedText(name, label, at) } }
        }
        stopTrip()
    }

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
            arrival = null
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
        arrival = Arrival(to.fix) // known before any route comes back, so arriving works offline too
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
        arrival = pts.lastOrNull()?.let { Arrival(it) }
        TripState.update { it.copy(destLabel = d.to, route = pts) }
        true
    }.getOrDefault(false)

    private fun stopTrip() {
        tripJob?.cancel(); tripJob = null
        planJob?.cancel(); planJob = null
        val ui = TripState.now
        if (ui.onTrip) {
            saveRecord(ui)
            app.saathi.data.TelemetryManager.flush(this) // send what the trip queued while she has signal
        }
        watcher = null
        arrival = null
        stopGestures()
        TripState.update { it.copy(onTrip = false, mode = null, plate = null, app = null, driver = null,
            fromLabel = null, destLabel = null, route = emptyList(), noRoute = false, vehicleNote = null) }
        RunState.cleanStop(this) // ended on purpose: the quiet after this is not a kill
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
            eventItems = ui.events, alertEntries = AlertManager.takeAlertEntries(),
            plateCheck = ui.plateCheck, evidence = evidence,
            lat = fixes.lastOrNull()?.lat, lng = fixes.lastOrNull()?.lng, endedAt = now,
            from = ui.fromLabel, to = ui.destLabel?.takeIf { it in saved },
        ))
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screen) }
        runCatching { unregisterReceiver(batteryLevel) }
        runCatching { unregisterReceiver(shuttingDown) }
        stopGestures()
        scope.cancel()
        // Only a stop with no trip running is clean; if Android took the service down mid-trip, say so.
        if (!TripState.now.onTrip) RunState.cleanStop(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
