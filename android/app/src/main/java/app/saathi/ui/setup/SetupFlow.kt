package app.saathi.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.saathi.core.ANYWHERE
import app.saathi.core.Contact
import app.saathi.core.PendingResult
import app.saathi.core.SavedPlace
import app.saathi.core.SetupProblem
import app.saathi.core.addPending
import app.saathi.core.setupProblem
import app.saathi.data.Settings
import app.saathi.ui.theme.Saathi
import app.saathi.ui.theme.SaathiMark
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

private enum class Step { CONTACTS, PERMISSIONS, PLACES, PRACTICE }

private val stateJson = Json { ignoreUnknownKeys = true }

/** Saveable state for a serializable value, so a rotation keeps what she typed. */
@Composable
private inline fun <reified T> rememberJsonState(initial: T): MutableState<T> =
    rememberSaveable(
        stateSaver = Saver<T, String>(
            save = { stateJson.encodeToString(it) },
            restore = { stateJson.decodeFromString<T>(it) },
        ),
    ) { mutableStateOf(initial) }

/** First-run setup: three short steps, then a practice screen. */
@Composable
fun SetupFlow(initial: Settings, onDone: (Settings) -> Unit) {
    var stepIndex by rememberSaveable { mutableIntStateOf(Step.CONTACTS.ordinal) }
    val step = Step.values()[stepIndex]

    var name by rememberSaveable { mutableStateOf(initial.name) }
    var contacts by rememberJsonState<List<Contact>>(initial.contacts)
    var places by rememberJsonState<List<SavedPlace>>(initial.places)

    // Step 1's typed-but-not-added form, and the problem to show.
    var formOpen by rememberSaveable { mutableStateOf(false) }
    var formName by rememberSaveable { mutableStateOf("") }
    var formPhone by rememberSaveable { mutableStateOf("") }
    var problem by remember { mutableStateOf<SetupProblem?>(null) }
    var problemNonce by remember { mutableIntStateOf(0) }

    // Step 2's live permission state, and the missing permission Continue pointed at.
    val ctx = LocalContext.current
    val live = rememberLivePerms()
    var permProblem by remember { mutableStateOf<Perm?>(null) }
    var permNonce by remember { mutableIntStateOf(0) }
    var skipDialog by rememberSaveable { mutableStateOf(false) }

    fun raise(p: SetupProblem) { problem = p; problemNonce++ }
    fun goTo(s: Step) { problem = null; permProblem = null; stepIndex = s.ordinal }

    fun continueContacts() {
        when (val r = addPending(contacts, formName, formPhone, ANYWHERE)) {
            is PendingResult.Problem -> { formOpen = true; raise(r.problem); return }
            is PendingResult.Ok -> { contacts = r.contacts; formName = ""; formPhone = ""; formOpen = false }
        }
        setupProblem(name, contacts)?.let { raise(it); return }
        goTo(Step.PERMISSIONS)
    }

    fun continuePermissions() {
        live.refresh()
        // Read the system again rather than trust what was drawn a moment ago.
        val missing = firstMissingRequired(readAllPerms(ctx))
        if (missing == null) goTo(Step.PLACES) else { permProblem = missing; permNonce++ }
    }

    fun finish() = onDone(initial.copy(name = name.trim(), contacts = contacts, places = places, onboarded = true))

    fun next() = when (step) {
        Step.CONTACTS -> continueContacts()
        Step.PERMISSIONS -> continuePermissions()
        Step.PLACES -> goTo(Step.PRACTICE)
        Step.PRACTICE -> finish()
    }

    fun back() { if (step != Step.CONTACTS) goTo(Step.values()[step.ordinal - 1]) }

    BackHandler(enabled = step != Step.CONTACTS) { back() }

    val c = Saathi.colors
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step != Step.CONTACTS) {
                IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            SaathiMark(24.dp)
            Spacer(Modifier.size(8.dp))
            Text("Saathi", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (step != Step.PRACTICE) {
                Text(
                    "Step ${step.ordinal + 1} of 3", style = MaterialTheme.typography.labelMedium, color = c.fg2,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                val color by animateColorAsState(
                    if (i <= step.ordinal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = "stepBar",
                )
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color))
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
        ) {
            when (step) {
                Step.CONTACTS -> ContactsStep(
                    name = name, onNameChange = { name = it },
                    contacts = contacts, onContactsChange = { contacts = it },
                    formOpen = formOpen, onFormOpenChange = { formOpen = it },
                    formName = formName, onFormNameChange = { formName = it },
                    formPhone = formPhone, onFormPhoneChange = { formPhone = it },
                    problem = problem, problemNonce = problemNonce, onProblemChange = { p -> if (p == null) problem = null else raise(p) },
                )
                Step.PERMISSIONS -> PermissionsStep(
                    granted = live.granted, onRefresh = live.refresh,
                    problemPerm = permProblem, problemNonce = permNonce,
                )
                Step.PLACES -> PlacesStep(places, { places = it })
                else -> Text("This step is being built.", style = MaterialTheme.typography.bodyLarge, color = c.fg2)
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = { next() },
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(20.dp),
            ) { Text(if (step == Step.PRACTICE) "Finish" else "Continue", style = MaterialTheme.typography.titleMedium) }
            if (step == Step.PLACES) {
                TextButton(onClick = { goTo(Step.PRACTICE) }) { Text("Skip") }
            }
            if (step == Step.PERMISSIONS && firstMissingRequired(live.granted) != null) {
                TextButton(onClick = { skipDialog = true }) { Text("Set up later") }
            }
        }
    }

    if (skipDialog && step == Step.PERMISSIONS) {
        AlertDialog(
            onDismissRequest = { skipDialog = false },
            title = { Text("Skip for now?") },
            text = { Text("Without these, Saathi can't watch trips or send alerts. You can finish this in Settings.") },
            confirmButton = { TextButton(onClick = { skipDialog = false }) { Text("Go back") } },
            dismissButton = { TextButton(onClick = { skipDialog = false; goTo(Step.PLACES) }) { Text("Skip anyway") } },
        )
    }
}
