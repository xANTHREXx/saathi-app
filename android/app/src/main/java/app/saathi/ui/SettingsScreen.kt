package app.saathi.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.saathi.BuildConfig
import app.saathi.R
import app.saathi.alert.AlertManager
import app.saathi.core.SetupProblem
import app.saathi.core.toIntroduce
import app.saathi.data.Api.Release
import app.saathi.data.AppUpdateManager
import app.saathi.data.Settings
import app.saathi.data.TelemetryManager
import app.saathi.ui.setup.ContactsEditor
import app.saathi.ui.setup.Perm
import app.saathi.ui.setup.PermissionList
import app.saathi.ui.setup.PlacesEditor
import app.saathi.ui.setup.ProblemText
import app.saathi.ui.setup.firstMissingRequired
import app.saathi.ui.setup.rememberLivePerms
import app.saathi.ui.theme.Saathi
import app.saathi.ui.theme.Star
import app.saathi.ui.theme.reduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PHRASES = listOf(
    "touched" to R.string.settings_phrase_touched,
    "following" to R.string.settings_phrase_following,
    "help" to R.string.settings_phrase_help,
)

private val PRESS_CHOICES = listOf(3, 4, 6, 0)

/** The list, and the page each row opens. System Back returns to the list. */
private enum class Page { LIST, PEOPLE, NAME, WAYS, WORKING, RIDES, PLACES, PRIVACY, APP }

/**
 * One thing Saathi needs to reach her people. [required] ones show alum when missing; the rest show a quiet
 * ring, because leaving an optional one unset is her choice, not a fault.
 */
private class Protection(val title: String, val summary: String, val ready: Boolean, val required: Boolean, val page: Page)

private val Settle = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Fixed, uneven turns for the plate's stars, so the row reads hand-stamped rather than drifting. */
private val TILTS = floatArrayOf(-3f, 2f, -1f, 4f, -4f, 1f, 3f)

/**
 * Settings opens on one answer: how ready Saathi is to reach her people, counted in stars, one numbered row per
 * protection. Each row opens its own page. Every change is saved straight away through [onChange].
 */
