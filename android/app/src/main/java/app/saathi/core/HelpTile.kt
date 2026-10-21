package app.saathi.core

/** What the "Saathi help" Quick Settings tile shows. Pure, so it can be tested without a phone. */
data class HelpTileLook(val active: Boolean, val subtitle: String)

/** What one tap on the tile does. */
enum class HelpTileAction { OPEN_APP, CANCEL_PENDING, REFRESH_ONLY, RAISE }

/** Set up means she finished setup and has someone to text. */
fun isSetUp(onboarded: Boolean, contacts: List<Contact>) = onboarded && contacts.isNotEmpty()

/**
 * The tile's subtitle and on/off look. A pending or sent alert wins over setup: if one is already
 * counting down or out, the tile must say so.
 */
fun helpTileLook(setUp: Boolean, contacts: List<Contact>, pending: Boolean, alertActive: Boolean): HelpTileLook = when {
    alertActive -> HelpTileLook(true, "Contacts told")
    pending -> HelpTileLook(true, "Texting in 5 s · tap to stop")
    !setUp -> HelpTileLook(false, "Finish setup first")
    // One name only when there is one person; with several, naming the first would hide the others.
    else -> HelpTileLook(false, "Tap to text " + (contacts.singleOrNull()?.name?.takeIf { it.isNotBlank() } ?: "your contacts"))
}

/** Tapping never takes back a sent alert (that is I'm okay, in the app) and always lets her cancel a pending one. */
fun helpTileAction(setUp: Boolean, pending: Boolean, alertActive: Boolean): HelpTileAction = when {
    alertActive -> HelpTileAction.REFRESH_ONLY
    pending -> HelpTileAction.CANCEL_PENDING
    !setUp -> HelpTileAction.OPEN_APP
    else -> HelpTileAction.RAISE
}
