package app.saathi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.saathi.alert.Alarm
import app.saathi.alert.AlertManager
import androidx.compose.ui.res.stringResource
import app.saathi.R
import app.saathi.core.Plate
import app.saathi.core.Reason
import app.saathi.core.ScanAttempt
import app.saathi.data.TelemetryManager
import app.saathi.trip.TripService
import app.saathi.trip.TripState
import app.saathi.ui.theme.PlateText
import app.saathi.ui.theme.Saathi
import app.saathi.ui.theme.SaathiTheme
import java.io.File

/**
 * Compares the plate on the vehicle in front of her with the one the ride app booked. In record mode
 * (a hailed auto, nothing booked) it only reads the plate and hands it back as [EXTRA_PLATE_RESULT].
 * The camera is live: the plate is read from the picture as she aims, and nothing is ever saved.
 */
class PlateCheckActivity : ComponentActivity() {
    companion object {
        const val EXTRA_BOOKED = "booked"
        const val EXTRA_RECORD = "record"
        const val EXTRA_PLATE_RESULT = "plate_result"
    }

    /** This go at scanning, kept only to count it for `plate_scanned`. */
    private val scan = ScanAttempt()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AlertManager.init(this)
        // Older versions took a photo here and could leave one in the cache if killed. Nothing is saved now; clear any.
        File(cacheDir, "plates").deleteRecursively()
        val record = intent.getBooleanExtra(EXTRA_RECORD, false)
        val booked = if (record) null else intent.getStringExtra(EXTRA_BOOKED) ?: TripState.now.plate
        setContent { SaathiTheme { PlateCheck(booked, record) { finish() } } }
    }

    override fun onDestroy() {
        // Left (or typed it in) after a real go at scanning: a scan that failed. Turning the phone over doesn't count.
        if (isFinishing) gaveUp()
        super.onDestroy()
    }

    private fun track(success: Boolean) = TelemetryManager.track(this, "plate_scanned", mapOf("success" to success))

    private fun gaveUp() { if (scan.gaveUp()) track(false) }

    private fun lockedOn() {
        if (scan.lockedOn()) track(true)
        Alarm(this).buzzOnce()
    }

    private fun hasCamera() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    }

    @Composable
    private fun PlateCheck(booked: String?, record: Boolean, done: () -> Unit) {
        val c = Saathi.colors
        var seen by remember { mutableStateOf<String?>(null) }
        var typed by remember { mutableStateOf("") }
        var cameraFailed by remember { mutableStateOf(false) }

        // The camera is asked for here, as the screen opens. A refusal never blocks: the plate can always be typed.
        var granted by remember { mutableStateOf(hasCamera()) }
        var asked by rememberSaveable { mutableStateOf(false) }
        // Refused so firmly that the system won't ask again; now only Settings can turn the camera on.
        var blocked by rememberSaveable { mutableStateOf(false) }
        val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok: Boolean ->
            granted = ok
            blocked = !ok && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        }
        LaunchedEffect(Unit) { if (!granted && !asked) { asked = true; askCamera.launch(Manifest.permission.CAMERA) } }
        // Back from Settings with the camera turned on: scanning starts by itself.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasCamera(); if (granted) blocked = false }

        val match = seen?.let { s -> booked?.let { Plate.matches(it, s) } }
        LaunchedEffect(match, seen) {
            when (match) {
                true -> TripState.update { it.copy(plateCheck = "match: $seen") }
                false -> TripState.update { it.copy(plateCheck = "mismatch: saw $seen, booked $booked") }
                null -> {}
            }
        }

        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding().navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(stringResource(when {
                record -> if (seen == null) R.string.plate_record_title else R.string.plate_record_title_read
                match == null -> R.string.plate_title
                match -> R.string.plate_title_match
                else -> R.string.plate_title_mismatch
            }), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
            Text(stringResource(when {
                record -> if (seen == null) R.string.plate_record_body else R.string.plate_record_body_read
                match == null -> R.string.plate_body
                match -> R.string.plate_body_match
                else -> R.string.plate_body_mismatch
            }), style = MaterialTheme.typography.bodyLarge, color = c.fg2, modifier = Modifier.padding(horizontal = 8.dp))

            // Scanning: the camera takes the middle. Once there is a plate, the comparison sits in the optical middle instead.
            val scanning = seen == null
            if (scanning) Box(Modifier.padding(top = 12.dp).weight(1f).fillMaxWidth()) {
                if (granted && !cameraFailed) PlateScanner(
                    onFrame = scan::frame,
                    onLocked = { plate -> lockedOn(); seen = plate },
                    onUnavailable = { cameraFailed = true },
                    Modifier.fillMaxSize(),
                ) else CameraHelp(
                    failed = cameraFailed, blocked = blocked,
                    onAllow = { if (blocked) openAppSettings() else askCamera.launch(Manifest.permission.CAMERA) },
                )
            } else Spacer(Modifier.weight(0.8f))
            if (!record || seen != null) Column(
                Modifier.padding(top = if (scanning) 12.dp else 20.dp).fillMaxWidth().clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow).border(1.dp, c.hair, MaterialTheme.shapes.large),
            ) {
                seen?.let { PlateRow(Icons.Rounded.PhotoCamera, stringResource(R.string.plate_in_front), it, ring = if (match == false) c.help else null) }
                // Nothing was booked for a hailed vehicle, so there is no second row to compare with.
                if (!record) {
                    if (seen != null) HorizontalDivider(color = c.hair)
                    PlateRow(Icons.Rounded.ReceiptLong, stringResource(R.string.plate_booked), booked, ring = null, ok = match == true)
                }
            }

            if (scanning) Spacer(Modifier.height(12.dp)) else Spacer(Modifier.weight(1f))
            val recorded = seen
            if (record && recorded != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { setResult(RESULT_OK, Intent().putExtra(EXTRA_PLATE_RESULT, recorded)); done() },
                    Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(24.dp),
                ) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.plate_use), style = MaterialTheme.typography.titleMedium) }
                // Back to the live camera.
                TextButton(onClick = { seen = null; typed = ""; cameraFailed = false }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.plate_retake), style = MaterialTheme.typography.titleMedium)
                }
            } else when (match) {
                // Always here while scanning, so a plate can be typed whatever the camera does.
                null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(typed, { typed = it.uppercase() }, Modifier.weight(1f), singleLine = true,
                        label = { Text(stringResource(R.string.plate_type_label)) })
                    FilledTonalButton(onClick = { Plate.normalise(typed).takeIf { it.isNotEmpty() }?.let { gaveUp(); seen = it } }, Modifier.height(56.dp),
                        shape = RoundedCornerShape(16.dp)) { Text(stringResource(if (record) R.string.setup_continue else R.string.plate_compare)) }
                }
                true -> Button(onClick = done, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(24.dp)) { Text(stringResource(R.string.action_done)) }
                false -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { startForegroundService(TripService.intent(this@PlateCheckActivity, TripService.ACTION_STOP_TRIP)); done() },
                        Modifier.fillMaxWidth().height(72.dp), shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.help, contentColor = Color.White),
                    ) { Icon(Icons.Rounded.Block, null); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.plate_dont_get_in), style = MaterialTheme.typography.headlineSmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { AlertManager.raise(Reason.PLATE_MISMATCH); done() }, Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(20.dp)) { Icon(Icons.Rounded.Sms, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.plate_tell_contacts)) }
                        FilledTonalButton(onClick = done, Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(20.dp)) { Text(stringResource(R.string.plate_its_fine)) }
                    }
                }
            }
        }
    }
}