@Composable
fun SettingsScreen(settings: Settings, onChange: (Settings) -> Unit) {
    var page by rememberSaveable { mutableStateOf(Page.LIST) }
    BackHandler(enabled = page != Page.LIST) { page = Page.LIST }
    val back = { page = Page.LIST }

    val ctx = LocalContext.current
    val live = rememberLivePerms()
    // The list keeps its place while she visits a page and comes back.
    val listScroll = rememberScrollState()
    // Which protections were ready last time the list was shown, so only one that just became ready stamps in.
    var seenReady by remember { mutableStateOf<Set<Int>?>(null) }

    // Her name: the field shows what she typed, but only a non-blank name is ever saved.
    var name by rememberSaveable { mutableStateOf(settings.name) }
    // The add-a-contact form lives here so it survives a rotation and a trip back to the list, like it does in setup.
    var formOpen by rememberSaveable { mutableStateOf(false) }
    var formName by rememberSaveable { mutableStateOf("") }
    var formPhone by rememberSaveable { mutableStateOf("") }
    var problem by remember { mutableStateOf<SetupProblem?>(null) }
    var problemNonce by remember { mutableIntStateOf(0) }

    val still = reduceMotion()
    Box(Modifier.fillMaxSize()) {
    AnimatedContent(
        page,
        transitionSpec = {
            if (still) EnterTransition.None togetherWith ExitTransition.None
            else {
                // Shared axis: a page comes in from the side it belongs to, and the list returns from the other.
                val dir = if (targetState == Page.LIST) -1 else 1
                (slideInHorizontally(tween(260, easing = Settle)) { it * dir / 5 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(200)) { -it * dir / 5 } + fadeOut(tween(120)))
            }
        },
        label = "settings-page",
    ) { p ->
        when (p) {
            Page.LIST -> ReadyList(settings, onChange, live.granted, listScroll, seenReady, { seenReady = it }) { page = it }
            Page.PEOPLE -> SubPage(stringResource(R.string.settings_people), back, stringResource(R.string.contacts_hint)) {
                PeoplePage(settings, onChange, formOpen, { formOpen = it }, formName, { formName = it }, formPhone, { formPhone = it },
                    problem, problemNonce) { pr -> problem = pr; if (pr != null) problemNonce++ }
            }
            Page.NAME -> SubPage(stringResource(R.string.ready_name), back, stringResource(R.string.settings_you_hint, name.trim().ifBlank { "Priya" })) {
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
            Page.WAYS -> SubPage(stringResource(R.string.ready_ways), back) { WaysPage(settings, onChange) }
            // What keeps Saathi working without her: the dying-phone texts, then the permissions it runs on.
            Page.WORKING -> SubPage(stringResource(R.string.ready_working), back) {
                Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    SettingSwitch(
                        stringResource(R.string.settings_dying_title), stringResource(R.string.settings_dying_body),
                        settings.dyingPhoneText, { onChange(settings.copy(dyingPhoneText = it)) },
                    )
                    PageSection(stringResource(R.string.settings_permissions), stringResource(R.string.settings_permissions_hint)) {
                        PermissionList(live.granted, live.refresh)
                    }
                }
            }
            // Row 6 lands on its own setting, not at the bottom of another row's page.
            Page.RIDES -> SubPage(stringResource(R.string.ready_rides), back) {
                PageSection(stringResource(R.string.perm_rides), stringResource(R.string.perm_rides_why)) {
                    if (live.granted[Perm.RIDES] == true) Row(
                        Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Saathi.colors.ok)
                        Text(stringResource(R.string.perm_allowed), style = MaterialTheme.typography.labelLarge, color = Saathi.colors.ok)
                    } else FilledTonalButton(
                        // Android's own notification-access screen; the page re-reads the state when she comes back.
                        onClick = { runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.perm_allow)) }
                }
            }
            Page.PLACES -> SubPage(stringResource(R.string.places_title), back, stringResource(R.string.settings_places_hint)) {
                PlacesEditor(settings.places, { onChange(settings.copy(places = it)) })
                SettingSwitch(
                    stringResource(R.string.settings_arrival_title), stringResource(R.string.settings_arrival_body),
                    settings.arrivalText, { onChange(settings.copy(arrivalText = it)) }, Modifier.padding(top = 16.dp),
                )
            }
            Page.PRIVACY -> SubPage(stringResource(R.string.settings_privacy), back) { PrivacyPage(settings, onChange) }
            Page.APP -> SubPage(stringResource(R.string.settings_app), back) { AppSection() }
        }
    }
    // Keep the clock readable when the list or a page scrolls up under it. Opaque: nothing shows through under the clock.
    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background))
    }
}

// ---------------------------------------------------------------- the list

@Composable
private fun ReadyList(
    settings: Settings,
    onChange: (Settings) -> Unit,
    granted: Map<Perm, Boolean>,
    scroll: ScrollState,
    seenReady: Set<Int>?,
    onSeen: (Set<Int>) -> Unit,
    open: (Page) -> Unit,
) {
    val c = Saathi.colors
    val ctx = LocalContext.current
    val items = protections(settings, granted)
    val readyNow = items.indices.filter { items[it].ready }.toSet()
    // The first time the list is shown nothing stamps; after that, only what became ready since.
    val fresh = if (seenReady == null) emptySet() else readyNow - seenReady
    LaunchedEffect(readyNow) { onSeen(readyNow) }

    var practiceHint by remember { mutableStateOf(false) }
    LaunchedEffect(practiceHint) { if (practiceHint) { delay(6000); practiceHint = false } }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).statusBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp))

        ReadinessPlate(items, fresh, open, Modifier.padding(top = 20.dp))

        Column(Modifier.padding(top = 8.dp)) {
            items.forEachIndexed { i, p -> ProtectionRow(i + 1, p) { open(p.page) } }
        }

        FilledTonalButton(
            onClick = { AlertManager.startPractice(5_000); practiceHint = true },
            Modifier.padding(top = 28.dp).fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(20.dp),
        ) { Text(stringResource(R.string.settings_practice_button), style = MaterialTheme.typography.titleMedium) }
        if (practiceHint) Text(stringResource(R.string.settings_practice_hint), style = MaterialTheme.typography.bodyMedium,
            color = c.fg2, modifier = Modifier.padding(start = 4.dp, top = 8.dp))

        Text(stringResource(R.string.settings_also), style = MaterialTheme.typography.labelLarge, color = c.fg2,
            modifier = Modifier.padding(start = 4.dp, top = 28.dp, bottom = 2.dp))
        LanguageRow()
        AlsoRow(stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_sum)) { open(Page.PRIVACY) }
        AlsoRow(stringResource(R.string.settings_app), stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), last = true) { open(Page.APP) }
        Spacer(Modifier.height(8.dp))
    }
}

