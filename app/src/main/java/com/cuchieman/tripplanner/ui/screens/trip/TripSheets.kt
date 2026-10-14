@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.cuchieman.tripplanner.ui.screens.trip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cuchieman.tripplanner.data.GeoResult
import com.cuchieman.tripplanner.data.PlaceTarget
import com.cuchieman.tripplanner.data.TripPlan
import com.cuchieman.tripplanner.data.local.PlaceEntity
import com.cuchieman.tripplanner.data.local.hasCoordinates
import com.cuchieman.tripplanner.data.target
import com.cuchieman.tripplanner.ui.GeoSearchUi
import com.cuchieman.tripplanner.ui.components.CategoryChips
import com.cuchieman.tripplanner.ui.components.MoneyField
import com.cuchieman.tripplanner.ui.components.TimeChip
import com.cuchieman.tripplanner.ui.components.TpButton
import com.cuchieman.tripplanner.ui.components.TpButtonVariant
import com.cuchieman.tripplanner.ui.components.TpSearchField
import com.cuchieman.tripplanner.ui.currencySymbol
import com.cuchieman.tripplanner.ui.formatDayTitle
import com.cuchieman.tripplanner.ui.formatTimeRange
import com.cuchieman.tripplanner.ui.minorToInput
import com.cuchieman.tripplanner.ui.openDirections
import com.cuchieman.tripplanner.ui.parseMoney
import com.cuchieman.tripplanner.ui.screens.create.SearchResults
import com.cuchieman.tripplanner.ui.theme.TpColors
import com.cuchieman.tripplanner.ui.theme.TpRadius
import java.util.Locale

/** Human name of a day or list, used in sheet titles and "Movido a …" messages. */
fun TripPlan.labelOf(target: PlaceTarget): String = when (target) {
    is PlaceTarget.Day -> {
        val index = (target.epochDay - trip.startDate).toInt()
        "Día ${index + 1} · ${formatDayTitle(days[index])}"
    }
    is PlaceTarget.InList -> lists.firstOrNull { it.id == target.listId }?.title ?: "la lista"
}

/** S06: search the geocoder or type a name, and add it to [target]. */
@Composable
fun AddPlaceSheet(
    targetLabel: String,
    search: GeoSearchUi,
    onQueryChange: (String) -> Unit,
    onChoose: (GeoResult) -> Unit,
    onManual: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            Text("Añadir a $targetLabel", style = MaterialTheme.typography.titleLarge, color = TpColors.Text)
            Spacer(Modifier.height(12.dp))
            TpSearchField(
                value = search.query,
                onValueChange = onQueryChange,
                placeholder = "Nombre, dirección o enlace de Google Maps",
                searching = search.searching
            )
            Spacer(Modifier.height(8.dp))
            SearchResults(
                search = search,
                onChoose = onChoose,
                onManual = onManual,
                manualLabel = { "Añadir «$it» sin ubicación" }
            )
        }
    }
}

