package app.saathi.ui

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.saathi.R
import androidx.compose.ui.unit.sp
import app.saathi.alert.AlertManager
import app.saathi.core.Reason
import app.saathi.core.languagesFor
import app.saathi.core.recipients
import app.saathi.core.secondsLeft
import app.saathi.data.Store
import app.saathi.trip.TripService
import app.saathi.trip.TripState
import app.saathi.ui.theme.Saathi
import app.saathi.ui.theme.SaathiTheme
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/** Says her phrase for her, loud, in the local language, then Hindi, then English. Works offline. Always day: a bright sheet a stranger can read. */
class SpeakForMeActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private val speaking = mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT), androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        AlertManager.init(this)
        startForegroundService(TripService.intent(this, TripService.ACTION_GUARD))

        val p = TripState.now.place
        val langs = languagesFor(p.countryCode ?: "IN", p.state)
        val settings = Store.settings(this)
        val lines = phrases(settings.phraseKey).let { all ->
            langs.map { it to (all[it] ?: all["hi-IN"] ?: "") }
        }
        val who = namesText(recipients(settings.contacts, TripState.city()).map { it.name })

        setContent { SaathiTheme(dark = false) { Speak(lines, who, speaking.intValue, onAgain = { speak(lines) }, onClose = { finish() }) } }

        tts = TextToSpeech(this) { ok -> if (ok == TextToSpeech.SUCCESS) speak(lines) }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { id?.removePrefix("say-")?.toIntOrNull()?.let { speaking.intValue = it } }
                override fun onDone(id: String?) { if (id == "gap-${lines.lastIndex}") speaking.intValue = -1 }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) {}
            })
        }
        // Speaking starts now; the text waits. Not on a recreate (rotation), or "Don't text" would re-arm it.
        if (savedInstanceState == null) AlertManager.raiseAfter(Reason.SPEAK_FOR_ME, UNDO_WINDOW_MS)
    }

    private fun speak(lines: List<Pair<String, String>>) {
        val t = tts ?: return
        t.stop()
        lines.forEachIndexed { i, (lang, text) ->
            val res = t.setLanguage(Locale.forLanguageTag(lang))
            if (res >= TextToSpeech.LANG_AVAILABLE) {
                t.speak(text, TextToSpeech.QUEUE_ADD, null, "say-$i")
                t.playSilentUtterance(500, TextToSpeech.QUEUE_ADD, "gap-$i")
            }
        }
    }

    private fun phrases(key: String): Map<String, String> = runCatching {
        val root = Json.parseToJsonElement(assets.open("phrases.json").bufferedReader().readText()).jsonObject
        (root[key] ?: root["touched"])!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
    }.getOrDefault(mapOf("en-IN" to "Please help me."))

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}

/** How long she has to say "Don't text" before contacts are told. */
/** The few seconds to undo an accidental Speak for me or Saathi Help before contacts are texted. */
internal const val UNDO_WINDOW_MS = 5_000L

private fun languageName(tag: String) = when (tag.substringBefore('-')) {
    "kn" -> "ಕನ್ನಡ"; "hi" -> "हिन्दी"; "mr" -> "मराठी"; "ta" -> "தமிழ்"; "te" -> "తెలుగు"; "ml" -> "മലയാളം"
    "bn" -> "বাংলা"; "gu" -> "ગુજરાતી"; "en" -> "English"; else -> tag
}

@Composable
private fun Speak(lines: List<Pair<String, String>>, who: String, speaking: Int, onAgain: () -> Unit, onClose: () -> Unit) {
    val s by TripState.ui.collectAsState()
    val c = Saathi.colors
    var notSent by rememberSaveable { mutableStateOf(false) }
    val pendingEnds = s.pendingAlertEndsAtMs
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
        .padding(horizontal = 14.dp, vertical = 8.dp)) {
        when {
            s.alertActive -> HelpToldBanner(s.told)
            pendingEnds != null -> PendingHelpBanner(who, pendingEnds, onDontText = { if (AlertManager.cancelPendingAlert()) notSent = true })
            notSent -> Text(stringResource(R.string.speak_not_sent), style = MaterialTheme.typography.bodySmall, color = c.fg2,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp))
            else -> HelpToldBanner(s.told) // the instant between the window closing and the alert starting
        }
        Column(
            Modifier.padding(top = 12.dp).weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest).border(1.dp, c.hair, MaterialTheme.shapes.large)
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            lines.forEachIndexed { i, (lang, text) ->
                if (i > 0) HorizontalDivider(color = c.hair)
                Column(Modifier.padding(vertical = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)) {
                        if (i == speaking) Icon(Icons.Rounded.VolumeUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(if (i == speaking) stringResource(R.string.speak_speaking, languageName(lang)) else languageName(lang), style = MaterialTheme.typography.labelMedium,
                            color = if (i == speaking) MaterialTheme.colorScheme.primary else c.fg2)
                    }
                    Text(text, style = when (i) {
                        0 -> MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 38.sp)
                        1 -> MaterialTheme.typography.headlineSmall.copy(lineHeight = 32.sp)
                        else -> MaterialTheme.typography.titleLarge
                    })
                }
            }
        }
        Row(Modifier.padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onAgain, Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Rounded.Replay, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.speak_again))
            }
            FilledTonalButton(onClose, Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(20.dp)) { Text(stringResource(R.string.action_close)) }
        }
    }
}

/** The alert went out (madder): who was told, and what was sent. */
@Composable
internal fun HelpToldBanner(told: List<String>) {
    val c = Saathi.colors
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(c.helpContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).background(c.help, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(if (told.isEmpty()) stringResource(R.string.told_telling) else ctx.toldText(told),
                style = MaterialTheme.typography.titleSmall, color = c.onHelpContainer)
            Text(stringResource(R.string.speak_told_sub, stringResource(R.string.no_112)),
                style = MaterialTheme.typography.bodySmall, color = c.onHelpContainer.copy(alpha = 0.8f))
        }
    }
}

/** Before send (alum, never madder): a few seconds to undo an accidental tap. */
@Composable
internal fun PendingHelpBanner(who: String, endsAtMs: Long, onDontText: () -> Unit) {
    val c = Saathi.colors
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAtMs) { while (true) { now = System.currentTimeMillis(); delay(50) } }
    val left = secondsLeft(endsAtMs, now)
    val fraction = ((endsAtMs - now).coerceAtLeast(0) / UNDO_WINDOW_MS.toFloat()).coerceIn(0f, 1f)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(c.alum.copy(alpha = 0.16f)).padding(14.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 4.dp.toPx()
                val inset = w / 2
                val arc = Size(size.width - w, size.height - w)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
                drawArc(c.alum, -90f, 360f * fraction, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text("$left", style = MaterialTheme.typography.titleSmall, color = c.alum)
        }
        Column {
            Text(stringResource(R.string.speak_texting, who, left), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.speak_hint), style = MaterialTheme.typography.bodySmall, color = c.fg2,
                modifier = Modifier.padding(top = 2.dp))
            FilledTonalButton(onDontText, Modifier.padding(top = 10.dp).heightIn(min = 48.dp), shape = RoundedCornerShape(20.dp)) {
                Text(stringResource(R.string.speak_dont_text))
            }
        }
    }
}
