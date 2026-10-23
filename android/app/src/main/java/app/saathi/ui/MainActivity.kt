package app.saathi.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.saathi.ui.onboarding.PracticeCard
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import app.saathi.ui.theme.reduceMotion
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.ElectricRickshaw
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.AlertDialog
import app.saathi.core.Mode
import app.saathi.core.PlateButton
import app.saathi.core.plateButton
import app.saathi.core.parsePlateCheck
import app.saathi.core.readyCardVisible
import app.saathi.core.recipients
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.saathi.alert.AlertManager
import app.saathi.alert.Notifier
import app.saathi.core.Reason
import app.saathi.data.ALERT_RECORD
import app.saathi.data.IntroRecord
import app.saathi.data.Settings
import app.saathi.BuildConfig
import app.saathi.data.RunState
import app.saathi.data.Store
import app.saathi.data.TestDevice
import app.saathi.data.TripRecord
import app.saathi.trip.TripService
import app.saathi.trip.TripState
import app.saathi.trip.UiState
import app.saathi.trip.openBackgroundSettings
import app.saathi.ui.setup.Perm
import app.saathi.ui.setup.SetupFlow
import app.saathi.ui.setup.firstMissingRequired
import app.saathi.ui.setup.readAllPerms
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.saathi.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.saathi.ui.theme.PlateText
import app.saathi.ui.theme.Saathi
import app.saathi.ui.theme.SaathiMark
import app.saathi.ui.theme.SaathiTheme
import app.saathi.ui.theme.Star
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AlertManager.init(this)
        setContent {
            SaathiTheme {
                var settings by remember { mutableStateOf(Store.settings(this)) }
                // Set when setup has just finished, so Home can say so once.
                var justSetUp by remember { mutableStateOf(false) }

                if (!settings.onboarded) SetupFlow(settings) { s ->
                    Store.save(this, s); settings = s; justSetUp = true; guard(this)
                } else {
                    // Once when Home appears, and again if the power-button count changes (the service re-reads it on each start).
                    LaunchedEffect(settings.powerPresses) { guard(this@MainActivity) }
                    Home(
                        settings, onChange = { s -> Store.save(this, s); settings = s },
                        justSetUp = justSetUp, onSetUpShown = { justSetUp = false },
                    )
                    // Home only: never over setup, a trip, an alert or a check-in.
                    UpdatePrompt()
                }
            }
        }
    }
}

private fun guard(ctx: Context) = ctx.startForegroundService(TripService.intent(ctx, TripService.ACTION_GUARD))

private fun startTrip(ctx: Context, mode: String, plate: String? = null, app: String? = null, replay: String? = null) =
    ctx.startForegroundService(TripService.intent(ctx, TripService.ACTION_START_TRIP)
        .putExtra(TripService.EXTRA_MODE, mode).putExtra(TripService.EXTRA_PLATE, plate)
        .putExtra(TripService.EXTRA_APP, app).putExtra(TripService.EXTRA_REPLAY, replay).putExtra(TripService.EXTRA_SPEED, 30))

// ---------------------------------------------------------------- home (flows 3, 4, 5)

private enum class Tab { TRIP, TRIPS, SETTINGS }

@Composable
private fun Home(settings: Settings, onChange: (Settings) -> Unit, justSetUp: Boolean, onSetUpShown: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(Tab.TRIP) }
    val snackbar = remember { SnackbarHostState() }
    val readyMessage = stringResource(R.string.home_ready_snackbar)
    val s by TripState.ui.collectAsState()
    LaunchedEffect(justSetUp) {
        if (justSetUp) {
            snackbar.showSnackbar(readyMessage)
            onSetUpShown()
        }
    }
    LaunchedEffect(s.tip) {
        s.tip?.let { text ->
            snackbar.showSnackbar(text)
            TripState.update { it.copy(tip = null) }
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Flat, like everything else: a tonal bar with no shadow.
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Surface(
                    Modifier.padding(horizontal = 12.dp), shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ) {
                    Text(data.visuals.message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                NavigationBarItem(tab == Tab.TRIP, { tab = Tab.TRIP }, { Icon(Icons.Rounded.Route, null) }, label = { Text(stringResource(R.string.nav_trip)) })
                NavigationBarItem(tab == Tab.TRIPS, { tab = Tab.TRIPS }, { Icon(Icons.Rounded.History, null) }, label = { Text(stringResource(R.string.nav_my_trips)) })
                NavigationBarItem(false, { ctx.startActivity(Intent(ctx, CommunityMapActivity::class.java)) },
                    { Icon(Icons.Rounded.Map, null) }, label = { Text(stringResource(R.string.nav_map)) })
                NavigationBarItem(tab == Tab.SETTINGS, { tab = Tab.SETTINGS }, { Icon(Icons.Rounded.Tune, null) }, label = { Text(stringResource(R.string.nav_settings)) })
            }
        },
    ) { pad ->
        Box(Modifier.padding(bottom = pad.calculateBottomPadding())) {
            when (tab) {
                Tab.TRIP -> TripTab(settings, onChange)
                Tab.TRIPS -> ActivityTab()
                Tab.SETTINGS -> SettingsScreen(settings, onChange)
            }
        }
    }
}

