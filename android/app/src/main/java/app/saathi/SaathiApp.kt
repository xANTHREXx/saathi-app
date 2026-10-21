package app.saathi

import android.app.Application
import app.saathi.data.CrashTracker
import app.saathi.data.RunState
import app.saathi.data.TelemetryManager
import app.saathi.data.TestDevice

class SaathiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Before the service can write a new pulse over it: did the phone kill the last process mid-trip?
        runCatching { RunState.checkForStop(this) }
        // Before anything can call the backend: says whether this is a test phone.
        TestDevice.init(this)
        // Register uncaught crash handler before anything else runs
        CrashTracker.install(this)
        // Flush any crashes recorded from previous run
        CrashTracker.flushPending(this)
        // Flush pending telemetry events if any
        TelemetryManager.flush(this)
    }
}
