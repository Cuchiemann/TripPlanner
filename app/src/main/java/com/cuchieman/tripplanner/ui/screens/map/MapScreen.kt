@file:OptIn(ExperimentalMaterial3Api::class)

package com.cuchieman.tripplanner.ui.screens.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuchieman.tripplanner.data.TripPlan
import com.cuchieman.tripplanner.data.local.PlaceEntity
import com.cuchieman.tripplanner.data.local.hasCoordinates
import com.cuchieman.tripplanner.ui.appViewModel
import com.cuchieman.tripplanner.ui.components.DayChipRow
import com.cuchieman.tripplanner.ui.components.DayPin
import com.cuchieman.tripplanner.ui.components.TpButton
import com.cuchieman.tripplanner.ui.components.TpButtonVariant
import com.cuchieman.tripplanner.ui.components.TpTopBar
import com.cuchieman.tripplanner.ui.formatDayChip
import com.cuchieman.tripplanner.ui.formatDayTitle
import com.cuchieman.tripplanner.ui.formatTimeRange
import com.cuchieman.tripplanner.ui.openDirections
import com.cuchieman.tripplanner.ui.screens.trip.TripUiState
import com.cuchieman.tripplanner.ui.screens.trip.TripViewModel
import com.cuchieman.tripplanner.ui.theme.LocalTpPalette
import com.cuchieman.tripplanner.ui.theme.TpColors
import com.cuchieman.tripplanner.ui.theme.TpPalette
import com.cuchieman.tripplanner.ui.theme.TpRadius
import org.osmdroid.util.GeoPoint
import java.time.LocalDate

private val SheetPeek = 176.dp

/**
 * S08: the map with the places of the chosen day in a sheet below it, so map and list are on
 * one screen (parity.md: the most visible difference with the original). Tapping a pin
 * selects the row; tapping a row centres the map on its pin.
 */
