package app.saathi.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.saathi.R
import app.saathi.alert.AlertManager
import app.saathi.core.OkayPath
import app.saathi.core.okayPath
import app.saathi.trip.TripState
import app.saathi.ui.theme.Saathi

/** Where an I'm okay would go right now, from the phone's real lock state. */
fun Context.currentOkayPath(): OkayPath {
    val km = getSystemService(KeyguardManager::class.java)
    return okayPath(TripState.now.alertActive, km.isKeyguardLocked, km.isDeviceSecure)
}

/**
 * The one way to say "I'm okay" from a screen. Before anything went out (a check-in answer, a pending window, a
 * practice) it is one tap. After an alert went out, a locked phone must be unlocked first; if she backs out of
 * the unlock, nothing is sent and she stays here. [onSent] runs only after I'm okay was sent.
 */
fun Activity.confirmOkay(onSent: () -> Unit = {}) {
    when (currentOkayPath()) {
        OkayPath.DIRECT -> { AlertManager.imOkay(); onSent() }
        OkayPath.UNLOCK_FIRST -> getSystemService(KeyguardManager::class.java).requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { AlertManager.imOkay(); onSent() }
                override fun onDismissCancelled() = Unit
                override fun onDismissError() = Unit
            },
        )
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** [confirmOkay] for the I'm okay plates in Compose. */
@Composable
fun rememberConfirmOkay(onSent: () -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current
    return {
        val activity = ctx.findActivity()
        if (activity != null) activity.confirmOkay(onSent)
        else if (ctx.currentOkayPath() == OkayPath.DIRECT) { AlertManager.imOkay(); onSent() } // never reached from the app's own screens
    }
}

/** Whether the phone is behind a secure lock right now; follows the screen turning off and her unlocking it. */
@Composable
private fun rememberLockedAndSecure(): Boolean {
    val ctx = LocalContext.current
    val km = remember { ctx.getSystemService(KeyguardManager::class.java) }
    var locked by remember { mutableStateOf(km.isKeyguardLocked && km.isDeviceSecure) }
    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { locked = km.isKeyguardLocked && km.isDeviceSecure }
        }
        ctx.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_OFF)
        })
        locked = km.isKeyguardLocked && km.isDeviceSecure
        onDispose { ctx.unregisterReceiver(receiver) }
    }
    return locked
}

/** Under I'm okay: the heads-up that a sent alert needs the phone unlocked first. Nothing otherwise. */
@Composable
fun UnlockFirstNote(modifier: Modifier = Modifier) {
    val ui by TripState.ui.collectAsState()
    val locked = rememberLockedAndSecure()
    if (okayPath(ui.alertActive, locked, locked) != OkayPath.UNLOCK_FIRST) return
    Text(
        stringResource(R.string.okay_unlock_first), style = MaterialTheme.typography.bodySmall, color = Saathi.colors.fg2,
        textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth().padding(top = 8.dp),
    )
}
