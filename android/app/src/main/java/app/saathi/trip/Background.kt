package app.saathi.trip

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.saathi.core.Oem

/** This phone's maker, from the build. */
fun deviceOem(): Oem = Oem.from(Build.MANUFACTURER, Build.BRAND)

/**
 * Opens the screen where her phone maker keeps its own "let this app run in the background" switch.
 * Tries the maker's known screens in order, and falls back to Saathi's page in Settings. Some
 * screens exist but refuse outsiders (SecurityException), so each is tried and skipped if it fails.
 */
fun openBackgroundSettings(ctx: Context, oem: Oem = deviceOem()) {
    val pm = ctx.packageManager
    for (t in oem.targets) {
        val intent = Intent().setComponent(ComponentName(t.pkg, t.cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching {
            if (pm.resolveActivity(intent, 0) == null) false else { ctx.startActivity(intent); true }
        }.getOrDefault(false)
        if (opened) return
    }
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
