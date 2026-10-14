@file:OptIn(ExperimentalFoundationApi::class)

package com.cuchieman.tripplanner.ui.screens.trip

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuchieman.tripplanner.data.PlaceTarget
import com.cuchieman.tripplanner.data.TripPlan
import com.cuchieman.tripplanner.data.target
import com.cuchieman.tripplanner.data.local.ChecklistItemEntity
import com.cuchieman.tripplanner.data.local.ExpenseEntity
import com.cuchieman.tripplanner.data.local.PlaceEntity
import com.cuchieman.tripplanner.data.local.PlaceListEntity
import com.cuchieman.tripplanner.data.local.hasCoordinates
import com.cuchieman.tripplanner.ui.appViewModel
import com.cuchieman.tripplanner.ui.components.ActionLinks
import com.cuchieman.tripplanner.ui.components.BudgetProgress
import com.cuchieman.tripplanner.ui.components.CompactPlaceRow
import com.cuchieman.tripplanner.ui.components.ConfirmDialog
import com.cuchieman.tripplanner.ui.components.DayChipRow
import com.cuchieman.tripplanner.ui.components.EmptyState
import com.cuchieman.tripplanner.ui.components.ExpenseRow
import com.cuchieman.tripplanner.ui.components.NoteField
import com.cuchieman.tripplanner.ui.components.PlaceRow
import com.cuchieman.tripplanner.ui.components.SectionBand
import com.cuchieman.tripplanner.ui.components.SectionHeader
import com.cuchieman.tripplanner.ui.components.TpDateRangePickerDialog
import com.cuchieman.tripplanner.ui.components.TpExtendedFab
import com.cuchieman.tripplanner.ui.components.TpRoundFab
import com.cuchieman.tripplanner.ui.components.TpTabRow
import com.cuchieman.tripplanner.ui.components.TpTopBar
import com.cuchieman.tripplanner.ui.components.TravelSegment
import com.cuchieman.tripplanner.ui.components.TripHeader
import com.cuchieman.tripplanner.ui.components.expenseCategory
import com.cuchieman.tripplanner.ui.dateToUtcMillis
import com.cuchieman.tripplanner.ui.dayDistanceKm
import com.cuchieman.tripplanner.ui.distanceText
import com.cuchieman.tripplanner.ui.formatKm
import com.cuchieman.tripplanner.ui.formatDayChip
import com.cuchieman.tripplanner.ui.formatDayTitle
import com.cuchieman.tripplanner.ui.formatMoney
import com.cuchieman.tripplanner.ui.formatRange
import com.cuchieman.tripplanner.ui.formatTimeRange
import com.cuchieman.tripplanner.ui.openDayRoute
import com.cuchieman.tripplanner.ui.openDirections
import com.cuchieman.tripplanner.ui.rememberCoverBitmap
import com.cuchieman.tripplanner.ui.theme.TpColors
import com.cuchieman.tripplanner.ui.utcMillisToDate
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate

const val TAB_OVERVIEW = 0
const val TAB_ITINERARY = 1
const val TAB_BUDGET = 2

