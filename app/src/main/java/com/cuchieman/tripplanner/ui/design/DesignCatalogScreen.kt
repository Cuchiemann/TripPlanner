package com.cuchieman.tripplanner.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cuchieman.tripplanner.ui.components.ActionLinks
import com.cuchieman.tripplanner.ui.components.BudgetProgress
import com.cuchieman.tripplanner.ui.components.CategoryChips
import com.cuchieman.tripplanner.ui.components.ConfirmDialog
import com.cuchieman.tripplanner.ui.components.DateRangeField
import com.cuchieman.tripplanner.ui.components.DayChipRow
import com.cuchieman.tripplanner.ui.components.DayPin
import com.cuchieman.tripplanner.ui.components.EmptyState
import com.cuchieman.tripplanner.ui.components.ExpenseRow
import com.cuchieman.tripplanner.ui.components.MoneyField
import com.cuchieman.tripplanner.ui.components.NoteField
import com.cuchieman.tripplanner.ui.components.PlaceRow
import com.cuchieman.tripplanner.ui.components.SectionBand
import com.cuchieman.tripplanner.ui.components.SectionHeader
import com.cuchieman.tripplanner.ui.components.TimeChip
import com.cuchieman.tripplanner.ui.components.TpButton
import com.cuchieman.tripplanner.ui.components.TpButtonVariant
import com.cuchieman.tripplanner.ui.components.TpDateRangePickerDialog
import com.cuchieman.tripplanner.ui.components.TpExtendedFab
import com.cuchieman.tripplanner.ui.components.TpRoundFab
import com.cuchieman.tripplanner.ui.components.TpSearchField
import com.cuchieman.tripplanner.ui.components.TpTabRow
import com.cuchieman.tripplanner.ui.components.TravelSegment
import com.cuchieman.tripplanner.ui.components.TripCard
import com.cuchieman.tripplanner.ui.components.TripHeader
import com.cuchieman.tripplanner.ui.components.expenseCategory
import com.cuchieman.tripplanner.ui.theme.TpColors
import com.cuchieman.tripplanner.ui.theme.TripPlannerTheme
import kotlinx.coroutines.launch

