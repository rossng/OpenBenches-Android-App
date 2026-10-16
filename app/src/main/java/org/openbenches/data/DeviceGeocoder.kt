package org.openbenches.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * A place name for a bench from the device's own geocoder (no API key), for servers that don't
 * return the stored address yet. Returns null if the device has no geocoder or it finds nothing.
 */
suspend fun deviceAddress(context: Context, latitude: Double, longitude: Double): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.getDefault())
    val address: Address? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                    override fun onError(errorMessage: String?) = cont.resume(null)
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
        }
    } catch (e: Exception) {
        null
    }
    return address?.let {
        formatPlace(it.subLocality, it.locality, it.subAdminArea, it.adminArea, it.countryName)
    }
}

/**
 * Joins place parts from smallest to largest, skipping blanks and repeats, like the website's
 * addresses ("Bath, Bath and North East Somerset, England, United Kingdom"). Street names and
 * house numbers are left out on purpose: benches are rarely at one.
 */
fun formatPlace(vararg parts: String?): String? =
    parts.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
        .joinToString(", ")
        .takeIf { it.isNotEmpty() }
