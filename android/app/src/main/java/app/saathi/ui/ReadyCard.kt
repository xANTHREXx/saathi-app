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
import app.saathi.core.Contact
import app.saathi.core.smsRecipients
import app.saathi.core.toldNames
import app.saathi.data.Settings
import app.saathi.trigger.HelpTileService
import app.saathi.ui.theme.Saathi
import android.graphics.drawable.Icon as AndroidIcon

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

    val names = toldNames(settings.contacts)
    val who = when {
        names == null -> stringResource(R.string.ready_told_names_none)
        names.second == null -> names.first
        names.others == 0 -> stringResource(R.string.ready_told_names_two, names.first, names.second)
        else -> pluralStringResource(R.plurals.ready_told_names_more, names.others, names.first, names.others)
    }

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
                done = settings.contactsToldAsked,
            ) {
                FilledTonalButton(
                    onClick = { if (writeAboutSaathi(ctx, settings.contacts)) onChange(latest.copy(contactsToldAsked = true)) },
                    Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.ready_told_write)) }
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
