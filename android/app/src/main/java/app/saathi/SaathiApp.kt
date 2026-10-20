package app.saathi

import android.app.Application
import app.saathi.data.CrashTracker
import app.saathi.data.TelemetryManager

class SaathiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Register uncaught crash handler before anything else runs
        CrashTracker.install(this)
        // Flush any crashes recorded from previous run
        CrashTracker.flushPending(this)
        // Flush pending telemetry events if any
        TelemetryManager.flush(this)
    }
}