/** The seven protections, in the order an alert needs them. Every one is read from facts the app already has. */
@Composable
private fun protections(s: Settings, granted: Map<Perm, Boolean>): List<Protection> {
    val ctx = LocalContext.current
    val pending = toIntroduce(s.contacts, s.introduced)
    val missingPerm = firstMissingRequired(granted)
    val ways = buildList {
        add(stringResource(R.string.help_title))
        if (s.tileAsked) add(stringResource(R.string.ways_tile))
        if (s.headsetHelp) add(stringResource(R.string.ways_earphone))
        if (s.shakeCheckIn) add(stringResource(R.string.ways_shake))
        if (s.powerPresses > 0) add(stringResource(R.string.ways_power))
    }.joinToString(", ")
    return listOf(
        Protection(stringResource(R.string.settings_people),
            if (s.contacts.isEmpty()) stringResource(R.string.ready_people_none) else toldWho(s.contacts),
            s.contacts.isNotEmpty(), required = true, Page.PEOPLE),
        Protection(stringResource(R.string.ready_known),
            when {
                s.contacts.isEmpty() -> stringResource(R.string.ready_known_nobody)
                pending.isNotEmpty() -> stringResource(R.string.settings_intro_contacts, toldWho(pending))
                else -> stringResource(R.string.ready_known_done)
            },
            s.contacts.isNotEmpty() && pending.isEmpty(), required = false, Page.PEOPLE),
        Protection(stringResource(R.string.ready_name),
            s.name.ifBlank { stringResource(R.string.ready_name_none) }, s.name.isNotBlank(), required = true, Page.NAME),
        // Hold for help always works, so this one is always ready; the others she turns on are listed after it.
        Protection(stringResource(R.string.ready_ways), ways, ready = true, required = false, Page.WAYS),
        Protection(stringResource(R.string.ready_working),
            missingPerm?.let { stringResource(permOffText(it)) } ?: stringResource(R.string.ready_working_ok),
            missingPerm == null, required = true, Page.WORKING),
        Protection(stringResource(R.string.ready_rides),
            stringResource(if (granted[Perm.RIDES] == true) R.string.ready_rides_on else R.string.perm_off_rides),
            granted[Perm.RIDES] == true, required = false, Page.RIDES),
        Protection(stringResource(R.string.places_title),
            if (s.places.isEmpty()) stringResource(R.string.ready_places_none) else s.places.joinToString(", ") { ctx.placeLabel(it.label) },
            s.places.isNotEmpty(), required = false, Page.PLACES),
    )
}

@StringRes
private fun permOffText(p: Perm): Int = when (p) {
    Perm.LOCATION -> R.string.perm_off_location
    Perm.LOCATION_ALWAYS -> R.string.perm_off_location_always
    Perm.SMS -> R.string.perm_off_sms
    Perm.NOTIFICATIONS -> R.string.perm_off_notifications
    Perm.LOCK_SCREEN -> R.string.perm_off_lock_screen
    Perm.BACKGROUND -> R.string.perm_off_background
    Perm.KEEP_RUNNING -> R.string.perm_off_keep_running
    Perm.RIDES, Perm.MICROPHONE -> R.string.perm_off_rides
}

