package app.saathi.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.saathi.BuildConfig
import app.saathi.R
import app.saathi.alert.AlertManager
import app.saathi.core.SetupProblem
import app.saathi.data.Api.Release
import app.saathi.data.AppUpdateManager
import app.saathi.data.Settings
import app.saathi.data.TelemetryManager
import app.saathi.trigger.HelpTileService
import app.saathi.ui.setup.ContactsEditor
import app.saathi.ui.setup.PermissionList
import app.saathi.ui.setup.PlacesEditor
import app.saathi.ui.setup.ProblemText
import app.saathi.ui.setup.rememberLivePerms
import app.saathi.ui.theme.Saathi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PHRASES = listOf(
    "touched" to "\"This man touched me. He's still here. Please help me.\"",
    "following" to "\"This man is following me. Please help me.\"",
    "help" to "\"I need help. Please stay with me and call for help.\"",
)

private val PRESS_CHOICES = listOf(3 to "3 presses", 4 to "4", 6 to "6", 0 to "Off")

/** Everything she set up, editable. Every change is saved straight away through [onChange]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: Settings, onChange: (Settings) -> Unit) {
    val c = Saathi.colors
    val ctx = LocalContext.current

    // Her name: the field shows what she typed, but only a non-blank name is ever saved.
    var name by rememberSaveable { mutableStateOf(settings.name) }

    // The add-a-contact form lives here so it survives a rotation, like it does in setup.
    var formOpen by rememberSaveable { mutableStateOf(false) }
    var formName by rememberSaveable { mutableStateOf("") }
    var formPhone by rememberSaveable { mutableStateOf("") }
    var problem by remember { mutableStateOf<SetupProblem?>(null) }
    var problemNonce by remember { mutableIntStateOf(0) }

    val live = rememberLivePerms()

    var practiceHint by remember { mutableStateOf(false) }
    LaunchedEffect(practiceHint) {
        if (practiceHint) { delay(6000); practiceHint = false }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 8.dp))

        SettingsSection("You", "Shown in alerts, like \"${name.trim().ifBlank { "Priya" }} hasn't answered a check-in.\"") {
            OutlinedTextField(
                name,
                {
                    name = it
                    val saved = it.trim()
                    if (saved.isNotEmpty() && saved != settings.name) onChange(settings.copy(name = saved))
                },
                Modifier.fillMaxWidth(),
                label = { Text("Your first name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                supportingText = if (name.isBlank()) {
                    { ProblemText("Your name is shown in alerts.") }
                } else null,
            )
        }

        SettingsSection("People to text", "If you don't answer a check-in, Saathi texts these people from your own SIM.") {
            ContactsEditor(
                contacts = settings.contacts, onContactsChange = { onChange(settings.copy(contacts = it)) },
                formOpen = formOpen, onFormOpenChange = { formOpen = it },
                formName = formName, onFormNameChange = { formName = it },
                formPhone = formPhone, onFormPhoneChange = { formPhone = it },
                problem = problem, problemNonce = problemNonce,
                onProblemChange = { p -> problem = p; if (p != null) problemNonce++ },
                emptyProblem = "Add at least one person, or alerts can't go anywhere.",
            )
        }

        SettingsSection("Places you go often", "Home and work, say. Saathi uses them to notice a detour or a late arrival.") {
            PlacesEditor(settings.places, { onChange(settings.copy(places = it)) })
        }

        SettingsSection("Help options") {
            Text("Speak for me says", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PHRASES.forEach { (key, text) ->
                    FilterChip(
                        selected = settings.phraseKey == key,
                        onClick = { if (settings.phraseKey != key) onChange(settings.copy(phraseKey = key)) },
                        label = { Text(text, Modifier.padding(vertical = 8.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Text("Quick Settings tile", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp))
            Text(
                "Pull down from the top of the screen, even when locked, and tap Saathi help. You get 5 seconds to tap again if it was a mistake.",
                style = MaterialTheme.typography.bodyMedium, color = c.fg2, modifier = Modifier.padding(top = 8.dp),
            )
            if (Build.VERSION.SDK_INT >= 33) {
                FilledTonalButton(
                    onClick = {
                        ctx.getSystemService(StatusBarManager::class.java)?.requestAddTileService(
                            ComponentName(ctx, HelpTileService::class.java), "Saathi help",
                            Icon.createWithResource(ctx, R.drawable.ic_tile_help), ctx.mainExecutor,
                        ) { }
                    },
                    modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                ) { Text("Add to Quick Settings") }
            } else {
                Text(
                    "Edit your Quick Settings (the pencil icon) and drag Saathi help in.",
                    style = MaterialTheme.typography.bodyMedium, color = c.fg2, modifier = Modifier.padding(top = 8.dp),
                )
            }

            Text("Power button", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp))
            FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESS_CHOICES.forEach { (n, label) ->
                    FilterChip(
                        selected = settings.powerPresses == n,
                        onClick = { if (settings.powerPresses != n) onChange(settings.copy(powerPresses = n)) },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                "Many phones open the camera on a double press, and Android's own Emergency SOS uses 5 presses. Pick a count that doesn't clash with yours. If presses open your camera, turn this off and use the Quick Settings tile.",
                style = MaterialTheme.typography.bodyMedium, color = c.fg2, modifier = Modifier.padding(top = 8.dp),
            )
        }

        SettingsSection("Community sharing") {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = settings.communityConsent, role = Role.Switch,
                    onValueChange = { onChange(settings.copy(communityConsent = it, consentAsked = true)) },
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Share anonymously: count my rides by vehicle plate and add my reports to the community map. Never my name or number.",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = settings.communityConsent, onCheckedChange = null)
            }
        }

        SettingsSection("Help improve Saathi") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = settings.crashReports, role = Role.Switch,
                        onValueChange = { onChange(settings.copy(crashReports = it)) },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Send crash reports", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "If Saathi crashes, send what went wrong. Numbers, names and places are removed first.",
                            style = MaterialTheme.typography.bodyMedium, color = c.fg2,
                        )
                    }
                    Switch(checked = settings.crashReports, onCheckedChange = null)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = settings.usageStats, role = Role.Switch,
                        onValueChange = {
                            if (!it) TelemetryManager.clearQueue(ctx) // nothing queued stays behind once she says no
                            onChange(settings.copy(usageStats = it))
                        },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Send usage stats", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Which features get used, rounded to the hour. Never where you go or who you text.",
                            style = MaterialTheme.typography.bodyMedium, color = c.fg2,
                        )
                    }
                    Switch(checked = settings.usageStats, onCheckedChange = null)
                }
            }
        }

        SettingsSection("Permissions", "Tap Allow on anything that's missing.") {
            PermissionList(live.granted, live.refresh)
        }

        SettingsSection("Practice") {
            FilledTonalButton(
                onClick = { AlertManager.startPractice(5_000); practiceHint = true },
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(20.dp),
            ) { Text("Practise a check-in", style = MaterialTheme.typography.titleMedium) }
            if (practiceHint) {
                Text(
                    "Lock your phone. Saathi rings in 5 seconds.",
                    style = MaterialTheme.typography.bodyMedium, color = c.fg2, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        SettingsSection("App") { AppSection() }

        Spacer(Modifier.height(8.dp))
    }
}

/** Version, and a manual update check that opens the same dialog as the automatic one. */
@Composable
private fun AppSection() {
    val c = Saathi.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var offered by remember { mutableStateOf<Release?>(null) }
    // null: not checked yet; true: up to date; false: the check didn't work.
    var upToDate by remember { mutableStateOf<Boolean?>(null) }

    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = c.fg2)
    FilledTonalButton(
        onClick = {
            checking = true
            upToDate = null
            scope.launch {
                AppUpdateManager.checkUpdate(ctx, force = true)
                    .onSuccess { rel -> offered = rel; upToDate = rel == null }
                    .onFailure { upToDate = false }
                checking = false
            }
        },
        enabled = !checking,
        modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
    ) { Text(if (checking) "Checking…" else "Check for updates") }
    when (upToDate) {
        true -> Text("You're on the latest version.", style = MaterialTheme.typography.bodyMedium, color = c.fg2,
            modifier = Modifier.padding(top = 8.dp))
        false -> ProblemText("Couldn't check for updates. Try again later.", Modifier.padding(top = 4.dp))
        null -> Unit
    }
    offered?.let { UpdateDialog(it, onDismiss = { offered = null }) }
}

/** A titled block: a heading, an optional one-liner in fg2, then the controls 12dp below. */
@Composable
private fun SettingsSection(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = Saathi.colors.fg2, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}