@Composable
private fun TripTab(settings: Settings, onChange: (Settings) -> Unit) {
    val s by TripState.ui.collectAsState()
    val ctx = LocalContext.current
    var showDemo by remember { mutableStateOf(false) }

    val runningPractice = s.practice && s.checkInEndsAtMs != null
    LaunchedEffect(runningPractice) {
        if (runningPractice) {
            ctx.startActivity(Intent(ctx, CheckInActivity::class.java))
        }
    }

    // The map only peeks above the sheet, scaled to the screen so short phones keep room for the sheet's last rows.
    val peek = (LocalConfiguration.current.screenHeightDp * 0.2f).coerceIn(112f, 184f)
    Column(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxWidth().weight(1f)) {
        // Tall enough to run under the sheet, short enough that her position dot stays above it.
        TripMap(s, Modifier.fillMaxWidth().height((peek + 120f).dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MapChip(Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { showDemo = !showDemo }) }) {
                    SaathiMark(20.dp); Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.weight(1f))
                // On a trip the plate matters more than the city, and two chips beside it crowd at large text sizes.
                if (s.plate == null) (TripState.city() ?: s.place.city)?.let { city ->
                    MapChip { Icon(Icons.Rounded.NearMe, null, Modifier.size(16.dp)); Text(city, style = MaterialTheme.typography.labelMedium) }
                }
                s.plate?.let { p -> MapChip { PlateText(p) } }
            }
            Spacer(Modifier.height(peek.dp))
            Surface(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally).size(36.dp, 4.dp)
                        .background(Saathi.colors.fg3.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
                    // The alert unfolds from the top of the sheet; I'm okay folds it away. While it folds, it keeps
                    // showing the alert it was, not an emptied one.
                    val lastAlert = remember { arrayOf(s) }
                    if (s.alertActive) lastAlert[0] = s
                    val unfold = motionMs(UNFOLD_MS); val leave = motionMs(LEAVE_MS); val move = motionMs(MOVE_MS)
                    Column {
                        AnimatedVisibility(
                            s.alertActive,
                            enter = expandVertically(tween(unfold, easing = Arrive), expandFrom = Alignment.Top) + fadeIn(tween(move)),
                            exit = shrinkVertically(tween(leave), shrinkTowards = Alignment.Top) + fadeOut(tween(leave)),
                        ) { Box(Modifier.padding(bottom = 14.dp)) { ToldBanner(lastAlert[0]) } }
                        // A ride picked up on its own: the sheet hands over from Ready to the trip, so she sees it begin.
                        AnimatedContent(
                            s.onTrip,
                            transitionSpec = {
                                (fadeIn(tween(move)) + slideInVertically(tween(unfold, easing = Arrive)) { it / 8 }) togetherWith
                                    fadeOut(tween(leave)) using SizeTransform(clip = false)
                            },
                            label = "sheet",
                        ) { onTrip -> if (onTrip) OnTrip(s) else Ready() }
                    }
                    if (!s.alertActive && s.checkInEndsAtMs == null) CheckOnMe(s, settings, onChange)
                    if (!s.onTrip && !s.alertActive) PracticeCard()
                    AfterTrip(settings, onChange)
                    if (readyCardVisible(s.onTrip, s.alertActive, settings.contacts.isNotEmpty(),
                            settings.readyCardDismissed, settings.contactsToldAsked, settings.tileAsked)) ReadyCard(settings, onChange)
                    if (showDemo) DemoPanel(settings, onChange)
                }
            }
        }
        // Keep the clock readable when the sheet scrolls up under it.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)))
        // The sheet runs on under the pinned help row; a short fade says "there's more" instead of slicing a line.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
    }
    // Help never scrolls away: always one hold from her thumb.
    Box(Modifier.fillMaxWidth().height(1.dp).background(Saathi.colors.hair))
    Box(Modifier.background(MaterialTheme.colorScheme.background).padding(vertical = 12.dp)) { HelpRow(settings, quiet = s.alertActive) }
    }
}

@Composable
private fun MapChip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, Saathi.colors.hair, RoundedCornerShape(18.dp)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** After an alert: who was told, and the one full-contrast control, I'm okay. */
@Composable
private fun ToldBanner(s: UiState) {
    val c = Saathi.colors
    val ctx = LocalContext.current
    val linkSent = stringResource(R.string.told_link)
    val recording = stringResource(R.string.told_recording)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(c.helpContainer).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // The check draws itself the moment the texts are confirmed sent, not before.
            Box(Modifier.size(36.dp).background(c.help, CircleShape), contentAlignment = Alignment.Center) {
                DrawnCheck(s.told.isNotEmpty(), Color.White, Modifier.size(20.dp))
            }
            Column {
                Crossfade(if (s.told.isEmpty()) stringResource(R.string.told_telling) else ctx.toldText(s.told),
                    animationSpec = tween(motionMs(MOVE_MS)), label = "told") {
                    Text(it, style = MaterialTheme.typography.titleSmall, color = c.onHelpContainer)
                }
                // Call 112 sits on the plate below, so the banner no longer needs to say Saathi doesn't call.
                Text(buildString {
                    append(linkSent)
                    if (s.recording) append(" · $recording")
                }, style = MaterialTheme.typography.bodySmall, color = c.onHelpContainer.copy(alpha = 0.8f))
                // Nothing retries on its own, so say only what failed and hand her the retry right here.
                if (s.smsFailed.isNotEmpty()) {
                    Text(stringResource(R.string.raised_failed, ctx.namesText(s.smsFailed)),
                        style = MaterialTheme.typography.bodySmall, color = c.onHelpContainer, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = { AlertManager.resend() }, Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.help, contentColor = Color.White)) { Text(stringResource(R.string.raised_retry)) }
                }
            }
        }
        OkayPlate(onClick = rememberConfirmOkay())
        UnlockFirstNote()
    }
}

/** The reversed plate: full contrast, the one thing to press. */
@Composable
fun OkayPlate(height: Int = 88, onClick: () -> Unit) {
    Button(
        onClick, Modifier.fillMaxWidth().height(height.dp), shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface),
    ) {
        Icon(Icons.Rounded.Check, null, Modifier.size(28.dp)); Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.im_okay), style = MaterialTheme.typography.headlineMedium)
    }
}

private fun watchedMinutes(s: UiState): Int {
    val first = s.trail.firstOrNull(); val last = s.trail.lastOrNull()
    // Replays run faster than the wall clock; their fixes carry the trip's own time.
    if (first != null && last != null && last.t > first.t) return ((last.t - first.t) / 60).toInt()
    return if (s.startedAtMs > 0) ((System.currentTimeMillis() - s.startedAtMs) / 60_000).toInt() else 0
}

