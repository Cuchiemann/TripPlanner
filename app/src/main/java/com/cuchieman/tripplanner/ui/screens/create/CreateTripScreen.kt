package com.cuchieman.tripplanner.ui.screens.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cuchieman.tripplanner.data.GeoResult
import com.cuchieman.tripplanner.data.Geocoding
import com.cuchieman.tripplanner.data.TripRepository
import com.cuchieman.tripplanner.ui.GeoSearch
import com.cuchieman.tripplanner.ui.GeoSearchUi
import com.cuchieman.tripplanner.ui.appViewModel
import com.cuchieman.tripplanner.ui.components.DateRangeField
import com.cuchieman.tripplanner.ui.components.TpButton
import com.cuchieman.tripplanner.ui.components.TpButtonVariant
import com.cuchieman.tripplanner.ui.components.TpDateRangePickerDialog
import com.cuchieman.tripplanner.ui.components.TpSearchField
import com.cuchieman.tripplanner.ui.components.TpTopBar
import com.cuchieman.tripplanner.ui.dateToUtcMillis
import com.cuchieman.tripplanner.ui.formatRange
import com.cuchieman.tripplanner.ui.theme.TpColors
import com.cuchieman.tripplanner.ui.theme.TpRadius
import com.cuchieman.tripplanner.ui.utcMillisToDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CreateTripForm(
    /** Chosen from the geocoder, with coordinates. */
    val destination: GeoResult? = null,
    /** Typed by hand when search is not available; no coordinates. */
    val manualDestination: String? = null,
    val title: String = "",
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val error: String? = null,
    val saving: Boolean = false,
) {
    val destinationName: String? get() = destination?.name ?: manualDestination
}

class CreateTripViewModel(
    private val repository: TripRepository,
    geocoding: Geocoding,
) : ViewModel() {
    val search = GeoSearch(viewModelScope, geocoding)
    private val _form = MutableStateFlow(CreateTripForm())
    val form: StateFlow<CreateTripForm> = _form.asStateFlow()

    fun onQueryChange(query: String) {
        _form.update { it.copy(destination = null, manualDestination = null, error = null) }
        search.onQueryChange(query)
    }

    fun choose(result: GeoResult) {
        _form.update { it.copy(destination = result, manualDestination = null, error = null) }
        search.clear()
    }

    fun chooseManual(name: String) {
        _form.update { it.copy(destination = null, manualDestination = name.trim(), error = null) }
        search.clear()
    }

    fun changeDestination() = _form.update { it.copy(destination = null, manualDestination = null) }

    fun onTitleChange(title: String) = _form.update { it.copy(title = title) }

    fun onDates(start: LocalDate, end: LocalDate) = _form.update { it.copy(start = start, end = end, error = null) }

    fun create(onCreated: (String) -> Unit) {
        // A double tap reaches here twice before the button redraws as loading.
        if (_form.value.saving) return
        val f = _form.value
        val name = f.destinationName
        val error = when {
            name.isNullOrBlank() -> "Elige un destino"
            f.start == null || f.end == null -> "Elige las fechas de ida y vuelta"
            else -> null
        }
        if (error != null) {
            _form.update { it.copy(error = error) }
            return
        }
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                repository.createTrip(f.title, name!!, f.destination?.lat, f.destination?.lng, f.start!!, f.end!!)
            }.onSuccess(onCreated)
                .onFailure { e -> _form.update { it.copy(saving = false, error = e.message) } }
        }
    }
}

/** S02 + S03: destination, dates, optional name. */
@Composable
fun CreateTripScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val vm = appViewModel { CreateTripViewModel(it.repository, it.geocoding) }
    val form by vm.form.collectAsStateWithLifecycle()
    val search by vm.search.ui.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(topBar = { TpTopBar("Nuevo viaje", onBack = onBack) }, containerColor = TpColors.Bg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("¿A dónde vas?", style = MaterialTheme.typography.titleLarge, color = TpColors.Text)

            val chosen = form.destinationName
            if (chosen != null) {
                ChosenDestination(chosen, form.destination?.address, onChange = vm::changeDestination)
            } else {
                TpSearchField(
                    value = search.query,
                    onValueChange = vm::onQueryChange,
                    placeholder = "Ciudad, región o país",
                    searching = search.searching
                )
                SearchResults(search, onChoose = vm::choose, onManual = vm::chooseManual)
            }

            DateRangeField(
                text = if (form.start != null && form.end != null) formatRange(form.start!!, form.end!!) else null,
                onClick = { showPicker = true }
            )

            OutlinedTextField(
                value = form.title,
                onValueChange = vm::onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nombre del viaje (opcional)") },
                placeholder = { Text(chosen?.let { "Viaje a $it" } ?: "Viaje a…") },
                singleLine = true,
                shape = RoundedCornerShape(TpRadius.md),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TpColors.Accent,
                    unfocusedBorderColor = TpColors.BorderInput
                )
            )

            form.error?.let {
                Text(
                    it,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = TpColors.Danger,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            TpButton(
                "Crear viaje",
                onClick = { vm.create(onCreated) },
                modifier = Modifier.fillMaxWidth(),
                loading = form.saving
            )
        }
    }

    if (showPicker) {
        TpDateRangePickerDialog(
            onDismiss = { showPicker = false },
            onConfirm = { s, e ->
                vm.onDates(utcMillisToDate(s), utcMillisToDate(e))
                showPicker = false
            },
            initialStartMillis = form.start?.let(::dateToUtcMillis),
            initialEndMillis = form.end?.let(::dateToUtcMillis)
        )
    }
}

@Composable
private fun ChosenDestination(name: String, address: String?, onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Place, contentDescription = null, tint = TpColors.Accent)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = TpColors.Text)
            Text(
                address ?: "Sin ubicación en el mapa",
                style = MaterialTheme.typography.bodyMedium,
                color = TpColors.TextMuted
            )
        }
        TpButton("Cambiar", onClick = onChange, variant = TpButtonVariant.Text)
    }
}

/** Geocoder results plus the manual option, which is always there. Shared with S06. */
@Composable
fun SearchResults(
    search: GeoSearchUi,
    onChoose: (GeoResult) -> Unit,
    onManual: (String) -> Unit,
    manualLabel: (String) -> String = { "Usar «$it» sin ubicación" },
) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        search.results.forEach { result ->
            ResultRow(result.name, result.address, onClick = { onChoose(result) })
        }
        when {
            search.linkFailed -> Hint("No se ha podido leer ese enlace (¿sin conexión?). Prueba con otro o pega las coordenadas.")
            search.failed -> Hint("No se puede buscar ahora (¿sin conexión?). Puedes añadirlo a mano.")
            search.empty -> Hint("Sin resultados. Prueba con otro nombre, pega un enlace de Google Maps o añádelo a mano.")
        }
        if (search.query.isNotBlank() && !search.isLocation) {
            ResultRow(manualLabel(search.query.trim()), null, onClick = { onManual(search.query) })
        }
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Place, contentDescription = null, tint = TpColors.TextMuted)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TpColors.Text)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TpColors.TextMuted)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        modifier = Modifier.padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = TpColors.TextMuted
    )
}
