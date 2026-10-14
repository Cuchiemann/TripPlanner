@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.cuchieman.tripplanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cuchieman.tripplanner.ui.theme.TpColors
import com.cuchieman.tripplanner.ui.theme.TpRadius

/** [key] is the value stored in expenses.category (see replica/schema.sql). */
data class ExpenseCategoryOption(val key: String, val label: String, val icon: ImageVector)

val ExpenseCategories = listOf(
    ExpenseCategoryOption("transporte", "Transporte", Icons.Filled.DirectionsCar),
    ExpenseCategoryOption("alojamiento", "Alojamiento", Icons.Filled.Hotel),
    ExpenseCategoryOption("comida", "Comida", Icons.Filled.Restaurant),
    ExpenseCategoryOption("actividades", "Actividades", Icons.Filled.LocalActivity),
    ExpenseCategoryOption("compras", "Compras", Icons.Filled.ShoppingBag),
    ExpenseCategoryOption("otros", "Otros", Icons.Filled.MoreHoriz),
)

fun expenseCategory(key: String): ExpenseCategoryOption =
    ExpenseCategories.firstOrNull { it.key == key } ?: ExpenseCategories.last()

@Composable
fun CategoryChips(
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExpenseCategories.forEach { option ->
            val selected = option.key == selectedKey
            FilterChip(
                selected = selected,
                onClick = { onSelect(option.key) },
                label = { Text(option.label) },
                leadingIcon = {
                    Icon(
                        if (selected) Icons.Filled.Check else option.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                shape = RoundedCornerShape(TpRadius.md),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TpColors.TimeChip,
                    selectedLabelColor = TpColors.OnTimeChip,
                    selectedLeadingIconColor = TpColors.OnTimeChip
                )
            )
        }
    }
}

/**
 * Budget vs spent. Amounts in minor units; [format] turns them into "420 €".
 * The state is always written out too, never shown by colour alone.
 */
@Composable
fun BudgetProgress(
    spentMinor: Long,
    budgetMinor: Long?,
    format: (Long) -> String,
    onSetBudget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (budgetMinor == null) {
        Column(modifier.fillMaxWidth()) {
            Text(
                "Gastado ${format(spentMinor)}",
                style = MaterialTheme.typography.titleMedium,
                color = TpColors.Text
            )
            Spacer(Modifier.height(8.dp))
            TpButton("Fijar presupuesto", onSetBudget, variant = TpButtonVariant.Secondary)
        }
        return
    }

    Column(modifier.fillMaxWidth()) {
        val ratio = if (budgetMinor > 0) spentMinor.toFloat() / budgetMinor else 0f
        val barColor = when {
            ratio > 1f -> TpColors.Danger
            ratio >= 0.8f -> TpColors.Warning
            else -> TpColors.Accent
        }
        val status = when {
            ratio > 1f -> "Te has pasado ${format(spentMinor - budgetMinor)}"
            ratio >= 0.8f -> "Cerca del límite · quedan ${format(budgetMinor - spentMinor)}"
            else -> "Quedan ${format(budgetMinor - spentMinor)}"
        }
        val statusColor = when {
            ratio > 1f -> TpColors.Danger
            ratio >= 0.8f -> TpColors.Warning
            else -> TpColors.TextMuted
        }

        Text(
            "Gastado ${format(spentMinor)} de ${format(budgetMinor)}",
            style = MaterialTheme.typography.titleMedium,
            color = TpColors.Text
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = TpColors.Chip
        )
        Spacer(Modifier.height(4.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium, color = statusColor)
    }
}