@Composable
private fun OnTrip(s: UiState) {
    val ctx = LocalContext.current
    val c = Saathi.colors
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); tick++ } }
    val minutes = remember(s.trail.size, s.startedAtMs, tick) { watchedMinutes(s) }
    var endArmed by remember { mutableStateOf(false) }
    LaunchedEffect(endArmed) { if (endArmed) { delay(4_000); endArmed = false } }
    val recordPlate = rememberPlateRecorder { plate ->
        ctx.startForegroundService(TripService.intent(ctx, TripService.ACTION_SET_PLATE).putExtra(TripService.EXTRA_PLATE, plate))
    }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(s.driver?.let { stringResource(R.string.trip_with_driver, it) } ?: s.place.label ?: stringResource(R.string.trip_on_your_way),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Green only while all is quiet: alum at a check-in, madder once contacts were told.
                    Box(Modifier.size(8.dp).background(if (s.alertActive) c.help else if (s.checkInEndsAtMs != null) c.alum else c.ok, CircleShape))
                    Text(ctx.watchingText(s.mode, s.app), style = MaterialTheme.typography.bodyMedium, color = c.fg2)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(pluralStringResource(R.plurals.trip_minutes, minutes, minutes), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.trip_watched), style = MaterialTheme.typography.labelSmall, color = c.fg2)
            }
        }
        // Where she's going, and changing it, aren't questions during a check-in or alert; its green dot
        // would also say "all fine" right under the alert colour.
        if (s.checkInEndsAtMs == null && !s.alertActive) DestinationRow(s)
        // Her two actions come before the thread, so they are on screen without scrolling. Never during a
        // check-in or alert, when ending the trip is the wrong thing to offer.
        if (s.checkInEndsAtMs == null && !s.alertActive) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // A ride app booked a plate to compare with; a hailed auto has none, so she can record it instead.
            when (plateButton(s.mode, s.plate, s.app)) {
                PlateButton.COMPARE -> FilledTonalButton(onClick = {
                    ctx.startActivity(Intent(ctx, PlateCheckActivity::class.java).putExtra(PlateCheckActivity.EXTRA_BOOKED, s.plate))
                }, Modifier.weight(1f)) { Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.check_plate)) }
                PlateButton.RECORD -> FilledTonalButton(onClick = recordPlate, Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_plate))
                }
                PlateButton.NONE -> {}
            }
            // The first tap only arms it, and it disarms itself, so one stray tap can't stop the watching.
            if (endArmed) Button(onClick = { ctx.startForegroundService(TripService.intent(ctx, TripService.ACTION_STOP_TRIP)) }, Modifier.weight(1f)) {
                Icon(Icons.Rounded.StopCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.end_trip_armed))
            } else FilledTonalButton(onClick = { endArmed = true }, Modifier.weight(1f)) {
                Icon(Icons.Rounded.StopCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.end_trip))
            }
        }
        LineDiagram(s, minutes)
    }
}

/**
 * Her trip as a line: where it started, anything the watcher noticed, and her, with the watched
 * stretch stamped in ajrakh stars, one per minute (the newest few, once a trip runs long).
 */
@Composable
private fun LineDiagram(s: UiState, minutes: Int) {
    val c = Saathi.colors
    val ctx = LocalContext.current
    // When the trip stalls, the stop and her own station matter more than history: show fewer stars.
    val shown = minutes.coerceIn(0, if (s.checkInEndsAtMs != null || s.alertActive) 3 else 5)
    val firstShown = remember { shown }
    val stalled = s.checkInEndsAtMs != null || s.alertActive

    Column {
        // The trail carries the trip's own clock (replays run fast), so start and stops agree.
        val startMs = s.trail.firstOrNull()?.t?.times(1000) ?: s.startedAtMs
        // During an alert only the stop and her own station matter; the history steps aside.
        if (!s.alertActive) {
        val plateMatched = stringResource(R.string.line_plate_matched)
        val plateMismatch = stringResource(R.string.line_plate_mismatch)
        Station(ring = c.fg3,
            title = if (startMs > 0) stringResource(R.string.line_started, ctx.clockTime(startMs)) else stringResource(R.string.line_started_plain),
            sub = listOfNotNull(s.app, s.plateCheck?.let { if (parsePlateCheck(it)?.matched ?: it.startsWith("match")) plateMatched else plateMismatch })
                .joinToString(" · ").ifBlank { null },
            dim = true)
        Row {
            // The stars sit on a hairline rail, so they read as one thread, not loose beads.
            val rail = c.starOff
            Column(Modifier.width(26.dp).drawBehind {
                drawLine(rail, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx())
            }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                // A long trip shows its newest few; two faint stars say the thread runs further back.
                if (minutes > shown) { Star(c.starOff, 8.dp); Star(c.starOff, 10.dp) }
                repeat(shown) { i ->
                    val newest = i == shown - 1
                    // A slight, fixed tilt per star, like a hand-placed block.
                    ThreadStar(newest && stalled, stamp = i >= firstShown, tilt = ((i * 37) % 9 - 4).toFloat())
                }
                if (shown == 0) Box(Modifier.width(3.dp).height(18.dp).background(c.starOff, RoundedCornerShape(2.dp)))
            }
            // Only the newest stars fit; say how much of the thread runs further back.
            if (minutes > shown) Text(pluralStringResource(R.plurals.thread_earlier, minutes - shown, minutes - shown), style = MaterialTheme.typography.bodySmall, color = c.fg2,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp))
        }
        }
        s.events.forEach { e ->
            Station(ring = c.alum, title = ctx.eventText(e).capitalised(), sub = ctx.clockTime(e.at * 1000), dim = false, filled = true)
        }
        Station(ring = MaterialTheme.colorScheme.primary,
            title = s.place.label?.let { stringResource(R.string.line_you_at, it) } ?: stringResource(R.string.line_you),
            sub = when {
                s.alertActive -> stringResource(R.string.raised_told)
                s.checkInEndsAtMs != null -> stringResource(R.string.line_checking_in)
                s.events.isEmpty() -> pluralStringResource(R.plurals.line_min_watched_quiet, minutes, minutes)
                else -> pluralStringResource(R.plurals.line_min_watched, minutes, minutes)
            }, dim = false, filled = true, big = true)
        if (s.app != null) Text(stringResource(R.string.line_ends_with_ride, s.app), style = MaterialTheme.typography.bodySmall,
            color = c.fg2, modifier = Modifier.padding(start = 40.dp, top = 6.dp))
        // Arriving ends the trip on its own, so say so while it's true.
        else s.destLabel?.let { Text(stringResource(R.string.line_ends_at_place, ctx.placeLabel(it)), style = MaterialTheme.typography.bodySmall,
            color = c.fg2, modifier = Modifier.padding(start = 40.dp, top = 6.dp)) }
    }
}