/**
 * The count, a star per protection, and one line on what comes next. A protection that just became ready
 * stamps its star in, like a block on cloth. The line opens the page that fixes it.
 */
@Composable
private fun ReadinessPlate(items: List<Protection>, fresh: Set<Int>, open: (Page) -> Unit, modifier: Modifier = Modifier) {
    val c = Saathi.colors
    val ready = items.count { it.ready }
    val fix = items.firstOrNull { !it.ready && it.required }
    val next = fix ?: items.firstOrNull { !it.ready }
    val still = reduceMotion()
    val needsFixing = stringResource(R.string.ready_state_fix)
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, c.hair, MaterialTheme.shapes.large).padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The count turns over like a departures board when a protection changes, in step with its star's stamp.
            AnimatedContent(
                ready,
                transitionSpec = {
                    if (still) EnterTransition.None togetherWith ExitTransition.None
                    else {
                        val up = targetState > initialState
                        (slideInVertically(tween(220, easing = Settle)) { if (up) it else -it } + fadeIn(tween(160))) togetherWith
                            (slideOutVertically(tween(160)) { if (up) -it else it } + fadeOut(tween(120)))
                    }
                },
                label = "ready-count",
            ) { n ->
                Text(
                    if (n == items.size) stringResource(R.string.ready_all, items.size) else stringResource(R.string.ready_count, n, items.size),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            // Hierarchy by weight, not a second face: the count in ink, the word in fg2 at 500.
            Text(stringResource(R.string.ready_word), Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium), color = c.fg2)
        }
        Row(Modifier.padding(top = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEachIndexed { i, p -> Mark(p, 22.dp, stamp = i in fresh, tilt = TILTS[i % TILTS.size]) }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .then(if (next != null) Modifier.clickable(role = Role.Button) { open(next.page) } else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(8.dp).background(if (fix != null) c.alum else if (next != null) c.fg3 else c.ok, CircleShape))
            // A missing required protection is named here; its row below says what is wrong, so nothing repeats.
            if (fix != null) Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = c.alum, fontWeight = FontWeight.SemiBold)) { append(fix.title) }
                    append(" · ")
                    append(needsFixing)
                },
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground,
            ) else Text(
                if (next != null) stringResource(R.string.ready_next, next.title) else stringResource(R.string.ready_all_note),
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.fg2,
            )
            if (fix != null) TrailingFix()
        }
    }
}

/** A numbered row: the protection's star or ring, what it is, where it stands. The whole row opens its page. */
@Composable
private fun ProtectionRow(n: Int, p: Protection, onOpen: () -> Unit) {
    val c = Saathi.colors
    val missing = !p.ready && p.required
    val state = stringResource(when { p.ready -> R.string.ready_state_set; missing -> R.string.ready_state_fix; else -> R.string.ready_state_unset })
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onOpen)
            .semantics { stateDescription = state }.padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("$n", Modifier.widthIn(min = 16.dp), style = MaterialTheme.typography.labelMedium, color = c.fg2, textAlign = TextAlign.End)
        Mark(p, 22.dp)
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.titleMedium)
            Text(p.summary, style = MaterialTheme.typography.bodyMedium, color = if (missing) c.alum else c.fg2,
                modifier = Modifier.padding(top = 2.dp))
        }
        if (missing) TrailingFix() else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.fg2)
    }
    HorizontalDivider(thickness = 1.dp, color = c.hair)
}

/** "Fix" in the chevron's slot: its end sits where the chevron's glyph ends, so the trailing column stays one line. */
@Composable
private fun TrailingFix() {
    Box(Modifier.widthIn(min = 24.dp), contentAlignment = Alignment.CenterEnd) {
        Text(stringResource(R.string.status_fix), style = MaterialTheme.typography.labelLarge, color = Saathi.colors.alum,
            modifier = Modifier.padding(end = 7.dp))
    }
}

