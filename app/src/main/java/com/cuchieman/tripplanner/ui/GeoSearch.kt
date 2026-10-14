package com.cuchieman.tripplanner.ui

import com.cuchieman.tripplanner.data.GeoResult
import com.cuchieman.tripplanner.data.Geocoding
import com.cuchieman.tripplanner.data.MapsLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GeoSearchUi(
    val query: String = "",
    val results: List<GeoResult> = emptyList(),
    val searching: Boolean = false,
    /** True when the last search failed (offline or no geocoder). Manual entry stays available. */
    val failed: Boolean = false,
    /** True when a search ran and found nothing. */
    val empty: Boolean = false,
    /** The query is a Google Maps link or coordinates, not a name: no "add by hand" row. */
    val isLocation: Boolean = false,
    /** A Maps link was pasted but could not be read (offline, or not a place). */
    val linkFailed: Boolean = false,
)

/**
 * Debounced search shared by S02 (destination) and S06 (add place). Takes a name (Android's
 * geocoder), a Google Maps link (followed to its coordinates) or plain coordinates.
 */
class GeoSearch(private val scope: CoroutineScope, private val geocoding: Geocoding) {
    private val _ui = MutableStateFlow(GeoSearchUi())
    val ui: StateFlow<GeoSearchUi> = _ui.asStateFlow()
    private var job: Job? = null

    fun onQueryChange(query: String) {
        job?.cancel()
        val text = query.trim()

        // Coordinates or a Maps link, alone or inside what the Maps app shares.
        if (MapsLink.looksLikeLocation(text)) {
            _ui.value = GeoSearchUi(query = query, isLocation = true, searching = true)
            job = scope.launch {
                val result = runCatching { MapsLink.fromText(text, ::firstMatch) }.getOrNull()
                _ui.update {
                    it.copy(searching = false, results = listOfNotNull(result), linkFailed = result == null)
                }
            }
            return
        }

        _ui.update { it.copy(query = query, failed = false, empty = false, isLocation = false, linkFailed = false) }
        if (text.length < 3) {
            _ui.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        job = scope.launch {
            delay(400)
            _ui.update { it.copy(searching = true) }
            val result = geocoding.search(text)
            _ui.update {
                it.copy(
                    searching = false,
                    results = result.getOrDefault(emptyList()),
                    failed = result.isFailure,
                    empty = result.getOrNull()?.isEmpty() == true
                )
            }
        }
    }

    /** For a Maps link that only names its place: the geocoder's best match for that name. */
    suspend fun firstMatch(name: String): GeoResult? = geocoding.search(name, max = 1).getOrNull()?.firstOrNull()

    fun clear() {
        job?.cancel()
        _ui.value = GeoSearchUi()
    }
}