/**
 * One watched minute on the thread. When the trip stalls, the newest turns alum with one settle, so she sees
 * where it changed; the same call site for every star, so nothing re-stamps when the thread shortens.
 */
@Composable
private fun ThreadStar(stalledHere: Boolean, stamp: Boolean, tilt: Float) {
    val c = Saathi.colors
    val color by animateColorAsState(if (stalledHere) c.alum else c.starOn, tween(motionMs(240)), label = "stall")
    val settle = remember { Animatable(1f) }
    // Already stalled when first shown (opening the app mid check-in) is not a change, so it doesn't settle.
    val wasStalled = remember { stalledHere }
    val ms = motionMs(320)
    LaunchedEffect(stalledHere) {
        if (stalledHere && !wasStalled && ms > 0) { settle.snapTo(1.35f); settle.animateTo(1f, tween(ms, easing = Arrive)) }
    }
    Star(color, 14.dp, stamp = stamp, tilt = tilt, modifier = Modifier.scale(settle.value))
}

@Composable
private fun Station(ring: Color, title: String, sub: String?, dim: Boolean, filled: Boolean = false, big: Boolean = false) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(26.dp).padding(top = 3.dp), contentAlignment = Alignment.Center) {
            val d = if (big) 18.dp else 14.dp
            Box(Modifier.size(d).clip(CircleShape).background(if (filled) ring else MaterialTheme.colorScheme.background)
                .border(3.dp, ring, CircleShape))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = if (dim) Saathi.colors.fg2 else MaterialTheme.colorScheme.onBackground)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Saathi.colors.fg2) }
        }
    }
}

/** Opens the camera to read a plate (no booking to compare with) and hands back the plate she accepts. */
@Composable
private fun rememberPlateRecorder(onPlate: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) r.data?.getStringExtra(PlateCheckActivity.EXTRA_PLATE_RESULT)?.let(onPlate)
    }
    return { launcher.launch(Intent(ctx, PlateCheckActivity::class.java).putExtra(PlateCheckActivity.EXTRA_RECORD, true)) }
}

@Composable
private fun Ready() {
    val ctx = LocalContext.current
    val c = Saathi.colors
    var autoOpen by remember { mutableStateOf(false) }
    var plate by remember { mutableStateOf("") }
    var perms by remember { mutableStateOf(readAllPerms(ctx)) }
    // Back from system settings, she should see the fix land.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { perms = readAllPerms(ctx) }
    val ridesOn = perms[Perm.RIDES] == true
    val photographPlate = rememberPlateRecorder { plate = it }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The promise is only made when it is true: with ride access off, she is asked to start trips herself.
        Text(stringResource(if (ridesOn) R.string.ready_title_rides else R.string.ready_title_manual), style = MaterialTheme.typography.titleLarge)
        StoppedNotice()
        ReadyStatus(perms) { perms = readAllPerms(ctx) }
        // Three by two, so six chips stay wide enough to read and grow with the font size.
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(Icons.AutoMirrored.Rounded.DirectionsWalk, stringResource(R.string.mode_walk), Modifier.weight(1f)) { startTrip(ctx, "WALK") }
            ModeChip(Icons.Rounded.DirectionsBus, stringResource(R.string.mode_bus), Modifier.weight(1f)) { startTrip(ctx, "BUS") }
            ModeChip(Icons.Rounded.Train, stringResource(R.string.mode_train), Modifier.weight(1f)) { startTrip(ctx, "TRAIN") }
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(Icons.Rounded.ElectricRickshaw, stringResource(R.string.mode_auto), Modifier.weight(1f), selected = autoOpen) { autoOpen = !autoOpen }
            ModeChip(Icons.Rounded.LocalTaxi, stringResource(R.string.mode_cab), Modifier.weight(1f)) { startTrip(ctx, "CAB") }
            ModeChip(Icons.Rounded.TwoWheeler, stringResource(R.string.mode_bike), Modifier.weight(1f)) { startTrip(ctx, "BIKE") }
        }
        if (autoOpen) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The quickest, safest way to get a hailed auto's plate is a photo while she gets in.
            FilledTonalButton(onClick = photographPlate, Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ready_plate_photo), style = MaterialTheme.typography.titleSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(plate, { plate = it.uppercase() }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.ready_plate_label)) })
                Button(onClick = { startTrip(ctx, "AUTO", plate.ifBlank { null }); autoOpen = false }, Modifier.height(56.dp)) { Text(stringResource(R.string.ready_start)) }
            }
        }
    }
}

/** Is she actually armed? Reads the real permission state, and a missing one is one tap from its fix. */
@Composable
private fun ReadyStatus(perms: Map<Perm, Boolean>, onRefresh: () -> Unit) {
    val ctx = LocalContext.current
    val c = Saathi.colors
    val missing = firstMissingRequired(perms)
    val fix: Perm? = missing ?: if (perms[Perm.RIDES] != true) Perm.RIDES else null
    // One full sentence per permission, so a translation never has to slot a title into a template.
    val text = stringResource(if (fix != null) permOffText(fix) else R.string.status_ready)
    val open = {
        if (fix == Perm.KEEP_RUNNING) openBackgroundSettings(ctx)
        else ctx.startActivity(
            if (fix == Perm.RIDES) Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            else Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
        )
    }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .then(if (fix != null) Modifier.background(c.alum.copy(alpha = 0.16f)) else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (fix != null) Modifier.clickable(role = Role.Button, onClick = open) else Modifier)
                .heightIn(min = 48.dp).padding(horizontal = if (fix != null) 14.dp else 0.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(8.dp).background(if (fix != null) c.alum else c.ok, CircleShape))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = if (fix != null) MaterialTheme.colorScheme.onBackground else c.fg2)
            if (fix != null) Text(stringResource(R.string.status_fix), style = MaterialTheme.typography.labelLarge, color = c.alum)
        }
        // Android can't tell us she changed her phone maker's switch, so she says so here too.
        if (fix == Perm.KEEP_RUNNING) TextButton(
            onClick = { RunState.setBackgroundDone(ctx, true); onRefresh() },
            Modifier.padding(start = 6.dp).heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.status_keep_done)) }
    }
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

