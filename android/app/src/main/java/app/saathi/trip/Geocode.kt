package app.saathi.trip

import android.content.Context
import android.location.Address
import android.location.Geocoder
import app.saathi.core.addressLine
import java.util.Locale

/** Reverse geocoding with the phone's own geocoder, shared by the trip screen and place setup. */
object Geocode {
    /** The nearest address for a point, or null when the phone can't look it up. Blocking: call on Dispatchers.IO. */
    fun lookup(ctx: Context, lat: Double, lng: Double): Address? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            @Suppress("DEPRECATION")
            Geocoder(ctx, Locale.ENGLISH).getFromLocation(lat, lng, 1)?.firstOrNull()
        }.getOrNull()
    }

    /** Short label for the trip screen, e.g. "BTM Lake East Road". */
    fun shortLabel(a: Address): String? =
        listOfNotNull(a.featureName?.takeIf { f -> f.any { it.isLetter() } && !f.contains("+") }, a.thoroughfare ?: a.subLocality)
            .distinct().joinToString(", ").ifBlank { null }

    /** Fuller line for a saved place, e.g. "12th Main, Indiranagar, Bengaluru". */
    fun placeLine(a: Address): String? = addressLine(listOf(a.featureName, a.thoroughfare, a.subLocality, a.locality))
}