@Composable
fun MapScreen(
    tripId: String,
    initialDay: LocalDate?,
    onBack: () -> Unit,
) {
    val vm = appViewModel { TripViewModel(tripId, it.repository, it.geocoding, it.appScope, it.covers, it.deletedTrip) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 0 = all days, i + 1 = day i.
    var filter by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val sheet = rememberBottomSheetScaffoldState()

    val plan = (state as? TripUiState.Ready)?.plan
    if (plan == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val chip = filter ?: initialDay?.let { d -> plan.days.indexOf(d).takeIf { it >= 0 }?.plus(1) } ?: 0
    val palette = LocalTpPalette.current
    val pins = pinsFor(plan, chip, palette)
    val selected = selectedId?.let(plan::place)?.takeIf { p -> pins.any { it.id == p.id } }

    BottomSheetScaffold(
        scaffoldState = sheet,
        topBar = { TpTopBar("Mapa", onBack = onBack) },
        sheetPeekHeight = SheetPeek,
        sheetContainerColor = TpColors.SurfaceRaised,
        containerColor = TpColors.Bg,
        sheetContent = {
            PlacesSheet(
                plan = plan,
                chip = chip,
                palette = palette,
                selectedId = selected?.id,
                onSelect = { selectedId = it },
                onDirections = { openDirections(context, it) }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            DayChipRow(
                labels = listOf("Todos") + plan.days.map(::formatDayChip),
                descriptions = listOf("Todos los días") + plan.days.mapIndexed { i, d -> "Día ${i + 1}, ${formatDayTitle(d)}" },
                selectedIndex = chip,
                onSelect = {
                    filter = it
                    selectedId = null
                },
                modifier = Modifier.padding(vertical = 8.dp)
            )
            // The osmdroid view draws past its bounds unless clipped; it covered the day chips.
            // Bottom padding keeps the pins above the sheet's resting height.
            Box(
                Modifier
                    .weight(1f)
                    .padding(bottom = SheetPeek)
                    .clipToBounds()
            ) {
                OsmMap(
                    pins = pins,
                    fallbackCenter = plan.trip.destinationLat?.let { lat ->
                        plan.trip.destinationLng?.let { lng -> GeoPoint(lat, lng) }
                    },
                    selectedId = selected?.id,
                    onPinClick = { selectedId = it },
                    modifier = Modifier.fillMaxSize()
                )
                if (pins.isEmpty()) {
                    Overlay(
                        if (chip == 0) "Ningún lugar tiene ubicación todavía. Añádelos buscándolos por nombre."
                        else "Este día no tiene lugares con ubicación."
                    )
                }
            }
        }
    }
}

/** The list half of the screen: the chosen day's places (or every day's), in order. */
@Composable
private fun PlacesSheet(
    plan: TripPlan,
    chip: Int,
    palette: TpPalette,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDirections: (PlaceEntity) -> Unit,
) {
    val groups: List<Pair<String, List<Triple<PlaceEntity, Int?, Color>>>> = buildList {
        plan.days.forEachIndexed { index, day ->
            if (chip == 0 || chip == index + 1) {
                val places = plan.placesOn(day)
                if (places.isNotEmpty() || chip != 0) {
                    add("Día ${index + 1} · ${formatDayTitle(day)}" to places.mapIndexed { i, p -> Triple(p, i + 1, palette.day(index)) })
                }
            }
        }
        if (chip == 0) {
            plan.lists.forEach { list ->
                val places = plan.placesIn(list.id)
                if (places.isNotEmpty()) add(list.title to places.map { Triple(it, null, palette.accent) })
            }
        }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        groups.forEach { (title, places) ->
            item(key = "h-$title") {
                Text(
                    title,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = TpColors.Text
                )
            }
            if (places.isEmpty()) {
                item(key = "e-$title") {
                    Text(
                        "Día libre por ahora.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TpColors.TextMuted
                    )
                }
            }
            items(places, key = { it.first.id }) { (place, number, color) ->
                SheetPlaceRow(
                    place = place,
                    number = number,
                    color = color,
                    selected = place.id == selectedId,
                    onClick = { if (place.hasCoordinates) onSelect(place.id) },
                    onDirections = { onDirections(place) }
                )
            }
        }
    }
}

@Composable
private fun SheetPlaceRow(
    place: PlaceEntity,
    number: Int?,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    onDirections: () -> Unit,
) {
    val time = formatTimeRange(place.startMinute, place.endMinute)
    val description = buildString {
        if (number != null) append("$number. ")
        append(place.name)
        if (time != null) append(", $time")
        if (!place.hasCoordinates) append(", sin ubicación en el mapa")
    }
    Surface(
        onClick = onClick,
        color = if (selected) TpColors.TimeChip else TpColors.SurfaceRaised,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = description }
            ) {
                DayPin(number = number, color = color)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.bodyLarge, color = TpColors.Text)
                    val sub = listOfNotNull(time, if (!place.hasCoordinates) "Sin ubicación" else place.address)
                    if (sub.isNotEmpty()) {
                        Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = TpColors.TextMuted, maxLines = 1)
                    }
                }
            }
            if (selected) {
                TpButton("Cómo llegar", onClick = onDirections, variant = TpButtonVariant.Text, icon = Icons.Filled.Directions)
            }
        }
    }
}

private fun pinsFor(plan: TripPlan, chip: Int, palette: TpPalette): List<MapPin> {
    val dayPins = plan.days.flatMapIndexed { index, day ->
        if (chip != 0 && chip != index + 1) return@flatMapIndexed emptyList()
        plan.placesOn(day).mapIndexedNotNull { i, p -> p.toPin(i + 1, palette.day(index)) }
    }
    // List places have no day: shown only on "Todos", in the accent colour without a number.
    val listPins = if (chip != 0) emptyList() else plan.lists.flatMap { list ->
        plan.placesIn(list.id).mapNotNull { it.toPin(null, palette.accent) }
    }
    return dayPins + listPins
}

private fun PlaceEntity.toPin(number: Int?, color: Color): MapPin? =
    if (lat == null || lng == null) null else MapPin(id, name, number, color.toArgb(), lat, lng)

@Composable
private fun Overlay(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(TpRadius.lg), color = TpColors.SurfaceRaised, shadowElevation = 2.dp) {
            Text(
                text,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = TpColors.Text
            )
        }
    }
}
