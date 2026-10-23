package app.saathi.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.saathi.BuildConfig
import app.saathi.R
import app.saathi.alert.AlertManager
import app.saathi.core.alertRules
import app.saathi.core.videoLink
import app.saathi.data.Settings
import app.saathi.data.TelemetryManager
import app.saathi.ui.theme.Saathi
import kotlinx.coroutines.delay

private data class Situation(
    val titleRes: Int,
    val saathiRes: Int,
    val youRes: Int,
)

private val SITUATIONS = listOf(
    Situation(R.string.how_sit_stop_title, R.string.how_sit_stop_saathi, R.string.how_sit_stop_you),
    Situation(R.string.how_sit_route_title, R.string.how_sit_route_saathi, R.string.how_sit_route_you),
    Situation(R.string.how_sit_check_title, R.string.how_sit_check_saathi, R.string.how_sit_check_you),
    Situation(R.string.how_sit_urgent_title, R.string.how_sit_urgent_saathi, R.string.how_sit_urgent_you),
    Situation(R.string.how_sit_plate_title, R.string.how_sit_plate_saathi, R.string.how_sit_plate_you),
)

/**
 * Settings sub-page: Alert rules, 5 real situations (situation → what Saathi does → what she can do),
 * a practice button, and the video link when configured.
 */
@Composable
fun HowItWorksPage(settings: Settings) {
    val ctx = LocalContext.current
    val c = Saathi.colors

    LaunchedEffect(Unit) {
        TelemetryManager.trackOnboarding(ctx, "how_it_works_opened")
    }

    var startingSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(startingSeconds) {
        if (startingSeconds > 0) {
            delay(1000L)
            startingSeconds -= 1
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 1. Alert rules content with clips
        AlertRulesContent(
            rules = alertRules(settings.powerPresses, settings.headsetHelp),
        )

        // 2. Real situations
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.how_situations_title),
                style = MaterialTheme.typography.titleMedium,
            )
            SITUATIONS.forEach { sit ->
                SituationCard(sit)
            }
        }

        // 3. Practice button
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (startingSeconds > 0) {
                Text(
                    text = stringResource(R.string.practice_card_starting, startingSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                FilledTonalButton(
                    onClick = {
                        startingSeconds = 5
                        AlertManager.startPractice(5000L, from = "settings")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = stringResource(R.string.setup_start_practice),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        // 4. Video link if configured
        val videoUrl = videoLink(BuildConfig.ONBOARDING_VIDEO_URL)
        if (videoUrl != null) {
            Text(
                text = stringResource(R.string.welcome_video_link),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable {
                        TelemetryManager.trackOnboarding(ctx, "setup_video_opened")
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)))
                        }
                    }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SituationCard(sit: Situation) {
    val c = Saathi.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, c.hair, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(sit.titleRes),
            style = MaterialTheme.typography.titleSmall,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.how_what_saathi_does),
                style = MaterialTheme.typography.labelSmall,
                color = c.fg2,
            )
            Text(
                text = stringResource(sit.saathiRes),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.how_what_you_can_do),
                style = MaterialTheme.typography.labelSmall,
                color = c.fg2,
            )
            Text(
                text = stringResource(sit.youRes),
                style = MaterialTheme.typography.bodyMedium,
                color = c.fg2,
            )
        }
    }
}
