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
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.saathi.R
import app.saathi.core.Oem
import app.saathi.data.RunState
import app.saathi.trip.deviceOem
import app.saathi.trip.openBackgroundSettings
import androidx.compose.material3.TextButton
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.saathi.ui.theme.Saathi

/** Everything step 2 asks for, in the order she sees it. Required ones first, then the optional ones. */
enum class Perm(@StringRes val title: Int, @StringRes val why: Int, val icon: ImageVector, val required: Boolean) {
    LOCATION(R.string.perm_location, R.string.perm_location_why, Icons.Rounded.LocationOn, true),
    LOCATION_ALWAYS(R.string.perm_location_always, R.string.perm_location_always_why, Icons.Rounded.MyLocation, true),
    SMS(R.string.perm_sms, R.string.perm_sms_why, Icons.Rounded.Sms, true),
    NOTIFICATIONS(R.string.perm_notifications, R.string.perm_notifications_why, Icons.Rounded.Notifications, true),
    LOCK_SCREEN(R.string.perm_lock_screen, R.string.perm_lock_screen_why, Icons.Rounded.Lock, true),
    BACKGROUND(R.string.perm_background, R.string.perm_background_why, Icons.Rounded.BatteryChargingFull, true),
    /** Her phone maker's own switch. Android can't tell us it's on, so she says so. Drawn by [KeepRunningCard], which holds its steps. */
    KEEP_RUNNING(R.string.perm_keep_title, R.string.perm_keep_why, Icons.Rounded.BatteryChargingFull, true),
    RIDES(R.string.perm_rides, R.string.perm_rides_why, Icons.Rounded.LocalTaxi, false),
    MICROPHONE(R.string.perm_microphone, R.string.perm_microphone_why, Icons.Rounded.Mic, false),
}

/** The permissions worth showing on this Android version. */
internal fun visiblePerms(sdk: Int = Build.VERSION.SDK_INT, oem: Oem = deviceOem()): List<Perm> = Perm.values().filter {
    when (it) {
        Perm.NOTIFICATIONS -> sdk >= 33
        Perm.LOCK_SCREEN -> sdk >= 34
        Perm.KEEP_RUNNING -> oem.needsCard
        else -> true
    }
}

/** The first required permission still missing, in display order, or null when she is done. */
internal fun firstMissingRequired(granted: Map<Perm, Boolean>, sdk: Int = Build.VERSION.SDK_INT, oem: Oem = deviceOem()): Perm? =
    visiblePerms(sdk, oem).firstOrNull { it.required && granted[it] != true }

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
        Perm.KEEP_RUNNING -> RunState.backgroundDone(ctx) // she tells us: no API says whether the maker's switch is on
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

/** Step 2: the heading, then [PermissionList]. State is read by [rememberLivePerms] in [SetupFlow]. */
@Composable
internal fun PermissionsStep(
    granted: Map<Perm, Boolean>,
    onRefresh: () -> Unit,
    problemPerm: Perm?,
    problemNonce: Int,
) {
    val c = Saathi.colors
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.perm_heading), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.perm_subheading),
            style = MaterialTheme.typography.bodyLarge, color = c.fg2, modifier = Modifier.padding(top = 8.dp),
        )
        PermissionList(granted, onRefresh, problemPerm, problemNonce, Modifier.padding(top = 24.dp))
    }
}