@Composable
private fun ModeChip(icon: ImageVector, label: String, modifier: Modifier, selected: Boolean = false, onClick: () -> Unit) {
    // At least 72dp, and the same height as its neighbours in the row, so a large font grows them together.
    Column(
        modifier.heightIn(min = 72.dp).fillMaxHeight().clip(MaterialTheme.shapes.medium)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Help on the left, larger; the companion call beside it. Speak for me's tile is off until the feature is validated. */
@Composable
private fun HelpRow(settings: Settings, quiet: Boolean = false) {
    val ctx = LocalContext.current
    // Grows with the system font size instead of clipping her contacts' names.
    Row(Modifier.padding(horizontal = 16.dp).heightIn(min = 140.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Her hold becomes the alert: the madder plate dissolves into the Call 112 plate in the same place.
        Crossfade(quiet, Modifier.weight(1.15f).fillMaxHeight(), animationSpec = tween(motionMs(UNFOLD_MS)), label = "help-plate") { q ->
            HoldForHelp(settings, Modifier.fillMaxSize(), q)
        }
        // Speak for me tile, off until validated:
        // Tile(Icons.Rounded.RecordVoiceOver, stringResource(R.string.tile_speak), Modifier.weight(1f)) { ctx.startActivity(Intent(ctx, SpeakForMeActivity::class.java)) }
        Tile(Icons.Rounded.Call, stringResource(R.string.tile_call), Modifier.weight(1f).fillMaxHeight()) { ctx.startActivity(Intent(ctx, CompanionActivity::class.java)) }
    }
}

/** One tall tile: icon over label, anchored bottom-left to echo the Help plate beside it. */
@Composable
private fun Tile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick).padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Hold for 2 seconds to raise help, so a brush against the screen doesn't. The plate fills as you hold. */
@Composable
// quiet: help was already raised, so I'm okay owns the screen and this steps back to a tonal plate.
private fun HoldForHelp(settings: Settings, modifier: Modifier, quiet: Boolean = false) {
    // Once help is raised, holding again does nothing, so the plate becomes the next real step: Call 112.
    if (quiet) { Call112Plate(modifier); return }
    val c = Saathi.colors
    val scope = rememberCoroutineScope()
    val fill = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    // Name who will really be texted: the same city-aware list the alert uses, not just the first two contacts.
    val named = recipients(settings.contacts, TripState.city()).map { it.name }
    val ctx = LocalContext.current
    val who = if (named.isEmpty()) "" else ctx.namesText(named.take(2)) + if (named.size > 2) " +${named.size - 2}" else ""
    var confirmNow by remember { mutableStateOf(false) }
    val spoken = if (who.isNotBlank()) stringResource(R.string.help_spoken, who) else stringResource(R.string.help_spoken_none)
    val raiseLabel = stringResource(R.string.help_action)
    Box(
        modifier.clip(RoundedCornerShape(32.dp)).background(c.help)
            // TalkBack and Switch Access cannot hold, so they get one deliberate activation, confirmed first.
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = spoken
                onClick(label = raiseLabel) { confirmNow = true; true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    holding = true
                    var job: Job? = null
                    job = scope.launch {
                        fill.snapTo(0f); fill.animateTo(1f, tween(2000, easing = LinearEasing))
                        AlertManager.raise(Reason.HOLD)
                        // Fired: stop saying "Keep holding" while her finger is still down.
                        holding = false
                    }
                    tryAwaitRelease()
                    job.cancel(); holding = false
                    scope.launch { fill.animateTo(0f, tween(250)) }
                })
            },
    ) {
        if (confirmNow) AlertDialog(
            onDismissRequest = { confirmNow = false },
            title = { Text(stringResource(R.string.help_dialog_title)) },
            text = { Text(if (who.isNotBlank()) stringResource(R.string.help_dialog_text, who) else stringResource(R.string.help_dialog_text_none)) },
            // Filled madder with white text (about 5:1); red text on the dark dialog surface was about 3:1.
            confirmButton = { Button({ confirmNow = false; AlertManager.raise(Reason.HOLD) }, Modifier.heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.help, contentColor = Color.White)) { Text(stringResource(R.string.help_dialog_send)) } },
            dismissButton = { FilledTonalButton({ confirmNow = false }, Modifier.heightIn(min = 52.dp)) { Text(stringResource(R.string.help_dialog_cancel)) } },
        )
        Box(Modifier.fillMaxHeight().fillMaxWidth(fill.value).background(Color.Black.copy(alpha = 0.22f)))
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.Bottom) {
            Column {
                Text(stringResource(if (holding) R.string.help_holding else R.string.help_title), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(if (who.isNotBlank()) stringResource(R.string.help_sub, who) else stringResource(R.string.help_sub_none),
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.92f), // ~4.9:1 on madder; the old tint was about 4.2:1
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/**
 * Help is already raised, so the plate in her thumb's reach becomes the next real step: Call 112, as on
 * check-in and the Help screen. It opens the dialer with 112 filled in; she presses call (apps can't place
 * emergency calls). Madder container, not madder, so I'm okay stays the one reversed plate.
 */
@Composable
private fun Call112Plate(modifier: Modifier) {
    val c = Saathi.colors
    val ctx = LocalContext.current
    Column(
        modifier.clip(RoundedCornerShape(32.dp)).background(c.helpContainer)
            .clickable(role = Role.Button) { runCatching { ctx.startActivity(Notifier.dial112()) } }
            .padding(18.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Icon(Icons.Rounded.Call, null, tint = c.onHelpContainer, modifier = Modifier.size(28.dp))
        Text(stringResource(R.string.call_112), style = MaterialTheme.typography.headlineMedium, color = c.onHelpContainer,
            modifier = Modifier.padding(top = 8.dp))
    }
}

/** After a trip: two taps to log a small thing, so "nothing major" still leaves a trace (RE-7). */
@Composable
private fun AfterTrip(settings: Settings, onChange: (Settings) -> Unit) {
    val ctx = LocalContext.current
    val s by TripState.ui.collectAsState()
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val records = remember(s.onTrip, refresh) { Store.records(ctx) }
    // The "anything felt off?" question is about a trip; an alert record has no trip to ask about.
    val latest = records.lastOrNull { it.mode != ALERT_RECORD }
    // Asked once, in context, right after she has answered for her first trip.
    if (!s.onTrip && !settings.consentAsked && latest != null && latest.feltOff != null) {
        ConsentCard(
            onYes = { stats ->
                if (stats) {
                    app.saathi.data.TelemetryManager.releaseHeld(ctx)
                } else {
                    app.saathi.data.TelemetryManager.dropHeld(ctx)
                }
                onChange(settings.copy(communityConsent = true, consentAsked = true, usageStats = stats))
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    if (latest.plate != null) app.saathi.data.Api.ride(ctx, latest.plate, latest.createdAt, latest.caseId)
                    if (latest.feltOff != "nothing" && latest.lat != null && latest.lng != null) {
                        app.saathi.data.Api.point(ctx, latest.lat, latest.lng, latest.endedAt ?: latest.createdAt,
                            latest.feltOff, latest.plate, latest.caseId)
                    }
                }
            },
            onNo = { stats ->
                app.saathi.data.TelemetryManager.dropHeld(ctx)
                onChange(settings.copy(consentAsked = true, usageStats = stats))
            },
        )
    }
    latest?.takeIf { it.feltOff == null && !s.onTrip }?.let { last ->
        FeltOff { kind ->
            Store.saveRecord(ctx, last.copy(feltOff = kind))
            val consent = settings.communityConsent
            if (kind != "nothing" && consent && last.lat != null && last.lng != null) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    app.saathi.data.Api.point(ctx, last.lat, last.lng, last.endedAt ?: last.createdAt, kind, last.plate, last.caseId)
                }
            }
            if (kind == "something_happened") ctx.startActivity(Intent(ctx, GuideActivity::class.java)
                .putExtra(GuideActivity.EXTRA_CASE, last.caseId).putExtra(GuideActivity.EXTRA_KIND, "something happened"))
            refresh++
        }
    }
}

@Composable
private fun ConsentCard(onYes: (usageStats: Boolean) -> Unit, onNo: (usageStats: Boolean) -> Unit) {
    val c = Saathi.colors
    var stats by remember { mutableStateOf(false) }
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow).border(1.dp, c.hair, MaterialTheme.shapes.large).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.consent_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.consent_body),
            style = MaterialTheme.typography.bodyMedium, color = c.fg2)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = stats, role = Role.Checkbox, onValueChange = { stats = it }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(checked = stats, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())
            Text(stringResource(R.string.consent_stats), style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { onYes(stats) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.consent_yes)) }
            TextButton(onClick = { onNo(stats) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.consent_no)) }
        }
    }
}

