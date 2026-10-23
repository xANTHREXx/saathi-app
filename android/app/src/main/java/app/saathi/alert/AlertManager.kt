package app.saathi.alert

import android.content.Context
import android.util.Log
import app.saathi.R
import app.saathi.core.Fingerprint
import app.saathi.core.Reason
import app.saathi.core.TripEvent
import app.saathi.core.alertText
import app.saathi.core.okText
import app.saathi.core.recipients
import app.saathi.core.retryTargets
import app.saathi.core.toldNames
import app.saathi.data.ALERT_RECORD
import app.saathi.data.AlertLogEntry
import app.saathi.data.Evidence
import app.saathi.data.Onboarding
import app.saathi.data.TripRecord
import app.saathi.data.Store
import app.saathi.data.TelemetryManager
import app.saathi.trip.LocationOnce
import app.saathi.trip.TripState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Check-ins and alerts. Every way of asking for help ends in [raise].
 * Decisions here are rules; the only network call is the optional live page.
 */
object AlertManager {
    const val CHECKIN_S = 10L

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var checkInJob: Job? = null
    private var liveJob: Job? = null
    private var practiceJob: Job? = null
    private var pendingJob: Job? = null
    private val pendingLock = Any()
    private val practiceLock = Any()
    private lateinit var alarm: Alarm
    private lateinit var recorder: Recorder
    private val alertLog = mutableListOf<String>()
    private val alertEntries = mutableListOf<AlertLogEntry>()
    /** The text the current alert went out with, kept so a manual retry sends the same message. */
    @Volatile private var lastAlertText: String? = null
    /** The record of an alert raised with no trip running, while it lasts; retries and I'm okay are written to it. */
    @Volatile private var offTripCase: String? = null

    /**
     * Where this alert's history goes: into the trip it happened on, or, with no trip running, into its own record,
     * saved at once so a closed app or a dead phone doesn't lose it, and never tacked onto the next trip.
     */
    private fun logAlert(entry: AlertLogEntry, logLine: String) {
        val ui = TripState.now
        val case = offTripCase
        when {
            case != null -> Store.records(app).firstOrNull { it.caseId == case }
                ?.let { Store.saveRecord(app, it.copy(alertEntries = it.alertEntries + entry)) }
            ui.onTrip -> { alertLog += logLine; alertEntries += entry }
            else -> {
                val id = "SA-" + java.util.UUID.randomUUID().toString().take(8).uppercase()
                offTripCase = id
                Store.saveRecord(app, TripRecord(
                    caseId = id, createdAt = entry.at, mode = ALERT_RECORD, alertEntries = listOf(entry),
                    lat = ui.lastFix?.lat, lng = ui.lastFix?.lng,
                ))
            }
        }
    }

