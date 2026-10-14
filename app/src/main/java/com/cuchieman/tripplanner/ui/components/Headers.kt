package com.cuchieman.tripplanner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuchieman.tripplanner.ui.theme.TpColors
import com.cuchieman.tripplanner.ui.theme.TpRadius
import com.cuchieman.tripplanner.ui.theme.TpSize

/** Cover block with a raised card on top: title and a tappable date range. */
@Composable
fun TripHeader(
    title: String,
    dates: String,
    onDatesClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverColor: Color = TpColors.Cover,
    cover: ImageBitmap? = null,
) {
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(TpSize.headerCover)
                .background(coverColor)
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = "Foto de portada",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Surface(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 120.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(TpRadius.lg),
            color = TpColors.SurfaceRaised,
            shadowElevation = 2.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = TpColors.Text)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .heightIn(min = TpSize.touchMin)
                        .clip(RoundedCornerShape(TpRadius.sm))
                        .clickable(role = Role.Button, onClickLabel = "Cambiar fechas", onClick = onDatesClick)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = TpColors.TextMuted
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(dates, style = MaterialTheme.typography.bodyMedium, color = TpColors.TextMuted)
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = TpColors.TextMuted)
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = TpColors.Text,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = TpColors.TextMuted,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            TpButton(actionLabel, onAction)
        }
    }
}
