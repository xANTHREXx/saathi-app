package app.saathi.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.saathi.R
import app.saathi.alert.SmsSender
import app.saathi.core.Contact
import app.saathi.core.introKey
import app.saathi.core.smsRecipients
import app.saathi.core.toIntroduce
import app.saathi.core.toldNames
import app.saathi.data.Settings
import app.saathi.trigger.HelpTileService
import app.saathi.ui.setup.Perm
import app.saathi.ui.setup.ProblemText
import app.saathi.ui.setup.granted
import app.saathi.ui.theme.Saathi
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.drawable.Icon as AndroidIcon

/** What one tap on "Text them" did. [opened] means her SMS app opened instead, so we can't know who got it. */
internal data class IntroOutcome(val sentKeys: List<String>, val sent: List<String>, val failed: List<String>, val opened: Boolean)

/**
 * The "I've added you to Saathi" text, from her SIM, to everyone not told yet: one tap, nothing to type.
 * One contact at a time, so each number is marked told only if its own text went. Without the SMS
 * permission it falls back to opening her SMS app with the note ready.
 */
internal suspend fun introduce(ctx: Context, settings: Settings): IntroOutcome {
    val to = toIntroduce(settings.contacts, settings.introduced)
    if (to.isEmpty()) return IntroOutcome(emptyList(), emptyList(), emptyList(), opened = false)
    if (!granted(ctx, Perm.SMS)) return IntroOutcome(emptyList(), emptyList(), emptyList(), opened = writeAboutSaathi(ctx, to))
    val body = ctx.getString(R.string.ready_told_message)
    return withContext(Dispatchers.IO) {
        val results = to.map { c -> c to SmsSender.send(ctx, listOf(c), body).sent.isNotEmpty() }
        IntroOutcome(
            sentKeys = results.filter { it.second }.map { introKey(it.first) },
            sent = results.filter { it.second }.map { it.first.name },
            failed = results.filterNot { it.second }.map { it.first.name },
            opened = false,
        )
    }
}

/** "Amma" / "Amma and Riya" / "Amma and 2 others"; "your contacts" when nobody has a name. */
@Composable
internal fun toldWho(contacts: List<Contact>): String {
    val names = toldNames(contacts)
    return when {
        names == null -> stringResource(R.string.ready_told_names_none)
        // Three or more carry no second name, so "others" is checked first; otherwise only the first name showed.
        names.others > 0 -> pluralStringResource(R.plurals.ready_told_names_more, names.others, names.first, names.others)
        names.second == null -> names.first
        else -> stringResource(R.string.ready_told_names_two, names.first, names.second)
    }
}

/**
 * One tap texts the contacts not told yet, then says who it reached. Saves onto [latest] (not a captured copy):
 * the send can finish after a recomposition.
 */
@Composable
internal fun IntroduceButton(latest: () -> Settings, onChange: (Settings) -> Unit, label: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val c = Saathi.colors
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<IntroOutcome?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalButton(
            onClick = {
                sending = true
                scope.launch {
                    val o = introduce(ctx, latest())
                    sending = false
                    outcome = o
                    val s = latest()
                    if (o.sentKeys.isNotEmpty() || o.opened) {
                        onChange(s.copy(introduced = (s.introduced + o.sentKeys).distinct(), contactsToldAsked = true))
                    }
                }
            },
            enabled = !sending,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(label) }
        outcome?.let { o ->
            if (o.sent.isNotEmpty()) Text(stringResource(R.string.intro_sent, ctx.namesText(o.sent)),
                style = MaterialTheme.typography.bodySmall, color = c.ok)
            if (o.failed.isNotEmpty()) ProblemText(stringResource(R.string.intro_failed, ctx.namesText(o.failed)))
        }
    }
}

/**
 * Opens her own SMS app with a note to her contacts, ready to send. Saathi sends nothing here: she does.
 * True if an app opened (we can't know whether she sent it).
 */
internal fun writeAboutSaathi(ctx: Context, contacts: List<Contact>): Boolean {
    val to = smsRecipients(contacts)
    if (to.isEmpty()) return false
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$to"))
        .putExtra("sms_body", ctx.getString(R.string.ready_told_message))
    return runCatching { ctx.startActivity(intent) }.isSuccess
}

/** Asks Android to add the Saathi help tile to Quick Settings. [onResult] gets the system's result code. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun requestHelpTile(ctx: Context, onResult: (Int) -> Unit) {
    ctx.getSystemService(StatusBarManager::class.java)?.requestAddTileService(
        ComponentName(ctx, HelpTileService::class.java), ctx.getString(R.string.help_tile_label),
        AndroidIcon.createWithResource(ctx, R.drawable.ic_tile_help), ctx.mainExecutor,
    ) { onResult(it) }
}

/** The tile is in Quick Settings now: she just added it, or it was already there. */
internal fun tileRequestDone(result: Int): Boolean =
    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
        result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED

/**
 * Home, once she is set up: tell your contacts, and add the help tile. Goes when both are done or she
 * says "Not now". A tonal card, not a plate: nothing here is urgent.
 */
@Composable
internal fun ReadyCard(settings: Settings, onChange: (Settings) -> Unit) {
    val ctx = LocalContext.current
    val c = Saathi.colors
    // A system dialog can outlast a recomposition; save onto what is current, not what was captured.
    val latest by rememberUpdatedState(settings)

    val pending = toIntroduce(settings.contacts, settings.introduced)
    val who = toldWho(pending.ifEmpty { settings.contacts })

    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow).border(1.dp, c.hair, MaterialTheme.shapes.large)
            .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ready_card_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onChange(latest.copy(readyCardDismissed = true)) }, Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.ready_card_not_now))
            }
        }
        Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadyRow(
                title = stringResource(R.string.ready_told_title, who), sub = stringResource(R.string.ready_told_sub),
                done = settings.contactsToldAsked || pending.isEmpty(),
            ) {
                IntroduceButton({ latest }, onChange, stringResource(R.string.ready_told_send))
            }

            var how by rememberSaveable { mutableStateOf(false) }
            ReadyRow(
                title = stringResource(R.string.ready_tile_title), sub = stringResource(R.string.ready_tile_sub),
                done = settings.tileAsked,
                more = if (how && Build.VERSION.SDK_INT < 33) {
                    {
                        Text(stringResource(R.string.ready_tile_steps), style = MaterialTheme.typography.bodyMedium, color = c.fg2)
                        TextButton(onClick = { onChange(latest.copy(tileAsked = true)) }, Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.ready_card_done))
                        }
                    }
                } else null,
            ) {
                if (Build.VERSION.SDK_INT >= 33) {
                    FilledTonalButton(
                        onClick = { requestHelpTile(ctx) { if (tileRequestDone(it)) onChange(latest.copy(tileAsked = true)) } },
                        Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.ready_tile_add)) }
                } else {
                    FilledTonalButton(onClick = { how = !how }, Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.ready_tile_how))
                    }
                }
            }
        }
    }
}

/** One thing to do: its words, then either its button or a green "Done". [more] opens under the row. */
@Composable
private fun ReadyRow(
    title: String, sub: String, done: Boolean,
    more: (@Composable () -> Unit)? = null,
    action: @Composable () -> Unit,
) {
    val c = Saathi.colors
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = c.fg2)
            }
            Spacer(Modifier.size(8.dp))
            if (done) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = c.ok)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.ready_card_done), style = MaterialTheme.typography.labelLarge, color = c.ok)
                }
            } else action()
        }
        if (!done) more?.let { Column(Modifier.padding(top = 4.dp), horizontalAlignment = Alignment.Start) { it() } }
    }
}