/** Every primitive on one scrollable screen: the design-system check. Not part of the shipped app. */
@Composable
fun DesignCatalogScreen() {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(1) }
    var day by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var money by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("comida") }
    var showPicker by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    val euros: (Long) -> String = { "${it / 100} €" }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            TpExtendedFab("Nuevo viaje", Icons.Filled.Add, onClick = {})
        },
        containerColor = TpColors.Bg
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                TripHeader(title = "Lisboa con amigos", dates = "3 may – 7 may · 5 días", onDatesClick = { showPicker = true })
                Spacer(Modifier.height(8.dp))
                TpTabRow(listOf("Resumen", "Itinerario", "Gastos"), tab, { tab = it })
                Spacer(Modifier.height(8.dp))
                DayChipRow(
                    labels = listOf("sáb 3/5", "dom 4/5", "lun 5/5", "mar 6/5", "mié 7/5"),
                    selectedIndex = day,
                    onSelect = { day = it }
                )
            }

            item {
                SectionHeader("Sábado 3 de mayo", expanded, { expanded = !expanded }, onMore = {})
                ActionLinks(listOf("Añadir nota" to {}, "Seleccionar" to {}))
            }
            if (expanded) {
                item {
                    PlaceRow(
                        name = "Torre de Belém",
                        note = "Ir temprano, a las 10 ya hay cola.",
                        pinColor = TpColors.day(0),
                        number = 1,
                        time = "10:00 – 11:30"
                    )
                    TravelSegment("1,2 km", onDirections = {})
                    PlaceRow(name = "Pastéis de Belém", note = "", pinColor = TpColors.day(0), number = 2)
                    PlaceRow(
                        name = "Mosteiro dos Jerónimos",
                        note = "Arrastrando",
                        pinColor = TpColors.day(0),
                        number = 3,
                        dragging = true
                    )
                }
            }
            item { SectionBand() }

            item {
                CatalogLabel("Pins por día")
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(8) { DayPin(number = it + 1, color = TpColors.day(it), size = 28.dp) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.padding(horizontal = 16.dp)) { TimeChip("19:30") }
                SectionBand(Modifier.padding(top = 16.dp))
            }

            item {
                CatalogLabel("Botones")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TpButton("Crear viaje", onClick = {})
                    TpButton("Fijar presupuesto", onClick = {}, variant = TpButtonVariant.Secondary)
                    TpButton("Cómo llegar", onClick = {}, variant = TpButtonVariant.Text)
                    TpButton("Borrar viaje", onClick = { showConfirm = true }, variant = TpButtonVariant.Danger, icon = Icons.Filled.Delete)
                    TpButton("Guardando", onClick = {}, loading = true)
                    TpButton("Desactivado", onClick = {}, enabled = false)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TpRoundFab(Icons.Filled.Map, "Ver mapa", onClick = {})
                        TpRoundFab(Icons.Filled.Add, "Añadir lugar", onClick = {})
                    }
                    TpButton("Mostrar snackbar con deshacer", variant = TpButtonVariant.Secondary, onClick = {
                        scope.launch {
                            val result = snackbar.showSnackbar("Lugar borrado", actionLabel = "Deshacer")
                            if (result == SnackbarResult.ActionPerformed) snackbar.showSnackbar("Restaurado")
                        }
                    })
                }
                SectionBand(Modifier.padding(top = 16.dp))
            }

            item {
                CatalogLabel("Campos")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TpSearchField(query, { query = it }, placeholder = "Busca un lugar o dirección")
                    TpSearchField("Lisb", {}, placeholder = "", searching = true)
                    DateRangeField(text = "3 may – 7 may · 5 días", onClick = { showPicker = true })
                    DateRangeField(text = null, onClick = {}, error = "La vuelta no puede ser antes de la ida")
                    MoneyField(money, { money = it }, label = "Importe", currencySymbol = "€")
                    MoneyField("0", {}, label = "Importe", currencySymbol = "€", error = "Introduce un importe mayor que 0")
                }
                NoteField(note, { note = it })
                SectionBand()
            }

            item {
                CatalogLabel("Gastos")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    BudgetProgress(42_000, null, euros, onSetBudget = {})
                    BudgetProgress(42_000, 80_000, euros, onSetBudget = {})
                    BudgetProgress(70_000, 80_000, euros, onSetBudget = {})
                    BudgetProgress(83_500, 80_000, euros, onSetBudget = {})
                    CategoryChips(category, { category = it })
                }
                ExpenseRow(expenseCategory("comida"), "Cena en Alfama", "48 €", placeName = "Tasca do Chico")
                ExpenseRow(expenseCategory("transporte"), "", "6 €")
                SectionBand()
            }

            item {
                CatalogLabel("Tarjetas y estados vacíos")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TripCard("Lisboa con amigos", "Lisboa", "3–7 may", 12, onClick = {})
                    TripCard("Escapada a Roma", "Roma", "2–4 feb", 1, onClick = {}, past = true)
                }
                EmptyState(
                    icon = Icons.Filled.Flight,
                    title = "Aún no tienes viajes",
                    message = "Crea uno y empieza a llenar los días.",
                    actionLabel = "Crear viaje",
                    onAction = {}
                )
                Box(Modifier.size(96.dp))
            }
        }
    }

    if (showPicker) {
        TpDateRangePickerDialog(onDismiss = { showPicker = false }, onConfirm = { _, _ -> showPicker = false })
    }
    if (showConfirm) {
        ConfirmDialog(
            title = "¿Borrar el viaje?",
            message = "Se borrarán sus lugares, notas y gastos.",
            confirmLabel = "Borrar",
            onConfirm = { showConfirm = false },
            onDismiss = { showConfirm = false }
        )
    }
}

@Composable
private fun CatalogLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = TpColors.TextMuted
    )
}

@Preview(showBackground = true, heightDp = 2400)
@Composable
private fun DesignCatalogPreview() {
    TripPlannerTheme { DesignCatalogScreen() }
}