/** Punched or not: an indigo star when ready, an alum ring when a required one is missing, a quiet ring otherwise. */
@Composable
private fun Mark(p: Protection, size: Dp, stamp: Boolean = false, tilt: Float = 0f) {
    val c = Saathi.colors
    key(p.ready) {
        // Fixed when the mark appears, so a stamp that started plays out even after the list recomposes.
        val stampOnce = remember { stamp }
        if (p.ready) Star(MaterialTheme.colorScheme.primary, size, stamp = stampOnce, tilt = tilt)
        // Day ink-3 is under 3:1 on resist white, so the quiet ring steps up to ink-2 by day; by night ink-3 holds.
        else StarRing(if (p.required) c.alum else if (c.isDark) c.fg3 else c.fg2, size)
    }
}

/** The ajrakh star drawn as an outline: the place a star will go. */
@Composable
private fun StarRing(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 2f * 0.62f
        val line = Stroke(width = 2.dp.toPx())
        val sq = Path().apply {
            moveTo(center.x - s, center.y - s); lineTo(center.x + s, center.y - s)
            lineTo(center.x + s, center.y + s); lineTo(center.x - s, center.y + s); close()
        }
        drawPath(sq, color, style = line)
        rotate(45f, center) { drawPath(sq, color, style = line) }
    }
}

/** A quiet row under Also: no number and no star, since none of these changes who gets told. */
@Composable
private fun AlsoRow(title: String, summary: String, last: Boolean = false, onClick: (() -> Unit)?) {
    val c = Saathi.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = c.fg2, modifier = Modifier.padding(top = 2.dp))
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.fg2)
    }
    if (!last) HorizontalDivider(thickness = 1.dp, color = c.hair)
}

/**
 * The app's language. From Android 13 the system keeps a language for each app, so this opens that screen.
 * Before that there is nothing to switch: Saathi follows the phone's language.
 */
@Composable
private fun LanguageRow() {
    val ctx = LocalContext.current
    val current = stringResource(R.string.lang_name)
    if (Build.VERSION.SDK_INT >= 33) AlsoRow(stringResource(R.string.settings_language), current) {
        val own = Uri.parse("package:${ctx.packageName}")
        runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS, own)) }
            .onFailure { runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, own)) } }
    } else AlsoRow(stringResource(R.string.settings_language), stringResource(R.string.settings_language_follow), onClick = null)
}

// ---------------------------------------------------------------- pages

/** A page under the list: back arrow and title, an optional one-liner, then its controls. */
@Composable
private fun SubPage(title: String, onBack: () -> Unit, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().imePadding().padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp, end = 16.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = Saathi.colors.fg2,
                modifier = Modifier.padding(bottom = 16.dp))
            content()
        }
    }
}

@Composable
private fun PeoplePage(
    settings: Settings, onChange: (Settings) -> Unit,
    formOpen: Boolean, onFormOpenChange: (Boolean) -> Unit,
    formName: String, onFormNameChange: (String) -> Unit,
    formPhone: String, onFormPhoneChange: (String) -> Unit,
    problem: SetupProblem?, problemNonce: Int, onProblemChange: (SetupProblem?) -> Unit,
) {
    // The tile and intro sends can answer after a recomposition; save onto what is current, not what was captured.
    val latest by rememberUpdatedState(settings)
    ContactsEditor(
        contacts = settings.contacts, onContactsChange = { onChange(settings.copy(contacts = it)) },
        formOpen = formOpen, onFormOpenChange = onFormOpenChange,
        formName = formName, onFormNameChange = onFormNameChange,
        formPhone = formPhone, onFormPhoneChange = onFormPhoneChange,
        problem = problem, problemNonce = problemNonce, onProblemChange = onProblemChange,
        emptyProblem = stringResource(R.string.contacts_empty_problem),
    )
    // Anyone added since the last "I've added you" text gets offered one; the rest aren't texted twice.
    val pending = toIntroduce(settings.contacts, settings.introduced)
    if (pending.isNotEmpty()) IntroduceButton(
        { latest }, onChange, stringResource(R.string.settings_intro_contacts, toldWho(pending)),
        Modifier.padding(top = 8.dp),
    )
}

