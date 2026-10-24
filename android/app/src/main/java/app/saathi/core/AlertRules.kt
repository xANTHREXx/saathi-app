package app.saathi.core

enum class Trigger {
    HOLD,
    TILE,
    WIDGET,
    SHORTCUT,
    NOTIFICATION,
    COMPANION,
    PLATE_MISMATCH,
    POWER_BUTTON,
    EARPHONE,
    LONG_STOP,
    OFF_ROUTE,
    LATE,
    CHECK_ON_ME,
}

/** Texts that go to her contacts without an alert: plain, one line each, no alarm and no live link. */
enum class PlainText {
    BATTERY_LOW,
    PHONE_OFF,
    ARRIVED,
    IM_OKAY,
}

data class AlertRules(
    val youAsk: List<Trigger>,
    val saathiAsks: List<Trigger>,
    val plainTexts: List<PlainText>,
)

fun alertRules(powerPresses: Int, earphoneOn: Boolean): AlertRules {
    val you = mutableListOf(
        Trigger.HOLD, Trigger.TILE, Trigger.WIDGET, Trigger.SHORTCUT,
        Trigger.NOTIFICATION, Trigger.COMPANION, Trigger.PLATE_MISMATCH,
    )
    if (powerPresses > 0) you.add(Trigger.POWER_BUTTON)
    if (earphoneOn) you.add(Trigger.EARPHONE)
    val saathi = listOf(Trigger.LONG_STOP, Trigger.OFF_ROUTE, Trigger.LATE, Trigger.CHECK_ON_ME)
    val plain = listOf(PlainText.BATTERY_LOW, PlainText.PHONE_OFF, PlainText.ARRIVED, PlainText.IM_OKAY)
    return AlertRules(youAsk = you, saathiAsks = saathi, plainTexts = plain)
}
