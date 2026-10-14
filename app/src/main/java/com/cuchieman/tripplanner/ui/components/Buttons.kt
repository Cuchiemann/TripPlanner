package com.cuchieman.tripplanner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.cuchieman.tripplanner.ui.theme.TpColors
import com.cuchieman.tripplanner.ui.theme.TpSize

enum class TpButtonVariant { Primary, Secondary, Text, Danger }

@Composable
fun TpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: TpButtonVariant = TpButtonVariant.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current
            )
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    val buttonModifier = modifier
        .heightIn(min = TpSize.touchMin)
        .semantics { if (loading) stateDescription = "Cargando" }
    val isEnabled = enabled && !loading

    when (variant) {
        TpButtonVariant.Primary -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = isEnabled,
            content = content
        )
        TpButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = isEnabled,
            border = BorderStroke(1.dp, TpColors.BorderInput),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TpColors.Text),
            content = content
        )
        TpButtonVariant.Text -> TextButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = isEnabled,
            colors = ButtonDefaults.textButtonColors(contentColor = TpColors.Link),
            content = content
        )
        TpButtonVariant.Danger -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = isEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = TpColors.Danger,
                contentColor = TpColors.OnDanger
            ),
            content = content
        )
    }
}

@Composable
fun TpExtendedFab(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
) {
    ExtendedFloatingActionButton(
        text = { Text(text, style = MaterialTheme.typography.labelLarge) },
        icon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        expanded = expanded,
        // Material's extended FAB keeps its label out of the merged semantics, so TalkBack
        // only said "button". Found by FlowsTest.assertClickablesLabelled.
        modifier = modifier.semantics { contentDescription = text },
        containerColor = TpColors.Accent,
        contentColor = TpColors.OnAccent
    )
}

@Composable
fun TpRoundFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        containerColor = TpColors.Inverse,
        contentColor = TpColors.OnInverse
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = TpColors.Danger)
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
