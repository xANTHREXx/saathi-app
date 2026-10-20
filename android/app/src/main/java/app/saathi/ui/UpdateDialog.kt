package app.saathi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.saathi.data.Api
import app.saathi.data.AppUpdateManager
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    release: Api.Release,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadFailed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (!release.isMandatory && !isDownloading) onDismiss()
        },
        title = {
            Text(text = "Update Available (v${release.versionName})", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                if (release.changelog.isNotBlank()) {
                    Text(text = release.changelog, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (isDownloading) {
                    Text(text = "Downloading update... ${(downloadProgress * 100).toInt()}%")
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (downloadFailed) {
                    Text(
                        text = "Download failed. Please check internet connection and try again.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(
                    onClick = {
                        isDownloading = true
                        downloadFailed = false
                        downloadProgress = 0f
                        scope.launch {
                            val apkFile = AppUpdateManager.downloadApk(ctx, release.apkUrl) { progress ->
                                downloadProgress = progress
                            }
                            isDownloading = false
                            if (apkFile != null) {
                                val intent = AppUpdateManager.createInstallIntent(ctx, apkFile)
                                ctx.startActivity(intent)
                                onDismiss()
                            } else {
                                downloadFailed = true
                            }
                        }
                    }
                ) {
                    Text(if (downloadFailed) "Retry" else "Update Now")
                }
            }
        },
        dismissButton = {
            if (!release.isMandatory && !isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        }
    )
}
