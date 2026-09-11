@file:Suppress("MagicNumber")

package com.fitforge.app.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// FitForge brand palette (white + purple). Pinned for both light and dark
// system settings — the design is not authored as a dark theme.
val FitForgePrimaryPurple = Color(0xFF6C3BFF)
val FitForgeDeepPurple = Color(0xFF4B24B8)
val FitForgeSoftPurple = Color(0xFFF0EBFF)
val FitForgeBackground = Color(0xFFF8F9FC)
val FitForgeSurface = Color(0xFFFFFFFF)
val FitForgePrimaryText = Color(0xFF171525)
val FitForgeSecondaryText = Color(0xFF6F6B7A)
val FitForgeBorder = Color(0xFFE6E2EF)

// Warm "burn" accent — distinguishes calories burned from calories consumed
// on the dashboard ring/metrics. Not part of the primary brand pair.
private val BurnCoral = Color(0xFFFF7A50)
private val OnBurnCoral = Color(0xFFFFFFFF)
private val BurnCoralContainer = Color(0xFFFFE6DD)
private val OnBurnCoralContainer = Color(0xFF7A2E12)

// Women's Wellness dominant accent — used only via WomensWellnessTheme.
val FitForgeWellnessPink = Color(0xFFE85D9E)
private val WellnessPinkContainer = Color(0xFFFDF2F7)
private val OnWellnessPinkContainer = Color(0xFF7A1030)

private val FitForgeColorScheme = lightColorScheme(
    primary = FitForgePrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = FitForgeSoftPurple,
    onPrimaryContainer = FitForgeDeepPurple,
    inversePrimary = FitForgeSoftPurple,
    secondary = FitForgeDeepPurple,
    onSecondary = Color.White,
    secondaryContainer = FitForgeSoftPurple,
    onSecondaryContainer = FitForgeDeepPurple,
    tertiary = BurnCoral,
    onTertiary = OnBurnCoral,
    tertiaryContainer = BurnCoralContainer,
    onTertiaryContainer = OnBurnCoralContainer,
    background = FitForgeBackground,
    onBackground = FitForgePrimaryText,
    surface = FitForgeSurface,
    onSurface = FitForgePrimaryText,
    surfaceVariant = Color(0xFFEFEDF5),
    onSurfaceVariant = FitForgeSecondaryText,
    surfaceTint = FitForgePrimaryPurple,
    inverseSurface = FitForgePrimaryText,
    inverseOnSurface = FitForgeBackground,
    outline = FitForgeBorder,
    outlineVariant = Color(0xFFF1EFF7),
)

/** Pill buttons, big rounded cards, soft dialogs — matches the Stitch shape language app-wide. */
private val FitForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FitForgeTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = FitForgeColorScheme,
        typography = Typography(),
        shapes = FitForgeShapes,
        content = content,
    )
}

/**
 * Wraps Women's Wellness screens (Cycle) in a pink-dominant accent while
 * keeping the rest of the FitForge visual language (typography, shapes,
 * surfaces, text colors) consistent with the parent theme.
 */
@Composable
fun WomensWellnessTheme(content: @Composable () -> Unit) {
    val wellnessScheme = FitForgeColorScheme.copy(
        primary = FitForgeWellnessPink,
        onPrimary = Color.White,
        primaryContainer = WellnessPinkContainer,
        onPrimaryContainer = OnWellnessPinkContainer,
        secondary = FitForgeWellnessPink,
        secondaryContainer = WellnessPinkContainer,
        onSecondaryContainer = OnWellnessPinkContainer,
        surfaceTint = FitForgeWellnessPink,
    )
    MaterialTheme(
        colorScheme = wellnessScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
