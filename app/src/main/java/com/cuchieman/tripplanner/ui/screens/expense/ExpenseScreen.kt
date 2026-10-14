package com.cuchieman.tripplanner.ui.screens.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cuchieman.tripplanner.data.TripPlan
import com.cuchieman.tripplanner.data.TripRepository
import com.cuchieman.tripplanner.ui.appViewModel
import com.cuchieman.tripplanner.ui.components.CategoryChips
import com.cuchieman.tripplanner.ui.components.MoneyField
import com.cuchieman.tripplanner.ui.components.TpButton
import com.cuchieman.tripplanner.ui.components.TpButtonVariant
import com.cuchieman.tripplanner.ui.components.TpTopBar
import com.cuchieman.tripplanner.ui.currencySymbol
import com.cuchieman.tripplanner.ui.minorToInput
import com.cuchieman.tripplanner.ui.parseMoney
import com.cuchieman.tripplanner.ui.theme.TpColors
import com.cuchieman.tripplanner.ui.theme.TpRadius
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ExpenseForm(
    val loaded: Boolean = false,
    val amount: String = "",
    val category: String = "otros",
    val description: String = "",
    val placeId: String? = null,
    val date: LocalDate? = null,
    val error: String? = null,
    val saving: Boolean = false,
)

class ExpenseViewModel(
    private val tripId: String,
    private val expenseId: String?,
    presetPlaceId: String?,
    private val repository: TripRepository,
) : ViewModel() {
    val plan: StateFlow<TripPlan?> = repository.observePlan(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _form = MutableStateFlow(ExpenseForm())
    val form: StateFlow<ExpenseForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = expenseId?.let { repository.getExpense(it) }
            _form.value = if (existing != null) {
                ExpenseForm(
                    loaded = true,
                    amount = minorToInput(existing.amountMinor, existing.currency),
                    category = existing.category,
                    description = existing.description,
                    placeId = existing.placeId,
                    date = existing.date?.let(LocalDate::ofEpochDay)
                )
            } else {
                // Spending at a place marked as a stay is most likely a stay, and so on.
                val placeType = presetPlaceId?.let { repository.getPlace(it)?.category }
                ExpenseForm(loaded = true, placeId = presetPlaceId, category = placeType ?: "otros")
            }
        }
    }

    val isEditing: Boolean get() = expenseId != null

    fun onAmount(text: String) = _form.update { it.copy(amount = text, error = null) }
    fun onCategory(key: String) = _form.update { it.copy(category = key) }
    fun onDescription(text: String) = _form.update { it.copy(description = text) }
    fun unlinkPlace() = _form.update { it.copy(placeId = null) }

    fun save(onDone: () -> Unit) {
        val plan = plan.value ?: return
        // A double tap reaches here twice before the button redraws as loading.
        if (_form.value.saving) return
        val f = _form.value
        val minor = parseMoney(f.amount, plan.trip.currency)
        if (minor == null) {
            _form.update { it.copy(error = "Introduce un importe mayor que 0") }
            return
        }
        // New expenses are dated today when today falls inside the trip.
        val today = LocalDate.now()
        val date = f.date ?: today.takeIf { !it.isBefore(plan.start) && !it.isAfter(plan.end) }
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                repository.saveExpense(expenseId, tripId, minor, f.category, f.description, f.placeId, date)
            }.onSuccess { onDone() }
                .onFailure { e -> _form.update { it.copy(saving = false, error = e.message ?: "No se ha podido guardar") } }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = expenseId ?: return
        viewModelScope.launch {
            repository.deleteExpense(id)
            onDone()
        }
    }
}

/** S10: add or edit an expense. */
@Composable
fun ExpenseScreen(
    tripId: String,
    expenseId: String?,
    placeId: String?,
    onBack: () -> Unit,
) {
    val vm = appViewModel { ExpenseViewModel(tripId, expenseId, placeId, it.repository) }
    val form by vm.form.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val currentPlan = plan

    Scaffold(
        topBar = { TpTopBar(if (vm.isEditing) "Editar gasto" else "Nuevo gasto", onBack = onBack) },
        containerColor = TpColors.Bg
    ) { padding ->
        if (currentPlan == null || !form.loaded) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MoneyField(
                value = form.amount,
                onValueChange = vm::onAmount,
                label = "Importe",
                currencySymbol = currencySymbol(currentPlan.trip.currency),
                error = form.error
            )
            Text("Categoría", style = MaterialTheme.typography.titleMedium, color = TpColors.Text)
            CategoryChips(selectedKey = form.category, onSelect = vm::onCategory)
            OutlinedTextField(
                value = form.description,
                onValueChange = vm::onDescription,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Descripción (opcional)") },
                singleLine = true,
                shape = RoundedCornerShape(TpRadius.md),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TpColors.Accent,
                    unfocusedBorderColor = TpColors.BorderInput
                )
            )
            form.placeId?.let { currentPlan.place(it) }?.let { place ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Vinculado a ${place.name}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TpColors.TextMuted
                    )
                    TpButton("Quitar", onClick = vm::unlinkPlace, variant = TpButtonVariant.Text)
                }
            }
            TpButton(
                "Guardar",
                onClick = { vm.save(onBack) },
                modifier = Modifier.fillMaxWidth(),
                loading = form.saving
            )
            if (vm.isEditing) {
                TpButton(
                    "Borrar gasto",
                    onClick = { vm.delete(onBack) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = TpButtonVariant.Danger,
                    icon = Icons.Filled.Delete
                )
            }
        }
    }
}
