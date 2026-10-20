package app.saathi.ui.setup

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.saathi.ui.theme.Saathi

/** Everything step 2 asks for, in the order she sees it. Required ones first, then the optional ones. */
enum class Perm(val title: String, val why: String, val icon: ImageVector, val required: Boolean) {
    LOCATION("Location", "To know where your trip goes.", Icons.Rounded.LocationOn, true),
    LOCATION_ALWAYS("Location all the time", "So Saathi keeps watching with the screen off.", Icons.Rounded.MyLocation, true),
    SMS("Text messages", "Alerts go from your own SIM, even with no data.", Icons.Rounded.Sms, true),
    NOTIFICATIONS("Notifications", "For check-ins and the trip card.", Icons.Rounded.Notifications, true),
    LOCK_SCREEN("Check-ins over the lock screen", "So 'Are you okay?' shows even when your phone is locked.", Icons.Rounded.Lock, true),
    BACKGROUND("Keep running in the background", "So Android doesn't stop Saathi mid-trip.", Icons.Rounded.BatteryChargingFull, true),
    RIDES("Notice Uber, Ola and Rapido rides", "Trips start on their own when a ride is booked.", Icons.Rounded.LocalTaxi, false),
    MICROPHONE("Microphone", "Records only after help is raised.", Icons.Rounded.Mic, false),
}

/** The permissions worth showing on this Android version. */
internal fun visiblePerms(sdk: Int = Build.VERSION.SDK_INT): List<Perm> = Perm.values().filter {
    when (it) {
        Perm.NOTIFICATIONS -> sdk >= 33
        Perm.LOCK_SCREEN -> sdk >= 34
        else -> true
    }
}

/** The first required permission still missing, in display order, or null when she is done. */
internal fun firstMissingRequired(granted: Map<Perm, Boolean>, sdk: Int = Build.VERSION.SDK_INT): Perm? =
    visiblePerms(sdk).firstOrNull { it.required && granted[it] != true }

/** Reads one permission's real state from the system, right now. */
internal fun granted(ctx: Context, perm: Perm): Boolean {
    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    return when (perm) {
        Perm.LOCATION -> has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)
        Perm.LOCATION_ALWAYS -> has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        Perm.SMS -> has(Manifest.permission.SEND_SMS)
        Perm.NOTIFICATIONS -> Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)
        Perm.LOCK_SCREEN -> Build.VERSION.SDK_INT < 34 ||
            ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        Perm.BACKGROUND -> ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
        Perm.RIDES -> NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        Perm.MICROPHONE -> has(Manifest.permission.RECORD_AUDIO)
    }
}

internal fun readAllPerms(ctx: Context): Map<Perm, Boolean> = Perm.values().associateWith { granted(ctx, it) }

/** Live permission state: re-read every time the app resumes (back from system settings), and on demand. */
internal class LivePerms(val granted: Map<Perm, Boolean>, val refresh: () -> Unit)

