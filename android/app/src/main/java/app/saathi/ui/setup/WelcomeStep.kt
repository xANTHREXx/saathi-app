package app.saathi.ui.setup

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.saathi.BuildConfig
import app.saathi.R
import app.saathi.core.videoLink
import app.saathi.data.Onboarding
import app.saathi.data.TelemetryManager
import app.saathi.ui.onboarding.LoopingClip
import app.saathi.ui.theme.Saathi

@Composable
fun WelcomeStep(onStart: () -> Unit) {
    val ctx = LocalContext.current
    val c = Saathi.colors

    LaunchedEffect(Unit) {
        if (Onboarding.once(ctx, "setup_started")) {
            TelemetryManager.trackOnboarding(ctx, "setup_started")
        }
    }

    val videoUrl = videoLink(BuildConfig.ONBOARDING_VIDEO_URL)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LoopingClip(
            clip = R.raw.onb_welcome,
            poster = R.drawable.onb_welcome_poster,
            contentDescription = stringResource(R.string.onb_clip_welcome_desc),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WelcomeBenefitRow(stringResource(R.string.welcome_line_watch))
            WelcomeBenefitRow(stringResource(R.string.welcome_line_ask))
            WelcomeBenefitRow(stringResource(R.string.welcome_line_text))
        }

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

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(32.dp), // reversed plate
        ) {
            Text(
                text = stringResource(R.string.welcome_start),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

@Composable
private fun WelcomeBenefitRow(text: String) {
    val c = Saathi.colors
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = c.ok,
            modifier = Modifier.size(20.dp).padding(top = 2.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = c.fg2,
        )
    }
}
