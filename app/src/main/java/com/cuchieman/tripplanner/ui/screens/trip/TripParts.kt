@file:OptIn(ExperimentalMaterial3Api::class)

package com.cuchieman.tripplanner.ui.screens.trip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.cuchieman.tripplanner.data.PlaceTarget
import com.cuchieman.tripplanner.data.TripPlan
import com.cuchieman.tripplanner.data.local.ChecklistItemEntity
import com.cuchieman.tripplanner.ui.components.TpButton
import com.cuchieman.tripplanner.ui.components.TpButtonVariant
import com.cuchieman.tripplanner.ui.theme.TpColors
import com.cuchieman.tripplanner.ui.theme.TpRadius

/** Bottom bar while selecting places: how many, move, delete, cancel. */
@Composable
fun SelectionBar(count: Int, onMove: () -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    Surface(color = TpColors.SurfaceRaised, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Terminar selección") }
            Text(
                if (count == 1) "1 seleccionado" else "$count seleccionados",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = TpColors.Text
            )
            TpButton("Mover a…", onClick = onMove, variant = TpButtonVariant.Text, enabled = count > 0)
            TpButton("Borrar", onClick = onDelete, variant = TpButtonVariant.Text, enabled = count > 0)
        }
    }
}

/** A packing-list line: the whole row toggles; the bin deletes (with undo). */
@Composable
fun ChecklistRow(item: ChecklistItemEntity, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = item.done, role = Role.Checkbox, onValueChange = onToggle)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = item.done, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(
            item.text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.done) TpColors.TextMuted else TpColors.Text,
            textDecoration = if (item.done) TextDecoration.LineThrough else null
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Borrar «${item.text}»", tint = TpColors.TextMuted)
        }
    }
}

/** The field at the end of the packing list. */
@Composable
fun ChecklistAddRow(value: String, onValueChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Añadir a la lista…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onAdd() }),
            shape = RoundedCornerShape(TpRadius.md),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TpColors.Accent,
                unfocusedBorderColor = TpColors.BorderInput
            )
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onAdd, enabled = value.isNotBlank()) {
            Icon(Icons.Filled.Add, contentDescription = "Añadir a la lista")
        }
    }
}

/** One text field in a dialog: new list, rename list. */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                shape = RoundedCornerShape(TpRadius.md)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Where to move the selected places: every day, then every list. */
@Composable
fun MoveTargetsSheet(plan: TripPlan, onPick: (PlaceTarget, String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text("Mover a…", style = MaterialTheme.typography.titleLarge, color = TpColors.Text)
            val targets = plan.days.map { PlaceTarget.Day(it.toEpochDay()) } + plan.lists.map { PlaceTarget.InList(it.id) }
            targets.forEach { target ->
                val label = plan.labelOf(target)
                Text(
                    label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { onPick(target, label) }
                        .padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TpColors.Text
                )
            }
        }
    }
}
