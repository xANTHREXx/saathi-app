package app.saathi.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.saathi.R
import app.saathi.alert.AlertManager
import app.saathi.core.AlertRules
import app.saathi.core.Trigger
import app.saathi.ui.theme.Saathi

/**
 * Shared content explaining exactly when Saathi texts her contacts.
 * Used on the Practice step of setup and on Settings › How Saathi works.
 */
@Composable
fun AlertRulesContent(
    rules: AlertRules,
    modifier: Modifier = Modifier,
) {
    val c = Saathi.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.rules_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        // Lane 1: You ask · goes straight away
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = c.helpContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoopingClip(
                        clip = R.raw.onb_you_ask,
                        poster = R.drawable.onb_you_ask_poster,
                        contentDescription = stringResource(R.string.onb_clip_you_ask_desc),
                        modifier = Modifier.size(160.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.rules_lane_you_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.onHelpContainer,
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rules.youAsk.forEach { trigger ->
                        val stringRes = when (trigger) {
                            Trigger.HOLD -> R.string.rules_trigger_hold
                            Trigger.TILE -> R.string.rules_trigger_tile
                            Trigger.SPEAK_FOR_ME -> R.string.rules_trigger_speak_for_me
                            Trigger.POWER_BUTTON -> R.string.rules_trigger_power
                            Trigger.EARPHONE -> R.string.rules_trigger_earphone
                            else -> return@forEach
                        }
                        RuleBullet(stringResource(stringRes), c.onHelpContainer)
                    }
                }
            }
        }

        // Lane 2: Saathi asks you first · only if you don't answer
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = c.alum.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, c.alum.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoopingClip(
                        clip = R.raw.onb_saathi_asks,
                        poster = R.drawable.onb_saathi_asks_poster,
                        contentDescription = stringResource(R.string.onb_clip_saathi_asks_desc),
                        modifier = Modifier.size(160.dp),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.rules_lane_saathi_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.rules_saathi_subtitle, AlertManager.CHECKIN_S.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.fg2,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rules.saathiAsks.forEach { trigger ->
                        val stringRes = when (trigger) {
                            Trigger.LONG_STOP -> R.string.rules_trigger_long_stop
                            Trigger.OFF_ROUTE -> R.string.rules_trigger_off_route
                            Trigger.LATE -> R.string.rules_trigger_late
                            Trigger.CHECK_ON_ME -> R.string.rules_trigger_check_on_me
                            else -> return@forEach
                        }
                        RuleBullet(stringResource(stringRes), MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // Closing rule sentence
        Text(
            text = stringResource(R.string.rules_closing_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = c.fg2,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun RuleBullet(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("•", style = MaterialTheme.typography.bodyMedium, color = color)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
