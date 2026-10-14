package com.cuchieman.tripplanner.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** One full set of colour roles. Light mirrors tokens.json, dark mirrors tokens-dark.json. */
@Immutable
data class TpPalette(
    val bg: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val chip: Color,
    val border: Color,
    val borderInput: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val cover: Color,
    val onCover: Color,
    val link: Color,
    val timeChip: Color,
    val onTimeChip: Color,
    val inverse: Color,
    val onInverse: Color,
    val inverseLink: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val danger: Color,
    val onDanger: Color,
    val success: Color,
    val warning: Color,
    val onDay: Color,
    val days: List<Color>,
) {
    /** Colour of day [index] (0-based), cycling after 8 days. */
    fun day(index: Int): Color = days[index.mod(days.size)]
}

// Change values in the JSON first, run contrast.py, then copy here.
val LightPalette = TpPalette(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFF1F3F5),
    surfaceRaised = Color(0xFFFFFFFF),
    chip = Color(0xFFE9ECEF),
    border = Color(0xFFDEE2E6),
    borderInput = Color(0xFF868E96),
    text = Color(0xFF1B1F24),
    textMuted = Color(0xFF5C636A),
    accent = Color(0xFF1C7C74),
    onAccent = Color(0xFFFFFFFF),
    cover = Color(0xFF1C7C74),
    onCover = Color(0xFFFFFFFF),
    link = Color(0xFF1F5FBF),
    timeChip = Color(0xFFE7F0FD),
    onTimeChip = Color(0xFF1A4F9E),
    inverse = Color(0xFF25292E),
    onInverse = Color(0xFFFFFFFF),
    inverseLink = Color(0xFF8FD3CB),
    accentContainer = Color(0xFFD7F0EC),
    onAccentContainer = Color(0xFF0E4A45),
    danger = Color(0xFFC92A2A),
    onDanger = Color(0xFFFFFFFF),
    success = Color(0xFF2B7A3D),
    warning = Color(0xFFA85D00),
    onDay = Color(0xFFFFFFFF),
    days = listOf(
        Color(0xFFC2255C),
        Color(0xFF5F3DC4),
        Color(0xFF0B7285),
        Color(0xFFC03E0B),
        Color(0xFF1864AB),
        Color(0xFF237A35),
        Color(0xFF862E9C),
        Color(0xFF495057),
    ),
)

val DarkPalette = TpPalette(
    bg = Color(0xFF121417),
    surface = Color(0xFF0B0D0F),
    surfaceRaised = Color(0xFF1D2126),
    chip = Color(0xFF2A2F35),
    border = Color(0xFF30363D),
    borderInput = Color(0xFF8B949E),
    text = Color(0xFFE6E8EB),
    textMuted = Color(0xFFA3ABB5),
    accent = Color(0xFF5CC2B6),
    onAccent = Color(0xFF0B1F1D),
    cover = Color(0xFF1F5F59),
    onCover = Color(0xFFFFFFFF),
    link = Color(0xFF86B2FF),
    timeChip = Color(0xFF1D2B44),
    onTimeChip = Color(0xFFB3CDFF),
    inverse = Color(0xFFE6E8EB),
    onInverse = Color(0xFF1B1F24),
    inverseLink = Color(0xFF17665F),
    accentContainer = Color(0xFF12403B),
    onAccentContainer = Color(0xFFBFE9E3),
    danger = Color(0xFFFF7B72),
    onDanger = Color(0xFF2B0B0A),
    success = Color(0xFF5BC27A),
    warning = Color(0xFFE3A84F),
    onDay = Color(0xFFFFFFFF),
    days = listOf(
        Color(0xFFC2255C),
        Color(0xFF7048E8),
        Color(0xFF0B7285),
        Color(0xFFC03E0B),
        Color(0xFF1864AB),
        Color(0xFF237A35),
        Color(0xFF9C36B5),
        Color(0xFF66707C),
    ),
)

val LocalTpPalette = staticCompositionLocalOf { LightPalette }

/** The current palette's roles, so components keep writing `TpColors.Accent`. */
object TpColors {
    val Bg: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.bg
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.surface
    val SurfaceRaised: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.surfaceRaised
    val Chip: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.chip
    val Border: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.border
    val BorderInput: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.borderInput
    val Text: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.text
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.textMuted
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.accent
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.onAccent
    val Cover: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.cover
    val OnCover: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.onCover
    val Link: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.link
    val TimeChip: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.timeChip
    val OnTimeChip: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.onTimeChip
    val Inverse: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.inverse
    val OnInverse: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.onInverse
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.danger
    val OnDanger: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.onDanger
    val Success: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.success
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.warning
    val OnDay: Color @Composable @ReadOnlyComposable get() = LocalTpPalette.current.onDay

    @Composable
    @ReadOnlyComposable
    fun day(index: Int): Color = LocalTpPalette.current.day(index)
}

object TpSpace {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object TpRadius {
    val sm = 6.dp
    val md = 8.dp
    val lg = 16.dp
    val xl = 24.dp
}

object TpSize {
    val touchMin = 48.dp
    val pinInline = 24.dp
    val pinMap = 28.dp
    val sectionBand = 12.dp
    val headerCover = 180.dp
}