/**
 * Permissions asked in place, with live state, without any heading. Used by the setup step and by Settings.
 * State is read by [rememberLivePerms]; this only draws it and launches each permission's dialog or settings screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PermissionList(
    granted: Map<Perm, Boolean>,
    onRefresh: () -> Unit,
    problemPerm: Perm? = null,
    problemNonce: Int = 0,
    modifier: Modifier = Modifier,
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
            Perm.KEEP_RUNNING -> openBackgroundSettings(ctx)
            Perm.RIDES -> openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, false)
        }
    }

    val visible = visiblePerms()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.filter { it.required }.forEach { perm ->
            Column(Modifier.fillMaxWidth().bringIntoViewRequester(requesters.getValue(perm))) {
                if (perm == Perm.KEEP_RUNNING) {
                    KeepRunningCard(deviceOem(), granted[perm] == true, onOpen = { ask(perm) }, onDone = {
                        RunState.setBackgroundDone(ctx, true)
                        onRefresh()
                    })
                } else {
                    PermCard(perm, granted[perm] == true, enabled = perm != Perm.LOCATION_ALWAYS || granted[Perm.LOCATION] == true) { ask(perm) }
                }
                if (problemPerm == perm && granted[perm] != true) {
                    ProblemText(
                        if (perm == Perm.KEEP_RUNNING) stringResource(R.string.perm_keep_needed)
                        else stringResource(R.string.perm_needed),
                    )
                }
            }
        }
        // Each optional card says so itself, after its title, so there is no separate heading to read around.
        visible.filter { !it.required }.forEach { perm ->
            Column(Modifier.fillMaxWidth().bringIntoViewRequester(requesters.getValue(perm))) {
                PermCard(perm, granted[perm] == true, enabled = true) { ask(perm) }
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

/** The maker's steps, in plain words, one string each so a translation can phrase them freely. */
internal fun oemSteps(oem: Oem): List<Int> = when (oem) {
    Oem.XIAOMI -> listOf(R.string.oem_xiaomi_1, R.string.oem_xiaomi_2, R.string.oem_xiaomi_3)
    Oem.VIVO -> listOf(R.string.oem_vivo_1, R.string.oem_vivo_2, R.string.oem_vivo_3)
    Oem.OPPO, Oem.REALME -> listOf(R.string.oem_oppo_1, R.string.oem_oppo_2, R.string.oem_oppo_3)
    Oem.ONEPLUS -> listOf(R.string.oem_oneplus_1, R.string.oem_oneplus_2, R.string.oem_oneplus_3)
    Oem.SAMSUNG -> listOf(R.string.oem_samsung_1, R.string.oem_samsung_2, R.string.oem_samsung_3)
    Oem.HUAWEI -> listOf(R.string.oem_huawei_1, R.string.oem_huawei_2, R.string.oem_huawei_3)
    Oem.OTHER -> emptyList()
}

/**
 * "Keep Saathi running": the steps for her phone maker's own background switch. Android can't say
 * whether she did it, so the button that settles it is hers to press: Done.
 */
@Composable
private fun KeepRunningCard(oem: Oem, isDone: Boolean, onOpen: () -> Unit, onDone: () -> Unit) {
    val c = Saathi.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.BatteryChargingFull, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.perm_keep_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.perm_keep_why), style = MaterialTheme.typography.bodySmall, color = c.fg2)
            }
            if (isDone) {
                Spacer(Modifier.size(8.dp))
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = c.ok)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.ready_card_done), style = MaterialTheme.typography.labelLarge, color = c.ok)
            }
        }
        if (!isDone) {
            Column(Modifier.padding(start = 36.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                oemSteps(oem).forEachIndexed { i, step ->
                    Text("${i + 1}. ${stringResource(step)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.padding(start = 36.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.perm_keep_open)) }
                TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.ready_card_done)) }
            }
        }
    }
}

@Composable
private fun PermCard(perm: Perm, isGranted: Boolean, enabled: Boolean, onAllow: () -> Unit) {
    val c = Saathi.colors
    val shape = RoundedCornerShape(20.dp)
    val canAct = !isGranted && enabled
    val title = stringResource(perm.title)
    val allowLabel = stringResource(R.string.perm_allow_label, title)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .then(
                if (canAct) Modifier.clickable(onClickLabel = allowLabel, role = Role.Button, onClick = onAllow)
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
            // "Optional" follows the title in the same line of text, so a long title wraps with it, not around it.
            val optional = stringResource(R.string.perm_optional)
            val optionalStyle = MaterialTheme.typography.labelSmall.toSpanStyle().copy(color = c.fg2)
            Text(
                if (perm.required) AnnotatedString(title) else buildAnnotatedString {
                    append(title)
                    append("  ")
                    withStyle(optionalStyle) { append(optional) }
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(stringResource(perm.why), style = MaterialTheme.typography.bodySmall, color = c.fg2)
            if (perm == Perm.LOCATION_ALWAYS && !isGranted) {
                Text(
                    stringResource(if (enabled) R.string.perm_always_steps else R.string.perm_after_first),
                    style = MaterialTheme.typography.bodySmall, color = c.fg2,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        if (isGranted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = c.ok)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.perm_allowed), style = MaterialTheme.typography.labelLarge, color = c.ok)
            }
        } else {
            FilledTonalButton(onClick = onAllow, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.perm_allow)) }
        }
    }
}