@Composable
private fun FeltOff(onPick: (String) -> Unit) {
    val c = Saathi.colors
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow).border(1.dp, c.hair, MaterialTheme.shapes.large).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.felt_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.felt_sub), style = MaterialTheme.typography.bodyMedium, color = c.fg2)
        Spacer(Modifier.height(4.dp))
        FilledTonalButton(onClick = { onPick("nothing") }, Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.felt_nothing)) }
        FilledTonalButton(onClick = { onPick("felt_off") }, Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.felt_off)) }
        Button(onClick = { onPick("something_happened") }, Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.help, contentColor = Color.White)) { Text(stringResource(R.string.felt_happened)) }
    }
}

/** One thing that happened, for the Activity timeline: a trip, an alert raised with no trip, or an intro text. */
private sealed interface Happening { val atMs: Long }
private class TripHappening(val r: TripRecord) : Happening { override val atMs = r.createdAt * 1000 }
private class AlertHappening(val r: TripRecord) : Happening { override val atMs = r.createdAt * 1000 }
private class IntroHappening(val i: IntroRecord) : Happening { override val atMs = i.at * 1000 }

/**
 * Everything Saathi did for her, newest first, under a heading per day: trips, alerts raised with no trip running,
 * and the texts that told her contacts about Saathi. The list says what happened in one row each; a tap opens the
 * record, where the fingerprints, the reporting draft and delete live. System Back returns to the list.
 */
@Composable
private fun ActivityTab() {
    val ctx = LocalContext.current
    val s by TripState.ui.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    // Re-read when an alert or trip starts or ends, so what just happened shows without leaving the tab.
    val items = remember(refresh, s.alertActive, s.onTrip, s.told, s.smsFailed) {
        (Store.records(ctx).map { if (it.mode == ALERT_RECORD) AlertHappening(it) else TripHappening(it) } +
            Store.intros(ctx).map { IntroHappening(it) }).sortedByDescending { it.atMs }
    }
    // The newest alert record with no end yet is the one still going, if one is.
    val liveAlert = if (s.alertActive) items.firstNotNullOfOrNull { (it as? AlertHappening)?.r?.takeIf { r -> r.endedAt == null }?.caseId } else null
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = open != null) { open = null }
    val listScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val still = reduceMotion()
    AnimatedContent(
        open,
        transitionSpec = {
            if (still) EnterTransition.None togetherWith ExitTransition.None
            else {
                // Shared axis, as in Settings: a record comes in from the side it belongs to.
                val dir = if (targetState == null) -1 else 1
                (slideInHorizontally(tween(260, easing = Arrive)) { it * dir / 5 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(200)) { -it * dir / 5 } + fadeOut(tween(120)))
            }
        },
        label = "activity",
    ) { caseId ->
        val record = caseId?.let { id ->
            items.firstNotNullOfOrNull { h -> when (h) { is TripHappening -> h.r; is AlertHappening -> h.r; else -> null }?.takeIf { it.caseId == id } }
        }
        if (record != null) RecordPage(
            record, live = record.caseId == liveAlert, onBack = { open = null },
            onGuide = { ctx.startActivity(Intent(ctx, GuideActivity::class.java).putExtra(GuideActivity.EXTRA_CASE, record.caseId)) },
            onDelete = {
                // Gone from the phone now; its anonymous community share goes too, when the server can be reached.
                Store.deleteRecord(ctx, record.caseId)
                scope.launch(kotlinx.coroutines.Dispatchers.IO) { app.saathi.data.Api.deleteMine(ctx, record.caseId) }
                open = null; refresh++
            },
        ) else ActivityList(items, liveAlert, listScroll) { open = it }
    }
}

