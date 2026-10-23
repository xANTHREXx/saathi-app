package app.saathi.core

enum class Trigger {
    HOLD,
    TILE,
    SPEAK_FOR_ME,
    POWER_BUTTON,
    EARPHONE,
    LONG_STOP,
    OFF_ROUTE,
    LATE,
    CHECK_ON_ME,
}

data class AlertRules(
    val youAsk: List<Trigger>,
    val saathiAsks: List<Trigger>,
)

fun alertRules(powerPresses: Int, earphoneOn: Boolean): AlertRules {
    val you = mutableListOf(Trigger.HOLD, Trigger.TILE, Trigger.SPEAK_FOR_ME)
    if (powerPresses > 0) you.add(Trigger.POWER_BUTTON)
    if (earphoneOn) you.add(Trigger.EARPHONE)
    val saathi = listOf(Trigger.LONG_STOP, Trigger.OFF_ROUTE, Trigger.LATE, Trigger.CHECK_ON_ME)
    return AlertRules(youAsk = you, saathiAsks = saathi)
}