/** S04 (Resumen), S05 (Itinerario) and S09 (Gastos), plus the sheets S06, S07, S11, S13, S14. */
@Composable
fun TripScreen(
    tripId: String,
    initialTab: Int,
    onBack: () -> Unit,
    onOpenMap: (day: LocalDate?) -> Unit,
    onOpenExpense: (expenseId: String?, placeId: String?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = appViewModel { TripViewModel(tripId, it.repository, it.geocoding, it.appScope, it.covers, it.deletedTrip) }
    val state by vm.state.collectAsStateWithLifecycle()
    val search by vm.search.ui.collectAsStateWithLifecycle()
    val notesDraft by vm.notesDraft.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // Height of the pinned tabs + day chips, so scrolling to a day lands below them, not under them.
    var stickyHeightPx by remember { mutableIntStateOf(0) }

    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var selectedDay by rememberSaveable { mutableIntStateOf(0) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    var addTarget by remember { mutableStateOf<PlaceTarget?>(null) }
    var detailPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var showBudget by remember { mutableStateOf(false) }
    var showDates by remember { mutableStateOf(false) }
    var compact by rememberSaveable { mutableStateOf(false) }
    // Null when not selecting; otherwise the ids of the selected places (move or delete several).
    var selection by remember { mutableStateOf<Set<String>?>(null) }
    var bulkMoving by remember { mutableStateOf(false) }
    var newList by remember { mutableStateOf(false) }
    var renamingList by remember { mutableStateOf<PlaceListEntity?>(null) }
    var deletingList by remember { mutableStateOf<PlaceListEntity?>(null) }
    var listMenu by remember { mutableStateOf<String?>(null) }
    var newItem by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(tab) { selection = null }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flushNotes() }

    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            val result = snackbar.showSnackbar(
                message.text,
                actionLabel = message.actionLabel,
                duration = if (message.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
        }
    }

    val current = state
    LaunchedEffect(current) { if (current is TripUiState.Missing) onBack() }
    if (current !is TripUiState.Ready) {
        Box(Modifier.fillMaxSize().background(TpColors.Bg))
        return
    }
    val plan = current.plan
    val dayIndex = selectedDay.coerceIn(0, plan.days.lastIndex)

    fun toggle(key: String) {
        collapsed = if (key in collapsed) collapsed - key else collapsed + key
    }

    val rows = when (tab) {
        TAB_OVERVIEW -> overviewRows(plan, collapsed)
        TAB_ITINERARY -> itineraryRows(plan, collapsed, compact)
        else -> budgetRows(plan)
    }
    val cover by rememberCoverBitmap(plan.trip.coverUri)

    // While a place is dragged the list shows dragRows, reordered live by the library. On drop
    // the new day or list and the index are read off it and saved. It is cleared when the saved
    // order comes back from the database, so the row does not flash back to where it was.
    var dragRows by remember { mutableStateOf<List<TripRow>?>(null) }
    LaunchedEffect(rows) { dragRows = null }
    val shownRows = dragRows ?: rows
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val current = dragRows ?: return@rememberReorderableLazyListState
        val f = current.indexOfFirst { it.key == from.key }
        val t = current.indexOfFirst { it.key == to.key }
        if (f >= 0 && t >= 0) dragRows = current.toMutableList().apply { add(t, removeAt(f)) }
    }

    fun dropPlace(place: PlaceEntity) {
        val list = dragRows ?: return
        val drop = dropTarget(list, place.id)
        if (drop == null || (drop.first == place.target && drop.second == place.position)) {
            dragRows = null // dropped where it was, or above the first section
            return
        }
        vm.reorder(place.id, drop.first, drop.second, onFailed = { dragRows = null })
    }

    Scaffold(
        topBar = {
            TpTopBar("", onBack = onBack, actions = {
                if (tab == TAB_ITINERARY) {
                    IconButton(onClick = { compact = !compact }) {
                        Icon(
                            if (compact) Icons.Filled.ViewAgenda else Icons.Filled.ViewHeadline,
                            contentDescription = if (compact) "Vista detallada" else "Vista compacta por horas"
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Ajustes del viaje")
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            selection?.let { selected ->
                SelectionBar(
                    count = selected.size,
                    onMove = { bulkMoving = true },
                    onDelete = {
                        vm.deletePlaces(selected.toList())
                        selection = null
                    },
                    onCancel = { selection = null }
                )
            }
        },
        floatingActionButton = {
            if (selection != null) {
                // Selecting: the bottom bar has the actions.
            } else if (tab == TAB_BUDGET) {
                TpExtendedFab("Añadir gasto", Icons.Filled.Add, onClick = { onOpenExpense(null, null) })
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TpRoundFab(Icons.Filled.Map, "Ver mapa", onClick = {
                        onOpenMap(if (tab == TAB_ITINERARY) plan.days[dayIndex] else null)
                    })
                    TpRoundFab(Icons.Filled.Add, "Añadir lugar", onClick = {
                        addTarget = if (tab == TAB_ITINERARY) {
                            PlaceTarget.Day(plan.days[dayIndex].toEpochDay())
                        } else {
                            PlaceTarget.InList(plan.lists.first { it.isDefault }.id)
                        }
                    })
                }
            }
        },
        containerColor = TpColors.Bg
    ) { padding ->
        LazyColumn(
            state = listState,
            // Padding on the list itself, not contentPadding: a sticky header pins to the top of the
            // viewport, and with contentPadding that is behind the top app bar.
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            item(key = "header") {
                TripHeader(
                    title = plan.trip.title,
                    dates = formatRange(plan.start, plan.end),
                    onDatesClick = { showDates = true },
                    cover = cover
                )
                Spacer(Modifier.height(8.dp))
            }
            stickyHeader(key = "tabs") {
                Column(
                    Modifier
                        .background(TpColors.Bg)
                        .onSizeChanged { stickyHeightPx = it.height }
                ) {
                    TpTabRow(listOf("Resumen", "Itinerario", "Gastos"), tab, { tab = it })
                    if (tab == TAB_ITINERARY) {
                        DayChipRow(
                            labels = plan.days.map(::formatDayChip),
                            descriptions = plan.days.mapIndexed { i, d -> "Día ${i + 1}, ${formatDayTitle(d)}" },
                            selectedIndex = dayIndex,
                            onSelect = { i ->
                                selectedDay = i
                                val target = rows.indexOfFirst { it is TripRow.DayHeader && it.index == i }
                                if (target >= 0) scope.launch { listState.animateScrollToItem(target + 2, -stickyHeightPx) }
                            },
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
            items(shownRows, key = { it.key }) { row ->
                // Every row is a drop target (headers included, so a place can cross into
                // another day); only place rows have a drag handle.
                ReorderableItem(reorderState, key = row.key) { isDragging ->
                when (row) {
                    is TripRow.Band -> SectionBand()
                    is TripRow.NotesHeader -> SectionHeader("Notas", row.expanded, { toggle(row.key) })
                    is TripRow.Notes -> NoteField(
                        value = notesDraft ?: plan.trip.notes,
                        onValueChange = vm::onNotesChange,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    is TripRow.ListHeader -> Box {
                        SectionHeader(
                            row.list.title,
                            row.expanded,
                            { toggle(row.key) },
                            onMore = { listMenu = row.list.id },
                            subtitle = when (row.count) {
                                0 -> null
                                1 -> "1 lugar"
                                else -> "${row.count} lugares"
                            }
                        )
                        Box(Modifier.align(Alignment.TopEnd)) {
                            DropdownMenu(expanded = listMenu == row.list.id, onDismissRequest = { listMenu = null }) {
                                DropdownMenuItem(
                                    text = { Text("Renombrar") },
                                    onClick = { renamingList = row.list; listMenu = null }
                                )
                                if (!row.list.isDefault) {
                                    DropdownMenuItem(
                                        text = { Text("Borrar lista") },
                                        onClick = { deletingList = row.list; listMenu = null }
                                    )
                                }
                            }
                        }
                    }
                    is TripRow.ChecklistHeader -> SectionHeader(
                        "Lista de equipaje",
                        row.expanded,
                        { toggle(row.key) },
                        subtitle = if (row.total > 0) "${row.done} de ${row.total} listos" else null
                    )
                    is TripRow.ChecklistItem -> ChecklistRow(
                        item = row.item,
                        onToggle = { vm.setChecklistDone(row.item.id, it) },
                        onDelete = { vm.deleteChecklistItem(row.item.id) }
                    )
                    is TripRow.ChecklistAdd -> ChecklistAddRow(
                        value = newItem,
                        onValueChange = { newItem = it },
                        onAdd = {
                            vm.addChecklistItem(newItem)
                            newItem = ""
                        }
                    )
                    is TripRow.NewListLink -> ActionLinks(listOf("Nueva lista" to { newList = true }))
                    is TripRow.DayHeader -> SectionHeader(
                        "Día ${row.index + 1} · ${formatDayTitle(row.day)}",
                        row.expanded,
                        { toggle(row.key) },
                        subtitle = row.subtitle
                    )
                    is TripRow.AddLink -> ActionLinks(
                        buildList {
                            add("Añadir lugar" to { addTarget = row.target })
                            if (row.selectable && selection == null) add("Seleccionar" to { selection = emptySet() })
                            if (row.routeable) {
                                val day = (row.target as PlaceTarget.Day).epochDay
                                add("Ruta en Google Maps" to {
                                    openDayRoute(context, plan.placesOn(LocalDate.ofEpochDay(day)))
                                    Unit
                                })
                            }
                        }
                    )
                    is TripRow.Empty -> Text(
                        row.text,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TpColors.TextMuted
                    )
                    is TripRow.Place -> {
                        val selected = selection
                        val id = row.place.id
                        val onRowClick = {
                            if (selected != null) {
                                selection = if (id in selected) selected - id else selected + id
                            } else {
                                detailPlaceId = id
                            }
                        }
                        val handle = Modifier
                            .longPressDraggableHandle(
                                enabled = selected == null,
                                onDragStarted = { dragRows = rows },
                                onDragStopped = { dropPlace(row.place) }
                            )
                            // Dragging is not available to TalkBack; these do the same job.
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction("Subir") { vm.nudge(id, -1); true },
                                    CustomAccessibilityAction("Bajar") { vm.nudge(id, 1); true },
                                    CustomAccessibilityAction("Mover a otro día") { detailPlaceId = id; true }
                                )
                            }
                        val pinColor = row.dayIndex?.let { TpColors.day(it) } ?: TpColors.Accent
                        val time = formatTimeRange(row.place.startMinute, row.place.endMinute)
                        if (compact && tab == TAB_ITINERARY) {
                            CompactPlaceRow(
                                name = row.place.name,
                                pinColor = pinColor,
                                number = row.number,
                                time = time,
                                dragging = isDragging,
                                selected = selected?.contains(id),
                                onClick = onRowClick,
                                modifier = handle
                            )
                        } else {
                            PlaceRow(
                                name = row.place.name,
                                note = row.place.note,
                                pinColor = pinColor,
                                number = row.number,
                                time = time,
                                category = row.place.category?.let(::expenseCategory),
                                dragging = isDragging,
                                selected = selected?.contains(id),
                                onClick = onRowClick,
                                modifier = handle
                            )
                        }
                    }
                    is TripRow.Travel -> TravelSegment(row.text, onDirections = { openDirections(context, row.to) })
                    is TripRow.BudgetSummary -> Column(Modifier.padding(16.dp)) {
                        BudgetProgress(
                            spentMinor = plan.spentMinor,
                            budgetMinor = plan.trip.budgetMinor,
                            format = { formatMoney(it, plan.trip.currency) },
                            onSetBudget = { showBudget = true }
                        )
                        if (plan.trip.budgetMinor != null) {
                            ActionLinks(
                                listOf("Cambiar presupuesto" to { showBudget = true }),
                                startPadding = 0.dp
                            )
                        }
                    }
                    is TripRow.Category -> Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            expenseCategory(row.category).label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TpColors.Text
                        )
                        Text(
                            formatMoney(row.amountMinor, plan.trip.currency),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TpColors.TextMuted
                        )
                    }
                    is TripRow.Expense -> ExpenseRow(
                        category = expenseCategory(row.expense.category),
                        description = row.expense.description,
                        amount = formatMoney(row.expense.amountMinor, row.expense.currency),
                        placeName = row.expense.placeId?.let { plan.place(it)?.name },
                        onClick = { onOpenExpense(row.expense.id, null) }
                    )
                    is TripRow.NoExpenses -> EmptyState(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = "Sin gastos todavía",
                        message = "Apunta lo que vayas pagando y verás cuánto te queda."
                    )
                }
                }
            }
        }
    }

    addTarget?.let { target ->
        AddPlaceSheet(
            targetLabel = plan.labelOf(target),
            search = search,
            onQueryChange = vm.search::onQueryChange,
            onChoose = { vm.addFromSearch(target, it); addTarget = null },
            onManual = { vm.addManual(target, it); addTarget = null },
            onDismiss = { vm.search.clear(); addTarget = null }
        )
    }

    detailPlaceId?.let { id ->
        // A place deleted elsewhere simply stops showing; the stale id is harmless.
        val place = plan.place(id)
        if (place != null) {
            PlaceDetailSheet(
                place = place,
                plan = plan,
                onSave = { name, note, address -> vm.saveDetails(id, name, note, address) },
                onSetTime = { s, e -> vm.setTime(id, s, e) },
                onSetCategory = { vm.setCategory(id, it) },
                onSetLocation = { vm.setLocationFromText(id, it) },
                onClearLocation = { vm.clearLocation(id) },
                onMove = { target, label -> vm.move(id, target, label) },
                onNudge = { vm.nudge(id, it) },
                onDelete = { vm.delete(id); detailPlaceId = null },
                onAddExpense = { detailPlaceId = null; onOpenExpense(null, id) },
                onDismiss = { detailPlaceId = null }
            )
        }
    }

    selection?.let { selected ->
        if (bulkMoving) {
            MoveTargetsSheet(
                plan = plan,
                onPick = { target, label ->
                    vm.movePlaces(selected.toList(), target, label)
                    bulkMoving = false
                    selection = null
                },
                onDismiss = { bulkMoving = false }
            )
        }
    }

    if (newList) {
        TextInputDialog(
            title = "Nueva lista",
            label = "Nombre",
            initial = "",
            confirmLabel = "Crear",
            onConfirm = { vm.createList(it); newList = false },
            onDismiss = { newList = false }
        )
    }
    renamingList?.let { list ->
        TextInputDialog(
            title = "Renombrar lista",
            label = "Nombre",
            initial = list.title,
            confirmLabel = "Guardar",
            onConfirm = { vm.renameList(list.id, it); renamingList = null },
            onDismiss = { renamingList = null }
        )
    }
    deletingList?.let { list ->
        val count = plan.placesIn(list.id).size
        ConfirmDialog(
            title = "¿Borrar «${list.title}»?",
            message = when (count) {
                0 -> "La lista está vacía."
                1 -> "También se borrará el lugar que tiene."
                else -> "También se borrarán sus $count lugares."
            },
            confirmLabel = "Borrar",
            onConfirm = { vm.deleteList(list.id); deletingList = null },
            onDismiss = { deletingList = null }
        )
    }

    if (showBudget) {
        BudgetDialog(
            currentMinor = plan.trip.budgetMinor,
            currency = plan.trip.currency,
            onSave = { vm.setBudget(it); showBudget = false },
            onDismiss = { showBudget = false }
        )
    }

    if (showDates) {
        TpDateRangePickerDialog(
            onDismiss = { showDates = false },
            onConfirm = { s, e ->
                vm.changeDates(utcMillisToDate(s), utcMillisToDate(e))
                showDates = false
            },
            initialStartMillis = dateToUtcMillis(plan.start),
            initialEndMillis = dateToUtcMillis(plan.end)
        )
    }
}

// Rows: each tab is flattened into one list so the LazyColumn can scroll to a day and,
// later, so drag-and-drop can cross day boundaries (see architecture.md).

private sealed interface TripRow {
    val key: String

    data class Band(override val key: String) : TripRow
    data class NotesHeader(val expanded: Boolean) : TripRow { override val key get() = "notes" }
    data object Notes : TripRow { override val key get() = "notes-body" }
    data class ListHeader(val list: PlaceListEntity, val expanded: Boolean, val count: Int = 0) : TripRow {
        override val key get() = "list-${list.id}"
    }
    data class ChecklistHeader(val done: Int, val total: Int, val expanded: Boolean) : TripRow {
        override val key get() = "checklist"
    }
    data class ChecklistItem(val item: ChecklistItemEntity) : TripRow { override val key get() = "chk-${item.id}" }
    data object ChecklistAdd : TripRow { override val key get() = "chk-add" }
    data object NewListLink : TripRow { override val key get() = "new-list" }
    data class DayHeader(val day: LocalDate, val index: Int, val expanded: Boolean, val subtitle: String? = null) : TripRow {
        override val key get() = "day-${day.toEpochDay()}"
    }
    data class AddLink(
        val target: PlaceTarget,
        val id: String,
        val selectable: Boolean = false,
        val routeable: Boolean = false,
    ) : TripRow { override val key get() = "add-$id" }
    data class Empty(val id: String, val text: String) : TripRow { override val key get() = "empty-$id" }
    /** [dayIndex] picks the pin colour; null means a list place (accent). */
    data class Place(val place: PlaceEntity, val number: Int, val dayIndex: Int?) : TripRow {
        override val key get() = "place-${place.id}"
    }
    data class Travel(val from: PlaceEntity, val to: PlaceEntity, val text: String) : TripRow {
        override val key get() = "travel-${from.id}-${to.id}"
    }
    data object BudgetSummary : TripRow { override val key get() = "budget" }
    data class Category(val category: String, val amountMinor: Long) : TripRow { override val key get() = "cat-$category" }
    data class Expense(val expense: ExpenseEntity) : TripRow { override val key get() = "exp-${expense.id}" }
    data object NoExpenses : TripRow { override val key get() = "no-exp" }
}

/**
 * Where a dragged place lands: the nearest day or list header above it, and how many places
 * sit between that header and it. Null when it was dropped above the first section.
 */
private fun dropTarget(rows: List<TripRow>, placeId: String): Pair<PlaceTarget, Int>? {
    val at = rows.indexOfFirst { it is TripRow.Place && it.place.id == placeId }
    if (at < 0) return null
    var index = 0
    for (i in at - 1 downTo 0) {
        when (val r = rows[i]) {
            is TripRow.Place -> index++
            is TripRow.DayHeader -> return PlaceTarget.Day(r.day.toEpochDay()) to index
            is TripRow.ListHeader -> return PlaceTarget.InList(r.list.id) to index
            else -> Unit
        }
    }
    return null
}

private fun overviewRows(plan: TripPlan, collapsed: Set<String>): List<TripRow> = buildList {
    val notesOpen = "notes" !in collapsed
    add(TripRow.NotesHeader(notesOpen))
    if (notesOpen) add(TripRow.Notes)
    add(TripRow.Band("band-notes"))

    val checklistOpen = "checklist" !in collapsed
    add(TripRow.ChecklistHeader(plan.checklist.count { it.done }, plan.checklist.size, checklistOpen))
    if (checklistOpen) {
        plan.checklist.forEach { add(TripRow.ChecklistItem(it)) }
        add(TripRow.ChecklistAdd)
    }
    add(TripRow.Band("band-checklist"))

    plan.lists.forEach { list ->
        val places = plan.placesIn(list.id)
        val header = TripRow.ListHeader(list, expanded = true, count = places.size)
        val open = header.key !in collapsed
        add(header.copy(expanded = open))
        if (open) {
            add(TripRow.AddLink(PlaceTarget.InList(list.id), list.id, selectable = places.isNotEmpty()))
            if (places.isEmpty()) add(TripRow.Empty(list.id, "Guarda aquí los sitios que aún no tienen día."))
            places.forEachIndexed { i, p -> add(TripRow.Place(p, i + 1, null)) }
        }
        add(TripRow.Band("band-${list.id}"))
    }
    add(TripRow.NewListLink)
}

/** "3 lugares · 2,4 km en línea recta" under a day's title (fixes.md #8); null for an empty day. */
private fun daySummary(places: List<PlaceEntity>): String? {
    if (places.isEmpty()) return null
    val count = if (places.size == 1) "1 lugar" else "${places.size} lugares"
    return dayDistanceKm(places)?.let { "$count · ${formatKm(it)} en línea recta" } ?: count
}

private fun itineraryRows(plan: TripPlan, collapsed: Set<String>, compact: Boolean): List<TripRow> = buildList {
    plan.days.forEachIndexed { index, day ->
        val places = plan.placesOn(day)
        val header = TripRow.DayHeader(day, index, expanded = true, subtitle = daySummary(places))
        val open = header.key !in collapsed
        add(header.copy(expanded = open))
        if (open) {
            add(
                TripRow.AddLink(
                    PlaceTarget.Day(day.toEpochDay()),
                    "d${day.toEpochDay()}",
                    selectable = places.isNotEmpty(),
                    routeable = places.count { it.hasCoordinates } >= 2
                )
            )
            if (places.isEmpty()) add(TripRow.Empty("d${day.toEpochDay()}", "Día libre por ahora."))
            places.forEachIndexed { i, p ->
                // The compact view is one line per place: no distance rows in between.
                if (i > 0 && !compact) distanceText(places[i - 1], p)?.let { add(TripRow.Travel(places[i - 1], p, it)) }
                add(TripRow.Place(p, i + 1, index))
            }
        }
        add(TripRow.Band("band-d$index"))
    }
}

private fun budgetRows(plan: TripPlan): List<TripRow> = buildList {
    add(TripRow.BudgetSummary)
    add(TripRow.Band("band-budget"))
    if (plan.expenses.isEmpty()) {
        add(TripRow.NoExpenses)
    } else {
        plan.spentByCategory.forEach { (category, amount) -> add(TripRow.Category(category, amount)) }
        add(TripRow.Band("band-cats"))
        plan.expenses.forEach { add(TripRow.Expense(it)) }
    }
}