@Composable
private fun ActivityList(items: List<Happening>, liveAlert: String?, scroll: androidx.compose.foundation.ScrollState, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val c = Saathi.colors
    Column(Modifier.fillMaxSize().verticalScroll(scroll).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.trips_title), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp))
        if (items.isEmpty()) Text(stringResource(R.string.trips_empty),
            style = MaterialTheme.typography.bodyLarge, color = c.fg2, modifier = Modifier.padding(horizontal = 4.dp))
        items.groupBy { ctx.dayHeading(it.atMs) }.forEach { (day, list) ->
            Text(day, style = MaterialTheme.typography.labelLarge, color = c.fg2, modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 4.dp))
            list.forEachIndexed { i, h ->
                when (h) {
                    is TripHappening -> TripRow(h.r) { onOpen(h.r.caseId) }
                    is AlertHappening -> AlertRow(h.r, live = h.r.caseId == liveAlert) { onOpen(h.r.caseId) }
                    is IntroHappening -> IntroRow(h.i)
                }
                if (i < list.lastIndex) HorizontalDivider(thickness = 1.dp, color = c.hair, modifier = Modifier.padding(start = 54.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * One row of the timeline: a 36dp marker, what happened, when, and one line of what came of it. The whole row opens the
 * record; a row with nothing to open (an intro text) has no chevron.
 */
@Composable
private fun HappeningRow(
    marker: @Composable () -> Unit, title: String, time: String, summary: String?, summaryColor: Color,
    onClick: (() -> Unit)?, trailing: (@Composable () -> Unit)? = null,
) {
    val c = Saathi.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        marker()
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium)
                Text(time, style = MaterialTheme.typography.bodySmall, color = c.fg2)
            }
            summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = summaryColor, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
        }
        trailing?.invoke()
        if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.fg2)
    }
}

/** A round 36dp marker: an icon on a tinted disc. */
@Composable
private fun Marker(icon: ImageVector, disc: Color, tint: Color) {
    Box(Modifier.size(36.dp).background(disc, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

private fun modeIcon(mode: Mode?): ImageVector = when (mode) {
    Mode.BUS -> Icons.Rounded.DirectionsBus
    Mode.TRAIN -> Icons.Rounded.Train
    Mode.AUTO -> Icons.Rounded.ElectricRickshaw
    Mode.CAB -> Icons.Rounded.LocalTaxi
    Mode.BIKE -> Icons.Rounded.TwoWheeler
    else -> Icons.AutoMirrored.Rounded.DirectionsWalk
}

/** What came of an alert, in a few words: who was told (or that no one was), and when she said she was okay. */
@Composable
private fun alertOutcome(r: TripRecord, live: Boolean): Pair<String, Color> {
    val c = Saathi.colors
    val ctx = LocalContext.current
    val told = r.alertEntries.flatMap { it.told }.distinct()
    val first = if (told.isEmpty()) stringResource(R.string.activity_none) else stringResource(R.string.activity_told, ctx.namesText(told))
    val then = when {
        r.endedAt != null -> stringResource(R.string.activity_okay_short, ctx.clockTime(r.endedAt * 1000))
        live -> stringResource(R.string.activity_live_short)
        else -> null
    }
    return listOfNotNull(first, then).joinToString(" · ") to when { told.isEmpty() -> c.help; live -> c.alum; else -> c.fg2 }
}

@Composable
private fun TripRow(r: TripRecord, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val c = Saathi.colors
    val mode = runCatching { Mode.valueOf(r.mode) }.getOrNull()
    val alerts = r.alertEntries.ifEmpty { r.alerts.mapNotNull { app.saathi.data.legacyAlertEntry(it) } }
    // An alert on the trip is the thing to see; otherwise what the watcher noticed.
    val (summary, color) = if (alerts.isNotEmpty()) alertOutcome(r.copy(alertEntries = alerts), live = false)
    else (r.eventItems.map { ctx.eventText(it) }.ifEmpty { r.events }).take(2).joinToString(" · ") { it.capitalised() }
        .ifEmpty { null }.let { it to c.fg2 }
    HappeningRow(
        marker = { Marker(modeIcon(mode), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) },
        title = mode?.let { stringResource(modeNameRes(it)) } ?: r.mode, time = ctx.clockTime(r.createdAt * 1000),
        summary = summary, summaryColor = color, onClick = onClick,
    )
}

@Composable
private fun AlertRow(r: TripRecord, live: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val c = Saathi.colors
    val (summary, color) = alertOutcome(r, live)
    HappeningRow(
        marker = { Marker(Icons.Rounded.Sos, c.help, Color.White) },
        title = stringResource(alertReasonRes(r.alertEntries.firstOrNull()?.reason)), time = ctx.clockTime(r.createdAt * 1000),
        summary = summary, summaryColor = color, onClick = onClick,
    )
}

/** The text that told her contacts about Saathi: nothing to open. */
@Composable
private fun IntroRow(i: IntroRecord) {
    val ctx = LocalContext.current
    val c = Saathi.colors
    HappeningRow(
        marker = { Marker(Icons.Rounded.Sms, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.primary) },
        title = stringResource(R.string.activity_intro, ctx.namesText(i.to)), time = ctx.clockTime(i.at * 1000),
        summary = null, summaryColor = c.fg2, onClick = null,
    )
}

/**
 * One record, opened from the list: everything it holds, in the order it happened, then its sealed fingerprints, the
 * reporting draft, and delete.
 */
@Composable
private fun RecordPage(r: TripRecord, live: Boolean, onBack: () -> Unit, onGuide: () -> Unit, onDelete: () -> Unit) {
    val c = Saathi.colors
    val ctx = LocalContext.current
    val isAlert = r.mode == ALERT_RECORD
    val mode = runCatching { Mode.valueOf(r.mode) }.getOrNull()
    val title = if (isAlert) stringResource(alertReasonRes(r.alertEntries.firstOrNull()?.reason))
        else mode?.let { stringResource(modeNameRes(it)) } ?: r.mode
    val alerts = r.alertEntries.ifEmpty { r.alerts.mapNotNull { app.saathi.data.legacyAlertEntry(it) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
            Text(title, Modifier.weight(1f).padding(start = 4.dp, end = 16.dp), style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ctx.dayAndTime(r.createdAt * 1000), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.fg2)
                r.plate?.let { PlateText(it) }
            }
            Spacer(Modifier.height(4.dp))
            (r.eventItems.map { ctx.eventText(it) }.ifEmpty { r.events }).forEach { Text(it.capitalised(), style = MaterialTheme.typography.bodyLarge) }
            alerts.forEach { a ->
                val at = ctx.clockTime(a.at * 1000)
                Text(
                    when {
                        a.told.isEmpty() -> stringResource(R.string.trips_alert_none, at)
                        a.retry -> stringResource(R.string.trips_alert_retry, at, ctx.namesText(a.told))
                        else -> stringResource(R.string.trips_alert_sent, at, ctx.namesText(a.told))
                    },
                    style = MaterialTheme.typography.bodyLarge, color = if (a.told.isEmpty()) c.help else MaterialTheme.colorScheme.onBackground,
                )
            }
            if (alerts.isEmpty() && r.alerts.isNotEmpty()) r.alerts.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            if (isAlert) when {
                r.endedAt != null -> Text(stringResource(R.string.activity_okay_at, ctx.clockTime(r.endedAt * 1000)),
                    style = MaterialTheme.typography.bodyLarge, color = c.fg2)
                live -> Text(stringResource(R.string.activity_alert_live), style = MaterialTheme.typography.bodyLarge, color = c.alum)
            }
            r.plateCheck?.let { raw ->
                val pc = parsePlateCheck(raw)
                Text(
                    when {
                        pc == null -> stringResource(R.string.trips_plate_other, raw)
                        pc.matched -> stringResource(R.string.trips_plate_matched, pc.seen)
                        else -> stringResource(R.string.trips_plate_mismatch, pc.seen, pc.booked.orEmpty())
                    },
                    style = MaterialTheme.typography.bodyLarge, color = c.fg2,
                )
            }
            Spacer(Modifier.height(8.dp))
            SealedRows(r)
            // While an alert is still going its record is still being written, so it can't be deleted yet.
            RecordActions(onGuide, onDelete.takeUnless { live })
        }
    }
}

/** One line per sealed file: what it is on the left, its fingerprint on the right, so long names never push a hash down. */
@Composable
private fun SealedRows(r: TripRecord) {
    if (r.evidence.isEmpty()) return
    val c = Saathi.colors
    val ctx = LocalContext.current
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.trips_sealed), style = MaterialTheme.typography.labelSmall, color = c.fg2)
        r.evidence.forEach {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ctx.evidenceName(it.name), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.fg2,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${it.sha256.take(12)}…", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = c.fg2)
            }
        }
    }
}