@Composable
internal fun rememberLivePerms(): LivePerms {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf(readAllPerms(ctx)) }
    val refresh = remember(ctx) { { state = readAllPerms(ctx) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    return LivePerms(state, refresh)
}

/**
 * Step 2: permissions asked in place, with live state. State is read by [rememberLivePerms] in [SetupFlow];
 * this only draws it and launches each permission's dialog or settings screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PermissionsStep(
    granted: Map<Perm, Boolean>,
    onRefresh: () -> Unit,
    problemPerm: Perm?,
    problemNonce: Int,
) {
    val ctx = LocalContext.current
    val activity = ctx as? ComponentActivity
    val c = Saathi.colors

    val requesters = remember { Perm.values().associateWith { BringIntoViewRequester() } }
    LaunchedEffect(problemPerm, problemNonce) {
        problemPerm?.let { requesters[it]?.bringIntoView() }
    }

    // Permissions Android will no longer show a dialog for: she said no twice, so the next tap opens settings.
    var blocked by remember { mutableStateOf(emptySet<Perm>()) }
    var asking by remember { mutableStateOf<Perm?>(null) }

    fun openAppDetails() {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        }
    }

    fun openSettings(action: String, withPackage: Boolean) {
        val intent = if (withPackage) Intent(action, Uri.parse("package:${ctx.packageName}")) else Intent(action)
        if (runCatching { ctx.startActivity(intent) }.isFailure) openAppDetails()
    }

    fun noteResult(perm: Perm, ok: Boolean, androidName: String) {
        if (!ok && activity?.shouldShowRequestPermissionRationale(androidName) == false) blocked = blocked + perm
        else if (ok) blocked = blocked - perm
        onRefresh()
    }

    val oneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val perm = asking
        asking = null
        if (perm != null) noteResult(perm, ok, androidPermissionOf(perm)) else onRefresh()
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        asking = null
        noteResult(Perm.LOCATION, res.values.any { it }, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun ask(perm: Perm) {
        if (perm in blocked) { openAppDetails(); return }
        when (perm) {
            Perm.LOCATION -> {
                asking = perm
                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            Perm.LOCATION_ALWAYS -> openAppDetails()
            Perm.SMS, Perm.NOTIFICATIONS, Perm.MICROPHONE -> {
                asking = perm
                oneLauncher.launch(androidPermissionOf(perm))
            }
            Perm.LOCK_SCREEN -> openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, true)
            Perm.BACKGROUND -> openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true)
            Perm.RIDES -> openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, false)
        }
    }

    val visible = visiblePerms()
    Column(Modifier.fillMaxWidth()) {
        Text("Let Saathi watch your trips", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Android asks once for each. Saathi uses them only on trips and alerts.",
            style = MaterialTheme.typography.bodyLarge, color = c.fg2, modifier = Modifier.padding(top = 8.dp),
        )

        Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            visible.filter { it.required }.forEach { perm ->
                Column(Modifier.fillMaxWidth().bringIntoViewRequester(requesters.getValue(perm))) {
                    PermCard(perm, granted[perm] == true, enabled = perm != Perm.LOCATION_ALWAYS || granted[Perm.LOCATION] == true) { ask(perm) }
                    if (problemPerm == perm && granted[perm] != true) ProblemText("Needed so alerts can reach your contacts.")
                }
            }
            Text(
                "Optional", style = MaterialTheme.typography.labelLarge, color = c.fg2,
                modifier = Modifier.padding(top = 8.dp),
            )
            visible.filter { !it.required }.forEach { perm ->
                Column(Modifier.fillMaxWidth().bringIntoViewRequester(requesters.getValue(perm))) {
                    PermCard(perm, granted[perm] == true, enabled = true) { ask(perm) }
                }
            }
        }
    }
}

private fun androidPermissionOf(perm: Perm): String = when (perm) {
    Perm.SMS -> Manifest.permission.SEND_SMS
    Perm.NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
    Perm.MICROPHONE -> Manifest.permission.RECORD_AUDIO
    else -> error("$perm is not asked through a permission dialog")
}

@Composable
private fun PermCard(perm: Perm, isGranted: Boolean, enabled: Boolean, onAllow: () -> Unit) {
    val c = Saathi.colors
    val shape = RoundedCornerShape(20.dp)
    val canAct = !isGranted && enabled
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .then(
                if (canAct) Modifier.clickable(onClickLabel = "Allow ${perm.title}", role = Role.Button, onClick = onAllow)
                else Modifier,
            )
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .heightIn(min = 72.dp)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            perm.icon, null,
            Modifier.size(24.dp).alpha(if (enabled) 1f else 0.5f),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f).alpha(if (enabled) 1f else 0.5f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(perm.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall)
                if (!perm.required) {
                    Spacer(Modifier.size(8.dp))
                    Text("Optional", style = MaterialTheme.typography.labelSmall, color = c.fg2)
                }
            }
            Text(perm.why, style = MaterialTheme.typography.bodySmall, color = c.fg2)
            if (perm == Perm.LOCATION_ALWAYS && !isGranted) {
                Text(
                    if (enabled) "Tap Allow, then Permissions › Location › Allow all the time." else "After the first one",
                    style = MaterialTheme.typography.bodySmall, color = c.fg2,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        if (isGranted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = c.ok)
                Spacer(Modifier.size(6.dp))
                Text("Allowed", style = MaterialTheme.typography.labelLarge, color = c.ok)
            }
        } else {
            FilledTonalButton(onClick = onAllow, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text("Allow") }
        }
    }
}
