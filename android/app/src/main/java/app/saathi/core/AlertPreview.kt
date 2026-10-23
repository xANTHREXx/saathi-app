package app.saathi.core

import java.time.ZoneId

private val SAMPLE_FIX = Fix(12.97840, 77.64080, 1_760_801_520L)
private const val SAMPLE_PLATE = "KA05AB1234"
private const val SAMPLE_PLACE = "Indiranagar"
private const val SAMPLE_LIVE_URL = "https://xanthrexx.github.io/saathi-app/live/?t=demo"

/**
 * Builds the sample alert text shown during onboarding so she sees the exact message her contacts receive.
 */
fun previewAlert(name: String, zone: ZoneId = ZoneId.of("Asia/Kolkata")): String =
    alertText(
        name = name.ifBlank { "Your contact" },
        reason = Reason.NO_RESPONSE,
        plate = SAMPLE_PLATE,
        event = null,
        place = SAMPLE_PLACE,
        fix = SAMPLE_FIX,
        fixAgeS = 30L,
        liveUrl = SAMPLE_LIVE_URL,
        zone = zone,
    )