/** The report is the action; delete is quieter and sits below, so neither squeezes the other. No [onDelete], no delete. */
@Composable
private fun RecordActions(onGuide: () -> Unit, onDelete: (() -> Unit)?) {
    val c = Saathi.colors
    var confirm by remember { mutableStateOf(false) }
    // An armed delete disarms itself, so a stray tap later can't erase a record.
    LaunchedEffect(confirm) { if (confirm) { delay(4_000); confirm = false } }
    FilledTonalButton(onClick = onGuide, Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.trips_guide), textAlign = TextAlign.Center)
    }
    if (onDelete != null) TextButton(
        onClick = { if (confirm) onDelete() else confirm = true },
        Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp),
        // No side padding: the word lines up with the card's text above; the 48dp target stays.
        contentPadding = PaddingValues(vertical = 8.dp),
        // Armed, it takes the attention colour: one more tap and the record is gone for good.
        colors = ButtonDefaults.textButtonColors(contentColor = if (confirm) c.alum else MaterialTheme.colorScheme.primary),
    ) { Text(stringResource(if (confirm) R.string.trips_delete_armed else R.string.trips_delete)) }
}

@Composable
private fun DemoPanel(settings: Settings, onChange: (Settings) -> Unit) {
    val ctx = LocalContext.current
    var cityOverride by remember { mutableStateOf("") }
    val isTest = TestDevice.resolve(settings.testDevice, BuildConfig.DEBUG)
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                value = isTest, role = Role.Switch,
                onValueChange = { onChange(settings.copy(testDevice = it)); TestDevice.update(it) },
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("This is a test phone", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Its trips, reports and stats are kept out of real numbers.",
                    style = MaterialTheme.typography.bodyMedium, color = Saathi.colors.fg2,
                )
            }
            Switch(checked = isTest, onCheckedChange = null)
        }
        Text("Demo panel (sample data)", style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { startTrip(ctx, "AUTO", "KA05AB1234", "Uber", replay = "blr-auto") }) { Text("Replay: Bengaluru Uber auto, 30×") }
        OutlinedButton(onClick = { startTrip(ctx, "BUS", replay = "pune-bus") }) { Text("Replay: Pune bus, 30×") }
        Text("Fake ride-app notifications", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { Notifier.demoRide(ctx, "Ravi is on the way", "Auto KA 05 AB 1234 · arriving in 4 min") }) { Text("Booked") }
            OutlinedButton(onClick = { Notifier.demoRide(ctx, "Your auto has arrived", "KA 05 AB 1234 is waiting at pickup") }) { Text("Arrived") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { Notifier.demoRide(ctx, "Ravi is on the way", "Auto KA 05 XY 9876 · arriving in 3 min") }) { Text("Flagged auto") }
            OutlinedButton(onClick = { Notifier.demoRide(ctx, "Trip completed", "Rate your driver") }) { Text("Ended") }
        }
        OutlinedTextField(cityOverride, { cityOverride = it }, singleLine = true, label = { Text("Pretend I'm in (city)") })
        OutlinedButton(onClick = {
            TripState.update { it.copy(cityOverride = cityOverride.ifBlank { null },
                place = it.place.copy(state = stateFor(cityOverride) ?: it.place.state, countryCode = "IN")) }
        }) { Text("Apply city") }
    }
}

private fun stateFor(city: String) = when (city.trim().lowercase()) {
    "bengaluru", "bangalore" -> "Karnataka"
    "pune", "mumbai" -> "Maharashtra"
    "delhi" -> "Delhi"
    else -> null
}
