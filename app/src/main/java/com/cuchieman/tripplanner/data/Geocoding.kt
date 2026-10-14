package com.cuchieman.tripplanner.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GeoResult(val name: String, val address: String?, val lat: Double, val lng: Double)

/**
 * Android's built-in geocoder: free, no API key. It needs a network connection and may be
 * missing on devices without Google services, so callers always offer manual entry too.
 */
class Geocoding(private val context: Context) {

    val isAvailable: Boolean get() = Geocoder.isPresent()

    suspend fun search(query: String, max: Int = 5): Result<List<GeoResult>> = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext Result.failure(IOException("Geocoder no disponible"))
        val geocoder = Geocoder(context, Locale.getDefault())
        runCatching {
            val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, max, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                        override fun onError(errorMessage: String?) =
                            cont.resumeWithException(IOException(errorMessage ?: "Error del geocoder"))
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, max).orEmpty()
            }
            addresses.map { it.toResult(query) }
        }
    }

    private fun Address.toResult(query: String): GeoResult {
        // featureName is sometimes just a house number; fall back to something readable.
        val name = featureName?.takeUnless { n -> n.all { it.isDigit() } }
            ?: locality
            ?: query
        return GeoResult(name = name, address = getAddressLine(0), lat = latitude, lng = longitude)
    }
}
