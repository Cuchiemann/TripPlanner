package com.cuchieman.tripplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cuchieman.tripplanner.ui.theme.TpColors
import com.cuchieman.tripplanner.ui.theme.TpRadius
import com.cuchieman.tripplanner.ui.theme.TpSize

/** Numbered circle in the day's colour. Decorative: the row around it carries the number for TalkBack. */
@Composable
fun DayPin(
    number: Int?,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = TpSize.pinInline,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        if (number != null) {
            Text(
                text = number.toString(),
                color = TpColors.OnDay,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun TimeChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(TpRadius.sm))
            .background(TpColors.TimeChip)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = TpColors.OnTimeChip,
        style = MaterialTheme.typography.labelMedium
    )
}

/** Straight-line distance to the next stop, with a link to open directions. */
@Composable
fun TravelSegment(
    distanceText: String,
    onDirections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 52.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Directions,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = TpColors.TextMuted
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$distanceText en línea recta",
            color = TpColors.TextMuted,
            style = MaterialTheme.typography.labelMedium
        )
        Text(" · ", color = TpColors.TextMuted, style = MaterialTheme.typography.labelMedium)
        Text(
            text = "Cómo llegar",
            modifier = Modifier
                .heightIn(min = TpSize.touchMin)
                .clickable(role = Role.Button, onClick = onDirections)
                .padding(horizontal = 4.dp, vertical = 14.dp),
            color = TpColors.Link,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
