@file:OptIn(ExperimentalMaterial3Api::class)

package com.cuchieman.tripplanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cuchieman.tripplanner.ui.theme.TpColors
import com.cuchieman.tripplanner.ui.theme.TpRadius

@Composable
private fun tpFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TpColors.Accent,
    unfocusedBorderColor = TpColors.BorderInput,
    focusedLabelColor = TpColors.Accent,
    cursorColor = TpColors.Accent,
)

@Composable
fun TpSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    searching: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            when {
                searching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                value.isNotEmpty() -> IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Borrar búsqueda")
                }
                else -> {}
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(TpRadius.md),
        colors = tpFieldColors()
    )
}

/** Amount input. Keeps only digits and one decimal separator; parsing is the caller's job. */
@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    currencySymbol: String,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            val separators = new.count { it == ',' || it == '.' }
            if (separators <= 1 && new.all { it.isDigit() || it == ',' || it == '.' }) onValueChange(new)
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        suffix = { Text(currencySymbol) },
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        shape = RoundedCornerShape(TpRadius.md),
        colors = tpFieldColors()
    )
}

/** Borderless multiline note. Autosave is the caller's job (debounce + flush on stop). */
@Composable
fun NoteField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Escribe notas, enlaces, recordatorios…",
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = TpColors.Accent,
            focusedPlaceholderColor = TpColors.TextMuted,
            unfocusedPlaceholderColor = TpColors.TextMuted,
        )
    )
}

/** Read-only field showing the chosen range; tapping it opens [TpDateRangePickerDialog]. */
@Composable
fun DateRangeField(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Fechas",
    error: String? = null,
) {
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text ?: "",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Elige ida y vuelta") },
            leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null) },
            supportingText = if (error != null) {
                { Text(error) }
            } else null,
            shape = RoundedCornerShape(TpRadius.md),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = TpColors.Text,
                disabledBorderColor = if (error != null) TpColors.Danger else TpColors.BorderInput,
                disabledLabelColor = TpColors.TextMuted,
                disabledLeadingIconColor = TpColors.TextMuted,
                disabledPlaceholderColor = TpColors.TextMuted,
                disabledSupportingTextColor = TpColors.Danger,
            )
        )
        // The disabled field ignores taps, so a transparent layer on top handles them.
        Box(
            Modifier
                .matchParentSize()
                .clickable(role = Role.Button, onClickLabel = "Elegir fechas", onClick = onClick)
        )
    }
}

/** Material date range picker. Returns UTC-midnight millis, as the picker gives them. */
@Composable
fun TpDateRangePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (startMillis: Long, endMillis: Long) -> Unit,
    initialStartMillis: Long? = null,
    initialEndMillis: Long? = null,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartMillis,
        initialSelectedEndDateMillis = initialEndMillis
    )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (start != null && end != null) onConfirm(start, end) },
                enabled = start != null && end != null
            ) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}
