package app.saathi.core

/** Which plate button a trip offers: none, compare with the booking, or record the plate of a hailed vehicle. */
enum class PlateButton { NONE, COMPARE, RECORD }

/**
 * A ride-app trip with a booked plate compares against it. A hailed auto or cab (no ride app) has no
 * booking, so with no plate yet she can photograph one; once it is recorded there is nothing to check.
 */
fun plateButton(mode: Mode?,plate: String?, app: String?): PlateButton = when {
    app != null -> if (plate != null) PlateButton.COMPARE else PlateButton.NONE
    plate == null && (mode == Mode.AUTO || mode == Mode.CAB) -> PlateButton.RECORD
    else -> PlateButton.NONE
}