/** S07 place detail, with S13 (times) and S14 (move to) inside it. Edits save when the sheet closes. */
@Composable
fun PlaceDetailSheet(
    place: PlaceEntity,
    plan: TripPlan,
    onSave: (name: String, note: String, address: String?) -> Unit,
    onSetTime: (start: Int?, end: Int?) -> Unit,
    onSetCategory: (String?) -> Unit,
    onSetLocation: (String) -> Unit,
    onClearLocation: () -> Unit,
    onMove: (PlaceTarget, String) -> Unit,
    onNudge: (Int) -> Unit,
    onDelete: () -> Unit,
    onAddExpense: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember(place.id) { mutableStateOf(place.name) }
    var address by remember(place.id) { mutableStateOf(place.address ?: "") }
    var note by remember(place.id) { mutableStateOf(place.note) }
    var moving by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<String?>(null) } // "start" | "end"
    var editingLocation by remember { mutableStateOf(false) }

    val siblingCount = when (val t = place.target) {
        is PlaceTarget.Day -> plan.placesOn(plan.days[(t.epochDay - plan.trip.startDate).toInt()]).size
        is PlaceTarget.InList -> plan.placesIn(t.listId).size
    }
    val dirty = name != place.name || address != (place.address ?: "") || note != place.note
    val close = {
        if (dirty && name.isNotBlank()) onSave(name, note, address)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (moving) {
                MoveToList(
                    plan = plan,
                    current = place.target,
                    onPick = { target ->
                        onMove(target, plan.labelOf(target))
                        moving = false
                    },
                    onCancel = { moving = false }
                )
            } else {
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TpColors.Accent,
                    unfocusedBorderColor = TpColors.BorderInput
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre") },
                    isError = name.isBlank(),
                    singleLine = true,
                    shape = RoundedCornerShape(TpRadius.md),
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Dirección") },
                    singleLine = true,
                    shape = RoundedCornerShape(TpRadius.md),
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notas y enlaces") },
                    minLines = 3,
                    shape = RoundedCornerShape(TpRadius.md),
                    colors = fieldColors
                )
                // fixes.md #7: say what the place is, so an Airbnb is a stay, not just a pin.
                Text("Tipo", style = MaterialTheme.typography.titleMedium, color = TpColors.Text)
                CategoryChips(
                    selectedKey = place.category ?: "",
                    onSelect = { key -> onSetCategory(if (key == place.category) null else key) }
                )
                // Location: for places the search could not find, paste a Maps link or coordinates.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ubicación", style = MaterialTheme.typography.titleMedium, color = TpColors.Text)
                        Text(
                            if (place.hasCoordinates) String.format(Locale.ROOT, "%.5f, %.5f", place.lat, place.lng)
                            else "Sin ubicación: no aparece en el mapa",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TpColors.TextMuted
                        )
                    }
                    TpButton(
                        if (place.hasCoordinates) "Cambiar" else "Añadir",
                        onClick = { editingLocation = true },
                        variant = TpButtonVariant.Text
                    )
                    if (place.hasCoordinates) {
                        TpButton("Quitar", onClick = onClearLocation, variant = TpButtonVariant.Text)
                    }
                }

                if (place.dayDate != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hora", style = MaterialTheme.typography.titleMedium, color = TpColors.Text)
                        Spacer(Modifier.width(12.dp))
                        val time = formatTimeRange(place.startMinute, place.endMinute)
                        if (time != null) TimeChip(time) else Text("Sin hora", color = TpColors.TextMuted)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TpButton(
                            if (place.startMinute == null) "Añadir inicio" else "Cambiar inicio",
                            onClick = { editingTime = "start" },
                            variant = TpButtonVariant.Text
                        )
                        if (place.startMinute != null) {
                            TpButton(
                                if (place.endMinute == null) "Añadir fin" else "Cambiar fin",
                                onClick = { editingTime = "end" },
                                variant = TpButtonVariant.Text
                            )
                            TpButton("Quitar hora", onClick = { onSetTime(null, null) }, variant = TpButtonVariant.Text)
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TpButton(
                        "Subir",
                        onClick = { onNudge(-1) },
                        icon = Icons.Filled.ArrowUpward,
                        variant = TpButtonVariant.Secondary,
                        enabled = place.position > 0
                    )
                    TpButton(
                        "Bajar",
                        onClick = { onNudge(1) },
                        icon = Icons.Filled.ArrowDownward,
                        variant = TpButtonVariant.Secondary,
                        enabled = place.position < siblingCount - 1
                    )
                    TpButton(
                        "Mover a…",
                        onClick = { moving = true },
                        icon = Icons.Filled.SwapVert,
                        variant = TpButtonVariant.Secondary
                    )
                    TpButton(
                        "Cómo llegar",
                        onClick = { openDirections(context, place) },
                        icon = Icons.Filled.Directions,
                        variant = TpButtonVariant.Secondary
                    )
                    TpButton(
                        "Añadir gasto",
                        onClick = onAddExpense,
                        icon = Icons.Filled.Euro,
                        variant = TpButtonVariant.Secondary
                    )
                }
                TpButton(
                    "Borrar lugar",
                    onClick = onDelete,
                    icon = Icons.Filled.Delete,
                    variant = TpButtonVariant.Danger,
                    modifier = Modifier.fillMaxWidth()
                )
                TpButton("Listo", onClick = close, modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank())
            }
        }
    }

    if (editingLocation) {
        TextInputDialog(
            title = "Ubicación",
            label = "Enlace de Google Maps o coordenadas",
            initial = "",
            confirmLabel = "Guardar",
            onConfirm = { onSetLocation(it); editingLocation = false },
            onDismiss = { editingLocation = false }
        )
    }

    when (editingTime) {
        "start" -> TimeDialog(
            title = "Hora de inicio",
            initialMinute = place.startMinute,
            onConfirm = { minute ->
                // Keep the end only if it still comes after the new start.
                val end = place.endMinute?.takeIf { it >= minute }
                onSetTime(minute, end)
                editingTime = null
            },
            onDismiss = { editingTime = null }
        )
        "end" -> TimeDialog(
            title = "Hora de fin",
            initialMinute = place.endMinute ?: place.startMinute?.plus(60)?.coerceAtMost(23 * 60 + 59),
            onConfirm = { minute ->
                onSetTime(place.startMinute, minute)
                editingTime = null
            },
            onDismiss = { editingTime = null }
        )
    }
}

@Composable
private fun MoveToList(
    plan: TripPlan,
    current: PlaceTarget,
    onPick: (PlaceTarget) -> Unit,
    onCancel: () -> Unit,
) {
    Text("Mover a…", style = MaterialTheme.typography.titleLarge, color = TpColors.Text)
    val targets = plan.days.map { PlaceTarget.Day(it.toEpochDay()) } +
        plan.lists.map { PlaceTarget.InList(it.id) }
    targets.filter { it != current }.forEach { target ->
        Text(
            plan.labelOf(target),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { onPick(target) }
                .padding(vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = TpColors.Text
        )
    }
    TpButton("Cancelar", onClick = onCancel, variant = TpButtonVariant.Text)
}

@Composable
fun TimeDialog(
    title: String,
    initialMinute: Int?,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = initialMinute ?: (10 * 60)
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimeInput(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** S11: set, change or clear the trip budget. */
@Composable
fun BudgetDialog(
    currentMinor: Long?,
    currency: String,
    onSave: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentMinor?.let { minorToInput(it, currency) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Presupuesto del viaje") },
        text = {
            MoneyField(
                value = text,
                onValueChange = { text = it; error = null },
                label = "Importe total",
                currencySymbol = currencySymbol(currency),
                error = error
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val minor = parseMoney(text, currency)
                if (minor == null) error = "Introduce un importe mayor que 0" else onSave(minor)
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (currentMinor != null) {
                    TextButton(onClick = { onSave(null) }) { Text("Quitar") }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}