    /** Called by the trip service when she says she's okay, so the watcher can start fresh. */
    var onOkay: (() -> Unit)? = null

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        alarm = Alarm(app)
        recorder = Recorder(app)
    }

    fun startCheckIn(event: TripEvent) {
        if (checkInJob?.isActive == true || TripState.now.alertActive) return
        endPractice() // a real check-in always wins over a practice
        val ends = System.currentTimeMillis() + CHECKIN_S * 1000
        TripState.update { it.copy(checkInEndsAtMs = ends) }
        alarm.start()
        Notifier.showCheckIn(app, event)
        checkInJob = scope.launch {
            delay(CHECKIN_S * 1000)
            if (isActive && TripState.now.checkInEndsAtMs != null) {
                endCheckIn()
                TelemetryManager.track(app, "checkin_timed_out")
                raise(Reason.NO_RESPONSE, event)
            }
        }
    }

    /**
     * Speak for me's "Don't text" window: the alert goes out after [delayMs] unless [cancelPendingAlert] is called.
     * Runs in [scope], so closing the screen or locking the phone does not stop it. Ignored if an alert is
     * already active or one is already pending.
     */
    fun raiseAfter(reason: Reason, delayMs: Long) {
        synchronized(pendingLock) {
            if (TripState.now.alertActive || pendingJob?.isActive == true || TripState.now.pendingAlertEndsAtMs != null) return
            val ends = System.currentTimeMillis() + delayMs
            TripState.update { it.copy(pendingAlertEndsAtMs = ends) }
            pendingJob = scope.launch {
                delay(delayMs)
                // Same lock as cancelPendingAlert: either it cancelled in time, or the alert goes out.
                val go = synchronized(pendingLock) {
                    if (!isActive || TripState.now.pendingAlertEndsAtMs == null) false
                    else { TripState.update { it.copy(pendingAlertEndsAtMs = null) }; pendingJob = null; true }
                }
                if (go) startAlert(reason, null)
            }
        }
    }

    /** The one deliberate press that undoes an accidental Speak for me. True if an alert was actually held back. */
    fun cancelPendingAlert(): Boolean = clearPending()

    private fun clearPending(): Boolean = synchronized(pendingLock) {
        val was = pendingJob?.isActive == true || TripState.now.pendingAlertEndsAtMs != null
        pendingJob?.cancel(); pendingJob = null
        if (TripState.now.pendingAlertEndsAtMs != null) TripState.update { it.copy(pendingAlertEndsAtMs = null) }
        was
    }

    /** Every other way of asking for help is immediate: it drops a pending Speak for me window, then alerts once. */
    fun raise(reason: Reason, event: TripEvent? = null) {
        clearPending()
        startAlert(reason, event)
    }

    private fun startAlert(reason: Reason, event: TripEvent?) {
        endPractice() // a real alert takes over from a practice; no-op when none is running
        scope.launch(Dispatchers.IO) {
            if (TripState.now.alertActive) return@launch
            TripState.update { it.copy(alertActive = true) }
            alarm.buzzOnce()
            val recording = recorder.start()
            val s = Store.settings(app)
            if (!TripState.now.onTrip || TripState.now.lastFix == null) {
                // Outside a trip there is no feed: take one reading so the SMS always has a map link.
                LocationOnce.get(app)?.let { f ->
                    TripState.update { it.copy(lastFix = f, lastFixAtMs = f.t * 1000) }
                }
            }
            val ui = TripState.now
            val to = recipients(s.contacts, TripState.city())
            val liveUrl = LiveShare.create(s.name.ifBlank { "She" }, ui.plate)
            val fixAge = if (ui.lastFixAtMs == 0L) 0 else (System.currentTimeMillis() - ui.lastFixAtMs) / 1000
            val text = alertText(s.name.ifBlank { "Your contact" }, reason, ui.plate, event ?: ui.events.lastOrNull(),
                ui.place.label, ui.lastFix?.copy(t = ui.lastFixAtMs / 1000), fixAge, liveUrl)
            lastAlertText = text
            val r = SmsSender.send(app, to, text)
            val now = System.currentTimeMillis() / 1000
            logAlert(AlertLogEntry(now, r.sent, reason = reason.name), "${reason.name} at $now: told ${r.sent.joinToString()}")
            Log.i("Saathi", "alert ${reason.name}: $text")
            TripState.update { it.copy(told = r.sent, smsFailed = r.failed, recording = recording) }
            Notifier.showAlerted(app, r.sent, r.failed)
            TelemetryManager.track(app, "alert_sent", mapOf(
                "reason" to reason.name,
                "contacts_count" to to.size,
                "offline" to (liveUrl == null)
            ))
            if (liveUrl != null) startLivePush()
        }
    }

    /**
     * Her one manual retry: texts the same alert to only the contacts it could not reach. Does nothing
     * unless an alert is active with failures, so it can never start a second alert or text anyone twice.
     */
    fun resend() {
        scope.launch(Dispatchers.IO) {
            val ui = TripState.now
            val text = lastAlertText
            if (!ui.alertActive || ui.smsFailed.isEmpty() || text == null) return@launch
            val to = retryTargets(Store.settings(app).contacts, TripState.city(), ui.smsFailed)
            val r = SmsSender.send(app, to, text)
            val now = System.currentTimeMillis() / 1000
            logAlert(AlertLogEntry(now, r.sent, retry = true), "RETRY at $now: told ${r.sent.joinToString()}")
            TripState.update { it.copy(told = (it.told + r.sent).distinct(), smsFailed = r.failed) }
            Notifier.showAlerted(app, TripState.now.told, r.failed)
        }
    }

    /** She's okay: stop everything, tell contacts only if an alert went out. */
    fun imOkay() {
        lastAlertText = null
        if (TripState.now.practice) {
            Onboarding.markPracticeDone(app, answered = true, from = practiceFrom)
            endPractice()
            return
        } // a practice: nothing was sent, so nothing to take back
        clearPending() // she says she's fine: a Speak for me window still counting down never goes out
        val wasAlert = TripState.now.alertActive
        val answeredCheckIn = TripState.now.checkInEndsAtMs != null // a real check-in was waiting (practices returned above)
        endCheckIn()
        liveJob?.cancel(); LiveShare.stop()
        val audio = recorder.stop()
        // An alert with no trip closes its own record: when she stood it down, and the sealed recording, if any.
        offTripCase?.takeIf { wasAlert }?.let { case ->
            offTripCase = null
            scope.launch(Dispatchers.IO) {
                val now = System.currentTimeMillis() / 1000
                val sealed = audio?.takeIf { it.exists() }?.let { listOf(Evidence("audio ${it.name}", Fingerprint.sha256(it.readBytes()), now)) }.orEmpty()
                Store.records(app).firstOrNull { it.caseId == case }
                    ?.let { Store.saveRecord(app, it.copy(endedAt = now, evidence = it.evidence + sealed)) }
            }
        }
        Notifier.cancel(app, Notifier.ALERT_ID)
        TripState.update { it.copy(alertActive = false, told = emptyList(), smsFailed = emptyList(), recording = false) }
        if (answeredCheckIn) {
            TelemetryManager.track(app, "checkin_answered")
            if (!Onboarding.tipShown(app, "first_checkin")) {
                Onboarding.markTip(app, "first_checkin")
                val contacts = Store.settings(app).contacts
                val names = toldNames(contacts)
                val who = when {
                    names == null -> app.getString(R.string.names_none)
                    names.others > 0 -> app.resources.getQuantityString(R.plurals.ready_told_names_more, names.others, names.first, names.others)
                    names.second != null -> app.getString(R.string.ready_told_names_two, names.first, names.second)
                    else -> names.first
                }
                val text = app.getString(R.string.tip_first_checkin, who)
                TripState.update { it.copy(tip = text) }
            }
        }
        onOkay?.invoke()
        if (wasAlert) scope.launch(Dispatchers.IO) {
            val s = Store.settings(app)
            SmsSender.send(app, recipients(s.contacts, TripState.city()), okText(s.name.ifBlank { "She" }))
        }
    }

    @Volatile private var practiceFrom: String = "setup"

    /** A practice check-in: same alarm, notification and screen as a real one, but silence never texts anyone. */
    fun startPractice(delayMs: Long, from: String = "setup") {
        if (checkInJob?.isActive == true || TripState.now.alertActive || practiceJob?.isActive == true) return
        practiceFrom = from
        TelemetryManager.trackOnboarding(app, "practice_started", mapOf("from" to from))
        TripState.update { it.copy(practice = false, practiceOver = false) }
        practiceJob = scope.launch {
            delay(delayMs)
            synchronized(practiceLock) {
                if (!isActive) return@launch
                val ends = System.currentTimeMillis() + CHECKIN_S * 1000
                TripState.update { it.copy(practice = true, practiceOver = false, checkInEndsAtMs = ends) }
                alarm.start()
                Notifier.showCheckIn(app, null, practice = true)
            }
            delay(CHECKIN_S * 1000)
            synchronized(practiceLock) {
                if (!isActive || !TripState.now.practice || TripState.now.checkInEndsAtMs == null) return@launch
                // Ran out unanswered: stop the noise and say so. Deliberately nothing is sent.
                alarm.stop()
                Notifier.cancel(app, Notifier.CHECKIN_ID)
                Onboarding.markPracticeDone(app, answered = false, from = practiceFrom)
                TripState.update { it.copy(checkInEndsAtMs = null, practiceOver = true) }
            }
        }
    }

    /** Ends a practice (answered, or dismissed afterwards). */
    fun endPractice() {
        synchronized(practiceLock) {
            if (!TripState.now.practice && practiceJob?.isActive != true) return
            practiceJob?.cancel(); practiceJob = null
            endCheckIn()
            TripState.update { it.copy(practice = false, practiceOver = false) }
        }
    }

    fun takeAlertLog(): List<String> = alertLog.toList().also { alertLog.clear() }
    fun takeAlertEntries(): List<AlertLogEntry> = alertEntries.toList().also { alertEntries.clear() }
    fun recordingFile() = recorder.file

    private fun endCheckIn() {
        checkInJob?.cancel()
        alarm.stop()
        Notifier.cancel(app, Notifier.CHECKIN_ID)
        TripState.update { it.copy(checkInEndsAtMs = null) }
    }

    private fun startLivePush() {
        liveJob?.cancel()
        liveJob = scope.launch(Dispatchers.IO) {
            val until = System.currentTimeMillis() + 60 * 60 * 1000
            while (isActive && TripState.now.alertActive && System.currentTimeMillis() < until) {
                if (!TripState.now.onTrip) LocationOnce.get(app, 3000)?.let { f ->
                    TripState.update { it.copy(lastFix = f, lastFixAtMs = System.currentTimeMillis()) }
                }
                TripState.now.lastFix?.let { LiveShare.push(it, TripState.now.events.map { e -> e.detail }) }
                delay(10_000)
            }
        }
    }
}
