package app.saathi.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.saathi.R
import app.saathi.data.Api
import app.saathi.data.AppUpdateManager
import app.saathi.data.DownloadResult
import app.saathi.trip.TripState
import app.saathi.trip.UiState
import app.saathi.ui.setup.ProblemText
import app.saathi.ui.theme.Saathi
import kotlinx.coroutines.launch
import java.io.File

/** Help comes first: no update prompt while a trip, an alert, a check-in or a practice is running. */
internal fun updatePromptBlocked(ui: UiState): Boolean =
    ui.onTrip || ui.alertActive || ui.checkInEndsAtMs != null || ui.practice

/** Home-only: checks for an update when Home is shown (and again once a trip/alert ends), offers it if found. */
@Composable
fun UpdatePrompt() {
    val ctx = LocalContext.current
    val ui by TripState.ui.collectAsState()
    val blocked = updatePromptBlocked(ui)
    var release by remember { mutableStateOf<Api.Release?>(null) }

    LaunchedEffect(blocked) {
        if (!blocked) AppUpdateManager.checkUpdate(ctx).getOrNull()?.let { release = it }
    }

    release?.let { UpdateDialog(it, onDismiss = { release = null }) }
}

private enum class Phase { OFFER, DOWNLOADING, READY }

/**
 * Always dismissable, even for a mandatory release: [Api.Release.isMandatory] only changes the wording.
 * It also dismisses itself if help-related state starts; a finished, verified download stays on disk
 * and is picked up again from Settings.
 */
@Composable
fun UpdateDialog(
    release: Api.Release,
    onDismiss: () -> Unit
) {
    val ui by TripState.ui.collectAsState()
    val blocked = updatePromptBlocked(ui)
    LaunchedEffect(blocked) { if (blocked) onDismiss() }
    if (!blocked) UpdateDialogContent(release, onDismiss)
}

@Composable
private fun UpdateDialogContent(release: Api.Release, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.OFFER) }
    var progress by remember { mutableFloatStateOf(0f) }
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var canInstall by remember { mutableStateOf(ctx.packageManager.canRequestPackageInstalls()) }
    var askedPermission by remember { mutableStateOf(false) }
    val metered = remember { AppUpdateManager.isMetered(ctx) }

    // A verified file from an earlier attempt (she left, or the permission screen restarted the app).
    LaunchedEffect(release.apkSha256) {
        val earlier = AppUpdateManager.verifiedFile(ctx, release)
        if (earlier != null && phase == Phase.OFFER) { file = earlier; phase = Phase.READY }
    }

    // Back from the "install unknown apps" screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = ctx.packageManager.canRequestPackageInstalls() }

    fun install(apk: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            askedPermission = true
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
            }
        } else {
            runCatching { ctx.startActivity(AppUpdateManager.createInstallIntent(ctx, apk)) }
                .onFailure { error = ctx.getString(R.string.update_err_installer) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (release.isMandatory) stringResource(R.string.update_important) else stringResource(R.string.update_available, release.versionName),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                if (release.isMandatory) {
                    Text(stringResource(R.string.update_mandatory), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                if (release.changelog.isNotBlank()) {
                    Text(release.changelog, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                when (phase) {
                    Phase.DOWNLOADING -> {
                        Text(stringResource(R.string.update_downloading, (progress * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                    Phase.OFFER -> if (metered) {
                        Text(
                            stringResource(R.string.update_metered),
                            style = MaterialTheme.typography.bodyMedium, color = Saathi.colors.fg2,
                        )
                    }
                    Phase.READY -> if (askedPermission && !canInstall) {
                        ProblemText(stringResource(R.string.update_allow_install))
                    }
                }
                error?.let { ProblemText(it, Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            when (phase) {
                Phase.DOWNLOADING -> Unit
                Phase.OFFER -> Button(
                    onClick = {
                        phase = Phase.DOWNLOADING
                        error = null
                        progress = 0f
                        scope.launch {
                            when (val result = AppUpdateManager.downloadApk(ctx, release) { progress = it }) {
                                is DownloadResult.Verified -> {
                                    file = result.file
                                    phase = Phase.READY
                                    install(result.file)
                                }
                                DownloadResult.Mismatch -> {
                                    phase = Phase.OFFER
                                    error = ctx.getString(R.string.update_err_mismatch)
                                }
                                DownloadResult.Failed -> {
                                    phase = Phase.OFFER
                                    error = ctx.getString(R.string.update_err_failed)
                                }
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.update_button)) }
                Phase.READY -> Button(
                    onClick = { error = null; file?.let { install(it) } },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.update_install)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.update_later)) }
        }
    )
}