/** Every way she can raise help, the one that always works first. Each choice says what it does where she makes it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaysPage(settings: Settings, onChange: (Settings) -> Unit) {
    val c = Saathi.colors
    val ctx = LocalContext.current
    val latest by rememberUpdatedState(settings)
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        PageSection(stringResource(R.string.help_title), stringResource(R.string.ways_hold_body))

        PageSection(stringResource(R.string.settings_tile), stringResource(R.string.settings_tile_body)) {
            if (Build.VERSION.SDK_INT >= 33) FilledTonalButton(
                // Home's "Two more things" card reads the same flag, so adding it here ticks it off there.
                onClick = { requestHelpTile(ctx) { if (tileRequestDone(it)) onChange(latest.copy(tileAsked = true)) } },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.settings_tile_add)) }
            else Text(stringResource(R.string.settings_tile_manual), style = MaterialTheme.typography.bodyMedium, color = c.fg2)
        }

        PageSection(stringResource(R.string.settings_power)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            // The choice says what it does: the gesture when it's on, why it starts off when it's off.
            Text(
                if (settings.powerPresses > 0) pluralStringResource(R.plurals.settings_power_gesture, settings.powerPresses, settings.powerPresses)
                else stringResource(R.string.settings_power_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = if (settings.powerPresses > 0) MaterialTheme.colorScheme.onBackground else c.fg2,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingSwitch(
                stringResource(R.string.settings_headset_title), stringResource(R.string.settings_headset_body),
                settings.headsetHelp, { onChange(settings.copy(headsetHelp = it)) },
            )
            SettingSwitch(
                stringResource(R.string.settings_shake_title), stringResource(R.string.settings_shake_body),
                settings.shakeCheckIn, { onChange(settings.copy(shakeCheckIn = it)) },
            )
        }

        PageSection(stringResource(R.string.settings_backtap_title), stringResource(R.string.settings_backtap_body))

        PageSection(stringResource(R.string.settings_speak_says)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PHRASES.forEach { (key, textRes) ->
                    FilterChip(
                        selected = settings.phraseKey == key,
                        onClick = { if (settings.phraseKey != key) onChange(settings.copy(phraseKey = key)) },
                        label = { Text(stringResource(textRes), Modifier.padding(vertical = 8.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyPage(settings: Settings, onChange: (Settings) -> Unit) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        PageSection(stringResource(R.string.settings_community)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = settings.communityConsent, role = Role.Switch,
                    onValueChange = { onChange(settings.copy(communityConsent = it, consentAsked = true)) },
                ),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.settings_community_body), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = settings.communityConsent, onCheckedChange = null)
            }
        }
        PageSection(stringResource(R.string.settings_improve)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingSwitch(
                    stringResource(R.string.settings_crash_title), stringResource(R.string.settings_crash_body),
                    settings.crashReports, { onChange(settings.copy(crashReports = it)) },
                )
                SettingSwitch(
                    stringResource(R.string.settings_usage_title), stringResource(R.string.settings_usage_body),
                    settings.usageStats,
                    {
                        if (!it) TelemetryManager.clearQueue(ctx) // nothing queued stays behind once she says no
                        onChange(settings.copy(usageStats = it))
                    },
                )
            }
        }
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

/** One on/off choice: title and body on the left, the switch on the right; the whole row toggles. */
@Composable
private fun SettingSwitch(title: String, body: String, checked: Boolean, onChecked: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            // Same size as a section heading, so a switch reads as a peer of the sections around it.
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Saathi.colors.fg2, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A titled block on a page: a heading, an optional one-liner in fg2, then the controls, if it has any. */
@Composable
private fun PageSection(title: String, hint: String? = null, content: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = Saathi.colors.fg2, modifier = Modifier.padding(top = 4.dp))
        if (content != null) {
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