/** Where the camera would be, when it can't be: why, in alum (nothing has been sent, nothing is wrong), and the way to allow it. */
@Composable
private fun CameraHelp(failed: Boolean, blocked: Boolean, onAllow: () -> Unit) {
    val c = Saathi.colors
    Column(
        Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, c.hair, MaterialTheme.shapes.large).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Rounded.NoPhotography, null, tint = c.alum, modifier = Modifier.size(36.dp))
        Text(
            stringResource(when { failed -> R.string.plate_camera_failed; blocked -> R.string.plate_camera_blocked; else -> R.string.plate_camera_off }),
            style = MaterialTheme.typography.bodyLarge, color = c.alum, textAlign = TextAlign.Center,
        )
        if (!failed) Button(onClick = onAllow, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(24.dp)) {
            Text(stringResource(if (blocked) R.string.plate_camera_settings else R.string.plate_camera_allow), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PlateRow(icon: ImageVector, label: String, plate: String?, ring: Color?, ok: Boolean = false) {
    val c = Saathi.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.fg2, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.fg2, modifier = Modifier.padding(start = 10.dp).weight(1f))
        if (ok) Box(Modifier.padding(end = 10.dp).size(24.dp).background(c.ok, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        if (plate != null) PlateText(plate, ring = ring, big = true) else Text(stringResource(R.string.plate_none_shown), style = MaterialTheme.typography.bodyMedium)
    }
}
