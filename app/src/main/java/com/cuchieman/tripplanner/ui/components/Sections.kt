@file:OptIn(ExperimentalMaterial3Api::class)

package com.cuchieman.tripplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cuchieman.tripplanner.ui.theme.TpColors
import com.cuchieman.tripplanner.ui.theme.TpRadius
import com.cuchieman.tripplanner.ui.theme.TpSize

/** Grey band between sections, the way the original separates them instead of using lines. */
@Composable
fun SectionBand(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(TpSize.sectionBand)
            .background(TpColors.Surface)
    )
}

@Composable
fun SectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = TpSize.touchMin)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics(mergeDescendants = true) {
                    stateDescription = if (expanded) "Desplegado" else "Plegado"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TpColors.Text
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, color = TpColors.Text)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TpColors.TextMuted)
                }
            }
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(
                    Icons.Filled.MoreHoriz,
                    contentDescription = "Más opciones de $title",
                    tint = TpColors.TextMuted
                )
            }
        }
    }
}

/** Row of text actions in link colour, separated by "·". */
@Composable
fun ActionLinks(
    actions: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    startPadding: Dp = 12.dp,
) {
    Row(modifier = modifier.padding(start = startPadding), verticalAlignment = Alignment.CenterVertically) {
        actions.forEachIndexed { index, (label, onClick) ->
            if (index > 0) {
                Text(" · ", color = TpColors.TextMuted, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                text = label,
                modifier = Modifier
                    .heightIn(min = TpSize.touchMin)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 4.dp, vertical = 14.dp),
                color = TpColors.Link,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun TpTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = TpColors.Bg,
        contentColor = TpColors.Text,
        indicator = { positions ->
            if (selectedIndex < positions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(positions[selectedIndex]),
                    height = 3.dp,
                    color = TpColors.Accent
                )
            }
        }
    ) {
        tabs.forEachIndexed { index, label ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                selectedContentColor = TpColors.Text,
                unselectedContentColor = TpColors.TextMuted
            )
        }
    }
}

/** Horizontal day selector. [labels] are short ("sáb 3/5"); [descriptions] are read by TalkBack. */
@Composable
fun DayChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    descriptions: List<String> = labels,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(labels) { index, label ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.semantics { contentDescription = descriptions[index] },
                shape = RoundedCornerShape(TpRadius.md),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = TpColors.Chip,
                    labelColor = TpColors.Text,
                    selectedContainerColor = TpColors.Inverse,
                    selectedLabelColor = TpColors.OnInverse
                ),
                border = null
            )
        }
    }
}
