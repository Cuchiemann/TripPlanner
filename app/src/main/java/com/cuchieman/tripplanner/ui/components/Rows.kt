@file:OptIn(ExperimentalMaterial3Api::class)

package com.cuchieman.tripplanner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cuchieman.tripplanner.ui.theme.TpColors
import com.cuchieman.tripplanner.ui.theme.TpRadius

@Composable
fun PlaceRow(
    name: String,
    note: String,
    pinColor: Color,
    modifier: Modifier = Modifier,
    number: Int? = null,
    time: String? = null,
    category: ExpenseCategoryOption? = null,
    dragging: Boolean = false,
    /** Null outside selection mode; true/false shows a checkbox instead of the pin. */
    selected: Boolean? = null,
    onClick: () -> Unit = {},
) {
    val description = buildString {
        if (number != null) append("$number. ")
        append(name)
        if (category != null) append(", ${category.label}")
        if (time != null) append(", $time")
        if (note.isNotBlank()) append(", nota: $note")
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (selected != null) toggleableState = ToggleableState(selected) },
        color = when {
            dragging -> TpColors.SurfaceRaised
            selected == true -> TpColors.TimeChip
            else -> TpColors.Bg
        },
        shadowElevation = if (dragging) 8.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clearAndSetSemantics { contentDescription = description }
        ) {
            if (selected != null) {
                Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.size(24.dp))
            } else {
                DayPin(number = number, color = pinColor, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = TpColors.Text)
                if (category != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            category.icon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = TpColors.TextMuted
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(category.label, style = MaterialTheme.typography.labelMedium, color = TpColors.TextMuted)
                    }
                }
                if (time != null) {
                    Spacer(Modifier.height(4.dp))
                    TimeChip(time)
                }
                Spacer(Modifier.height(4.dp))
                if (note.isBlank()) {
                    Text(
                        "Añade notas o enlaces",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = TpColors.TextMuted
                    )
                } else {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TpColors.TextMuted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * One line per place: time, pin, name. The compact itinerary (fixes.md #6), for reading a day
 * "line by line, what time I'm doing what".
 */
@Composable
fun CompactPlaceRow(
    name: String,
    pinColor: Color,
    number: Int?,
    time: String?,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
    selected: Boolean? = null,
    onClick: () -> Unit = {},
) {
    val description = buildString {
        if (number != null) append("$number. ")
        append(name)
        if (time != null) append(", $time")
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (selected != null) toggleableState = ToggleableState(selected) },
        color = when {
            dragging -> TpColors.SurfaceRaised
            selected == true -> TpColors.TimeChip
            else -> TpColors.Bg
        },
        shadowElevation = if (dragging) 8.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                time ?: "—",
                modifier = Modifier.width(104.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (time != null) TpColors.OnTimeChip else TpColors.TextMuted
            )
            if (selected != null) {
                Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.size(24.dp))
            } else {
                DayPin(number = number, color = pinColor, size = 20.dp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = TpColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TripCard(
    title: String,
    destination: String,
    dates: String,
    placeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    past: Boolean = false,
    cover: ImageBitmap? = null,
) {
    val places = if (placeCount == 1) "1 lugar" else "$placeCount lugares"
    val description = "$title, $destination, $dates, $places"
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TpRadius.lg),
        color = TpColors.SurfaceRaised,
        shadowElevation = 1.dp
    ) {
        Column(Modifier.clearAndSetSemantics { contentDescription = description }) {
            // The user's photo, or a colour block with the initial until they pick one.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(if (past) TpColors.day(7) else TpColors.Cover),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) {
                    Image(
                        bitmap = cover,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = if (past) 0.6f else 1f
                    )
                } else {
                    Text(
                        title.take(1).uppercase(),
                        style = MaterialTheme.typography.displaySmall,
                        color = TpColors.OnCover
                    )
                }
            }
            Column(Modifier.padding(16.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TpColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$destination · $dates",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TpColors.TextMuted
                )
                Text(places, style = MaterialTheme.typography.labelMedium, color = TpColors.TextMuted)
            }
        }
    }
}

@Composable
fun ExpenseRow(
    category: ExpenseCategoryOption,
    description: String,
    amount: String,
    modifier: Modifier = Modifier,
    placeName: String? = null,
    onClick: () -> Unit = {},
) {
    val title = description.ifBlank { category.label }
    val spoken = buildString {
        append("$title, $amount")
        if (placeName != null) append(", en $placeName")
    }
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), color = TpColors.Bg) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clearAndSetSemantics { contentDescription = spoken },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(TpColors.Chip),
                contentAlignment = Alignment.Center
            ) {
                Icon(category.icon, contentDescription = null, tint = TpColors.Text)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = TpColors.Text)
                if (placeName != null) {
                    Text(placeName, style = MaterialTheme.typography.bodyMedium, color = TpColors.TextMuted)
                }
            }
            Text(amount, style = MaterialTheme.typography.titleMedium, color = TpColors.Text)
        }
    }
}
