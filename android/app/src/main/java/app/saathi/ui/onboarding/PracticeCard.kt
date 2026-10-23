package app.saathi.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.saathi.R
import app.saathi.alert.AlertManager
import app.saathi.core.PRACTICE_CARD_SNOOZE_MS
import app.saathi.core.practiceCardVisible
import app.saathi.data.Onboarding
import app.saathi.ui.theme.Saathi
import kotlinx.coroutines.delay

/**
 * A quiet card on Home offering a practice check-in until one is completed or 14 days elapse.
 * Snoozed for 3 days on "Not now". Follows the non-reversed plate and neutral styling of ReadyCard.
 */
@Composable
fun PracticeCard(
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val c = Saathi.colors

    var practiceDone by remember { mutableStateOf(Onboarding.practiceDone(ctx)) }
    var firstShownAt by remember { mutableStateOf(Onboarding.cardFirstShownAt(ctx)) }
    var snoozedUntil by remember { mutableStateOf(Onboarding.cardSnoozedUntil(ctx)) }

    LaunchedEffect(Unit) {
        if (firstShownAt == 0L) {
            val now = System.currentTimeMillis()
            Onboarding.setCardFirstShownAt(ctx, now)
            firstShownAt = now
        }
    }

    val visible = practiceCardVisible(
        practiceDone = practiceDone,
        firstShownAt = firstShownAt,
        snoozedUntil = snoozedUntil,
        nowMs = System.currentTimeMillis(),
    )
    if (!visible) return

    var startingSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(startingSeconds) {
        if (startingSeconds > 0) {
            delay(1000L)
            startingSeconds -= 1
        }
    }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, c.hair, MaterialTheme.shapes.large)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.practice_card_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            if (startingSeconds == 0) {
                TextButton(
                    onClick = {
                        val snooze = System.currentTimeMillis() + PRACTICE_CARD_SNOOZE_MS
                        Onboarding.setCardSnoozedUntil(ctx, snooze)
                        snoozedUntil = snooze
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.ready_card_not_now))
                }
            }
        }

        Text(
            text = stringResource(R.string.practice_card_body),
            style = MaterialTheme.typography.bodyMedium,
            color = c.fg2,
        )

        if (startingSeconds > 0) {
            Text(
                text = stringResource(R.string.practice_card_starting, startingSeconds),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            FilledTonalButton(
                onClick = {
                    startingSeconds = 5
                    AlertManager.startPractice(5000L, from = "home")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.setup_start_practice),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}
