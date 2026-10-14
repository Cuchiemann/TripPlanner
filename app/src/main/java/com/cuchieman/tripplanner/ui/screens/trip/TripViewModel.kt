package com.cuchieman.tripplanner.ui.screens.trip

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuchieman.tripplanner.data.CoverStore
import com.cuchieman.tripplanner.data.DeletedTrip
import com.cuchieman.tripplanner.data.GeoResult
import com.cuchieman.tripplanner.data.MapsLink
import com.cuchieman.tripplanner.data.Geocoding
import com.cuchieman.tripplanner.data.PlaceTarget
import com.cuchieman.tripplanner.data.TripPlan
import com.cuchieman.tripplanner.data.TripRepository
import com.cuchieman.tripplanner.ui.GeoSearch
import com.cuchieman.tripplanner.ui.UiMessage
import com.cuchieman.tripplanner.ui.toKml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface TripUiState {
    data object Loading : TripUiState
    /** The trip was deleted (or never existed): the screen should leave. */
    data object Missing : TripUiState
    data class Ready(val plan: TripPlan) : TripUiState
}

/** Shared by S04/S05/S09 (tabs), S08 (map) and S12 (settings); each screen gets its own instance. */
class TripViewModel(
    val tripId: String,
    private val repository: TripRepository,
    geocoding: Geocoding,
    private val appScope: CoroutineScope,
    private val covers: CoverStore,
    private val deletedTrip: MutableStateFlow<DeletedTrip?>,
) : ViewModel() {

    val state: StateFlow<TripUiState> = repository.observePlan(tripId)
        .map { plan -> if (plan == null) TripUiState.Missing else TripUiState.Ready(plan) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripUiState.Loading)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    val search = GeoSearch(viewModelScope, geocoding)

    // Notes: the field shows the draft; the database gets it 500 ms after the last keystroke,
    // and once more when the screen stops, so closing the app never loses the last words.

    private val _notesDraft = MutableStateFlow<String?>(null)
    val notesDraft: StateFlow<String?> = _notesDraft.asStateFlow()
    private var notesJob: Job? = null

    fun onNotesChange(text: String) {
        _notesDraft.value = text
        notesJob?.cancel()
        notesJob = viewModelScope.launch {
            delay(500)
            repository.updateNotes(tripId, text)
        }
    }

    fun flushNotes() {
        val text = _notesDraft.value ?: return
        notesJob?.cancel()
        appScope.launch { repository.updateNotes(tripId, text) }
    }

    override fun onCleared() {
        flushNotes()
    }

    // Places

    fun addFromSearch(target: PlaceTarget, result: GeoResult) = writeOnce("add-place", "No se ha podido añadir el lugar") {
        repository.addPlace(tripId, target, result.name, result.address, result.lat, result.lng)
        search.clear()
        _messages.send(UiMessage("Añadido: ${result.name}"))
    }

    fun addManual(target: PlaceTarget, name: String) = writeOnce("add-place", "No se ha podido añadir el lugar") {
        repository.addPlace(tripId, target, name)
        search.clear()
        _messages.send(UiMessage("Añadido: ${name.trim()} (sin ubicación en el mapa)"))
    }

    fun saveDetails(placeId: String, name: String, note: String, address: String?) =
        write("No se ha podido guardar") { repository.updatePlaceDetails(placeId, name, note, address) }

    fun setTime(placeId: String, start: Int?, end: Int?) =
        write("No se ha podido guardar la hora") { repository.setPlaceTime(placeId, start, end) }

    /** A pasted Google Maps link (short or long) or "lat, lng" becomes the place's location. */
    fun setLocationFromText(placeId: String, text: String) = write("No se ha podido poner la ubicación") {
        val point = MapsLink.fromText(text.trim(), search::firstMatch)?.let { it.lat to it.lng }
        if (point == null) {
            _messages.send(UiMessage("No se ha reconocido. Pega un enlace de Google Maps o unas coordenadas como 38.15, -0.69."))
            return@write
        }
        repository.setPlaceLocation(placeId, point.first, point.second)
        _messages.send(UiMessage("Ubicación guardada"))
    }

    fun clearLocation(placeId: String) =
        write("No se ha podido quitar la ubicación") { repository.setPlaceLocation(placeId, null, null) }

    fun setCategory(placeId: String, category: String?) =
        write("No se ha podido guardar el tipo") { repository.setPlaceCategory(placeId, category) }

    fun move(placeId: String, target: PlaceTarget, label: String) = write("No se ha podido mover") {
        repository.movePlace(placeId, target)
        _messages.send(UiMessage("Movido a $label"))
    }

    /** Drag and drop: [index] is the position among the places already in [target]. */
    fun reorder(placeId: String, target: PlaceTarget, index: Int, onFailed: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.movePlace(placeId, target, index) }.onFailure { e ->
                onFailed()
                _messages.send(UiMessage(e.message?.takeIf { e is IllegalArgumentException } ?: "No se ha podido mover"))
            }
        }
    }

    fun nudge(placeId: String, delta: Int) = write("No se ha podido mover") { repository.nudgePlace(placeId, delta) }

    fun delete(placeId: String) = write("No se ha podido borrar") {
        val removed = repository.deletePlace(placeId) ?: return@write
        _messages.send(
            UiMessage("Borrado: ${removed.name}", actionLabel = "Deshacer") {
                viewModelScope.launch { repository.restorePlace(removed) }
            }
        )
    }

    // Trip

    fun changeDates(start: LocalDate, end: LocalDate) = write("No se han podido cambiar las fechas") {
        val moved = repository.changeDates(tripId, start, end)
        if (moved > 0) {
            val what = if (moved == 1) "1 lugar movido" else "$moved lugares movidos"
            _messages.send(UiMessage("$what a ${TripRepository.DEFAULT_LIST_TITLE}"))
        }
    }

    fun rename(title: String) = write("No se ha podido renombrar") {
        repository.renameTrip(tripId, title)
        _messages.send(UiMessage("Nombre guardado"))
    }

    /** Deletes with undo: the home screen offers "Deshacer" (should-have: undo for trips). */
    fun deleteTrip(onDone: () -> Unit) = write("No se ha podido borrar el viaje") {
        val deleted = repository.deleteTripForUndo(tripId) ?: return@write
        // A trip deleted earlier and never restored is gone for good now: drop its photo too.
        deletedTrip.value?.coverPath?.let { covers.delete(it) }
        deletedTrip.value = deleted
        onDone()
    }

    // Cover photo

    fun setCover(uri: Uri) = write("No se ha podido poner la foto") {
        val old = (state.value as? TripUiState.Ready)?.plan?.trip?.coverUri
        val path = covers.save(tripId, uri)
        repository.setCover(tripId, path)
        covers.delete(old)
    }

    fun clearCover() = write("No se ha podido quitar la foto") {
        val old = (state.value as? TripUiState.Ready)?.plan?.trip?.coverUri
        repository.setCover(tripId, null)
        covers.delete(old)
    }

    // Lists

    fun createList(title: String) = writeOnce("create-list", "No se ha podido crear la lista") { repository.createList(tripId, title) }
    fun renameList(listId: String, title: String) = write("No se ha podido renombrar la lista") { repository.renameList(listId, title) }
    fun deleteList(listId: String) = write("No se ha podido borrar la lista") { repository.deleteList(listId) }

    // Several places at once

    fun movePlaces(ids: List<String>, target: PlaceTarget, label: String) = write("No se han podido mover") {
        repository.movePlaces(ids, target)
        _messages.send(UiMessage(if (ids.size == 1) "1 lugar movido a $label" else "${ids.size} lugares movidos a $label"))
    }

    fun deletePlaces(ids: List<String>) = write("No se han podido borrar") {
        val removed = repository.deletePlaces(ids)
        val what = if (removed.size == 1) "1 lugar borrado" else "${removed.size} lugares borrados"
        _messages.send(UiMessage(what, actionLabel = "Deshacer") {
            viewModelScope.launch { repository.restorePlaces(removed) }
        })
    }

    // Checklist

    fun addChecklistItem(text: String) = writeOnce("add-item", "No se ha podido añadir") { repository.addChecklistItem(tripId, text) }
    fun setChecklistDone(id: String, done: Boolean) = write("No se ha podido guardar") { repository.setChecklistDone(id, done) }

    fun deleteChecklistItem(id: String) = write("No se ha podido borrar") {
        val item = repository.deleteChecklistItem(id) ?: return@write
        _messages.send(UiMessage("Borrado: ${item.text}", actionLabel = "Deshacer") {
            viewModelScope.launch { repository.restoreChecklistItem(item) }
        })
    }

    /** KML for Google My Maps / Google Earth, one folder per day (could-have: export to Google Maps). */
    fun exportKmlTo(uri: Uri, resolver: ContentResolver) = write("No se ha podido exportar") {
        val plan = (state.value as? TripUiState.Ready)?.plan ?: return@write
        withContext(Dispatchers.IO) {
            val out = resolver.openOutputStream(uri, "wt") ?: error("No output stream for $uri")
            out.use { it.write(plan.toKml().toByteArray(Charsets.UTF_8)) }
        }
        _messages.send(UiMessage("Exportado. Ábrelo en Google My Maps: Importar"))
    }

    /** Writes the trip's backup JSON to a file the user picked (Storage Access Framework). */
    fun exportTo(uri: Uri, resolver: ContentResolver) = write("No se ha podido exportar el viaje") {
        val json = repository.exportTrip(tripId)
        withContext(Dispatchers.IO) {
            val out = resolver.openOutputStream(uri, "wt") ?: error("No output stream for $uri")
            out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }
        _messages.send(UiMessage("Viaje exportado"))
    }

    fun setBudget(minor: Long?) = write("No se ha podido guardar el presupuesto") { repository.setBudget(tripId, minor) }

    // Creating actions that a fast double tap reaches twice before the UI redraws (bugs B07).
    private val inFlight = mutableSetOf<String>()

    /** Like [write], but a second call with the same [key] is ignored until the first finishes. */
    private fun writeOnce(key: String, failure: String, block: suspend () -> Unit) {
        if (!inFlight.add(key)) return
        write(failure) {
            try {
                block()
            } finally {
                inFlight.remove(key)
            }
        }
    }

    /** Runs a write; a failure becomes a snackbar instead of a crash. */
    private fun write(failure: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { e ->
                _messages.send(UiMessage(e.message?.takeIf { e is IllegalArgumentException } ?: failure))
            }
        }
    }
}
