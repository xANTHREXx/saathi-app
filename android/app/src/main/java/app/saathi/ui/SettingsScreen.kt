package app.saathi.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import app.saathi.ui.setup.ContactsEditor
import app.saathi.ui.setup.PermissionList
import app.saathi.ui.setup.PlacesEditor
import app.saathi.ui.setup.ProblemText
import app.saathi.ui.setup.rememberLivePerms
import app.saathi.ui.theme.Saathi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PHRASES = listOf(
    "touched" to R.string.settings_phrase_touched,
    "following" to R.string.settings_phrase_following,
    "help" to R.string.settings_phrase_help,
)

private val PRESS_CHOICES = listOf(3, 4, 6, 0)

/** Everything she set up, editable. Every change is saved straight away through [onChange]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: Settings, onChange: (Settings) -> Unit) {
    val c = Saathi.colors
    val ctx = LocalContext.current
    // The tile dialog can answer after a recomposition; save onto what is current, not what was captured.
    val latest by rememberUpdatedState(settings)

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
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 8.dp))

        SettingsSection(
            stringResource(R.string.settings_you),
            stringResource(R.string.settings_you_hint, name.trim().ifBlank { "Priya" }),
        ) {
            OutlinedTextField(
                name,
                {
                    name = it
                    val saved = it.trim()
                    if (saved.isNotEmpty() && saved != settings.name) onChange(settings.copy(name = saved))
                },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                supportingText = if (name.isBlank()) {
                    { ProblemText(stringResource(R.string.settings_name_problem)) }
                } else null,
            )
        }

        SettingsSection(stringResource(R.string.settings_language)) { LanguageRow() }

        SettingsSection(stringResource(R.string.settings_people), stringResource(R.string.contacts_hint)) {
            ContactsEditor(
                contacts = settings.contacts, onContactsChange = { onChange(settings.copy(contacts = it)) },
                formOpen = formOpen, onFormOpenChange = { formOpen = it },
                formName = formName, onFormNameChange = { formName = it },
                formPhone = formPhone, onFormPhoneChange = { formPhone = it },
                problem = problem, problemNonce = problemNonce,
                onProblemChange = { p -> problem = p; if (p != null) problemNonce++ },
                emptyProblem = stringResource(R.string.contacts_empty_problem),
            )
            if (settings.contacts.isNotEmpty()) TextButton(
                onClick = {
                    if (writeAboutSaathi(ctx, settings.contacts) && !settings.contactsToldAsked) {
                        onChange(settings.copy(contactsToldAsked = true))
                    }
                },
                Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.settings_write_contacts)) }
        }

        SettingsSection(stringResource(R.string.places_title), stringResource(R.string.settings_places_hint)) {
            PlacesEditor(settings.places, { onChange(settings.copy(places = it)) })
        }

        SettingsSection(stringResource(R.string.settings_help_options)) {
            Text(stringResource(R.string.settings_speak_says), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PHRASES.forEach { (key, textRes) ->
                    FilterChip(
                        selected = settings.phraseKey == key,
                        onClick = { if (settings.phraseKey != key) onChange(settings.copy(phraseKey = key)) },
                        label = { Text(stringResource(textRes), Modifier.padding(vertical = 8.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Text(stringResource(R.string.settings_tile), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp))
            Text(
                stringResource(R.string.settings_tile_body),
                style = MaterialTheme.typography.bodyMedium, color = c.fg2, modifier = Modifier.padding(top = 8.dp),
            )
            if (Build.VERSION.SDK_INT >= 33) {
                FilledTonalButton(
                    // Home's "Two more things" card reads the same flag, so adding it here ticks it off there.
                    onClick = { requestHelpTile(ctx) { if (tileRequestDone(it)) onChange(latest.copy(tileAsked = true)) } },
                    modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.settings_tile_add)) }
            } else {
                Text(
                    stringResource(R.string.settings_tile_manual),
                    style = MaterialTheme.typography.bodyMedium, color = c.fg2, modifier = Modifier.padding(top = 8.dp),
                )
            }

            Text(stringResource(R.string.settings_power), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp))
            FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESS_CHOICES.forEach { n ->
                    FilterChip(
                        selected = settings.powerPresses == n,
                        onClick = { if (settings.powerPresses != n) onChange(settings.copy(powerPresses = n)) },
                        label = {
                            Text(when (n) {
                                3 -> pluralStringResource(R.plurals.settings_presses, n, n)
                                0 -> stringResource(R.string.settings_off)
                                else -> "$n"
                            })
                        },
                    )
                }
            }
            Text(
                stringResource(R.string.settings_power_hint),
                style = MaterialTheme.typography.bodyMedium, color = c.fg2, modifier = Modifier.padding(top = 8.dp),
            )
        }

        SettingsSection(stringResource(R.string.settings_community)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = settings.communityConsent, role = Role.Switch,
                    onValueChange = { onChange(settings.copy(communityConsent = it, consentAsked = true)) },
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.settings_community_body),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = settings.communityConsent, onCheckedChange = null)
            }
        }

        SettingsSection(stringResource(R.string.settings_improve)) {
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
                        Text(stringResource(R.string.settings_crash_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.settings_crash_body),
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
                        Text(stringResource(R.string.settings_usage_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.settings_usage_body),
                            style = MaterialTheme.typography.bodyMedium, color = c.fg2,
                        )
                    }
                    Switch(checked = settings.usageStats, onCheckedChange = null)
                }
            }
        }

        SettingsSection(stringResource(R.string.settings_permissions), stringResource(R.string.settings_permissions_hint)) {
            PermissionList(live.granted, live.refresh)
        }

        SettingsSection(stringResource(R.string.settings_practice)) {
            FilledTonalButton(
                onClick = { AlertManager.startPractice(5_000); practiceHint = true },
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(20.dp),
            ) { Text(stringResource(R.string.settings_practice_button), style = MaterialTheme.typography.titleMedium) }
            if (practiceHint) {
                Text(
                    stringResource(R.string.settings_practice_hint),
                    style = MaterialTheme.typography.bodyMedium, color = c.fg2, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        SettingsSection(stringResource(R.string.settings_app)) { AppSection() }

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

    Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = c.fg2)
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
    ) { Text(stringResource(if (checking) R.string.settings_checking else R.string.settings_check_updates)) }
    when (upToDate) {
        true -> Text(stringResource(R.string.settings_up_to_date), style = MaterialTheme.typography.bodyMedium, color = c.fg2,
            modifier = Modifier.padding(top = 8.dp))
        false -> ProblemText(stringResource(R.string.settings_update_failed), Modifier.padding(top = 4.dp))
        null -> Unit
    }
    offered?.let { UpdateDialog(it, onDismiss = { offered = null }) }
}

/**
 * The app's language. From Android 13 the system keeps a language for each app, so this opens that screen.
 * Before that there is nothing to switch: Saathi follows the phone's language.
 */
@Composable
private fun LanguageRow() {
    val ctx = LocalContext.current
    val c = Saathi.colors
    val current = stringResource(R.string.lang_name)
    if (Build.VERSION.SDK_INT >= 33) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) {
                    val own = Uri.parse("package:${ctx.packageName}")
                    runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS, own)) }
                        .onFailure {
                            runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, own)) }
                        }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.fg2)
        }
    } else {
        Text(current, style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.settings_language_follow),
            style = MaterialTheme.typography.bodyMedium, color = c.fg2, modifier = Modifier.padding(top = 4.dp),
        )
    }
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
