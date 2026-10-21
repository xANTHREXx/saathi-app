package app.saathi.core

/** The line under the tile's name. Which words it takes is the service's job, so each language can phrase it. */
enum class TileSubtitle { CONTACTS_TOLD, TEXTING_SOON, FINISH_SETUP, TAP_TO_TEXT_NAME, TAP_TO_TEXT_CONTACTS }

/**
 * What the "Saathi help" Quick Settings tile shows. Pure, so it can be tested without a phone.
 * [name] is the one contact's name, only for [TileSubtitle.TAP_TO_TEXT_NAME].
 */
data class HelpTileLook(val active: Boolean, val subtitle: TileSubtitle, val name: String? = null)

/** What one tap on the tile does. */
enum class HelpTileAction { OPEN_APP, CANCEL_PENDING, REFRESH_ONLY, RAISE }

/** Set up means she finished setup and has someone to text. */
fun isSetUp(onboarded: Boolean, contacts: List<Contact>) = onboarded && contacts.isNotEmpty()

/**
 * The tile's subtitle and on/off look. A pending or sent alert wins over setup: if one is already
 * counting down or out, the tile must say so.
 */
fun helpTileLook(setUp: Boolean, contacts: List<Contact>, pending: Boolean, alertActive: Boolean): HelpTileLook = when {
    alertActive -> HelpTileLook(true, TileSubtitle.CONTACTS_TOLD)
    pending -> HelpTileLook(true, TileSubtitle.TEXTING_SOON)
    !setUp -> HelpTileLook(false, TileSubtitle.FINISH_SETUP)
    else -> {
        // One name only when there is one person; with several, naming the first would hide the others.
        val only = contacts.singleOrNull()?.name?.takeIf { it.isNotBlank() }
        if (only != null) HelpTileLook(false, TileSubtitle.TAP_TO_TEXT_NAME, only) else HelpTileLook(false, TileSubtitle.TAP_TO_TEXT_CONTACTS)
    }
}

/** Tapping never takes back a sent alert (that is I'm okay, in the app) and always lets her cancel a pending one. */
fun helpTileAction(setUp: Boolean, pending: Boolean, alertActive: Boolean): HelpTileAction = when {
    alertActive -> HelpTileAction.REFRESH_ONLY
    pending -> HelpTileAction.CANCEL_PENDING
    !setUp -> HelpTileAction.OPEN_APP
    else -> HelpTileAction.RAISE
}
