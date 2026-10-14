package com.cuchieman.tripplanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// Follows the system light/dark setting; dark mode is free (fixes.md #4). No dynamic colour:
// the day colours and accent must stay predictable. The surfaceContainer roles are set from
// the palette so sheets, dialogs and pickers do not pick up Material's default lilac tint.
private fun colorScheme(p: TpPalette, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = p.accentContainer,
        onPrimaryContainer = p.onAccentContainer,
        secondary = p.link,
        onSecondary = p.bg,
        secondaryContainer = p.chip,
        onSecondaryContainer = p.text,
        background = p.bg,
        onBackground = p.text,
        surface = p.bg,
        onSurface = p.text,
        surfaceVariant = p.surface,
        onSurfaceVariant = p.textMuted,
        surfaceTint = p.bg,
        inverseSurface = p.inverse,
        inverseOnSurface = p.onInverse,
        inversePrimary = p.inverseLink,
        error = p.danger,
        onError = p.onDanger,
        outline = p.borderInput,
        outlineVariant = p.border,
        surfaceBright = p.surfaceRaised,
        surfaceDim = p.surface,
        surfaceContainerLowest = p.bg,
        surfaceContainerLow = p.surfaceRaised,
        surfaceContainer = p.surfaceRaised,
        surfaceContainerHigh = p.surfaceRaised,
        surfaceContainerHighest = p.chip,
    )
}

private val TpShapes = Shapes(
    extraSmall = RoundedCornerShape(TpRadius.sm),
    small = RoundedCornerShape(TpRadius.sm),
    medium = RoundedCornerShape(TpRadius.md),
    large = RoundedCornerShape(TpRadius.lg),
    extraLarge = RoundedCornerShape(TpRadius.xl),
)

@Composable
fun TripPlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalTpPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme(palette, darkTheme),
            typography = TpTypography,
            shapes = TpShapes,
            content = content
        )
    }
}
