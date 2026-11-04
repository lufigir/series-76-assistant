package com.series76.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.series76.assistant.R

/*
 * The look of a printed data sheet: warm paper or graphite, one signal orange for what can be pressed, hairlines
 * in place of shadows, and figures in a monospaced face. Dynamic color stays off, because a wallpaper-derived
 * palette would not keep these contrast pairs, and the status colors have to mean the same on every phone.
 */
private val LightColors =
    lightColorScheme(
        primary = Color(0xFFB03F0C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFE2D3),
        onPrimaryContainer = Color(0xFF3F1400),
        secondary = Color(0xFF47525C),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE4E5E2),
        onSecondaryContainer = Color(0xFF20262B),
        tertiary = Color(0xFF7A5200),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFBE7BC),
        onTertiaryContainer = Color(0xFF2E1E00),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        background = Color(0xFFF5F4EF),
        onBackground = Color(0xFF1A1D20),
        surface = Color(0xFFF5F4EF),
        onSurface = Color(0xFF1A1D20),
        surfaceVariant = Color(0xFFE4E3DD),
        onSurfaceVariant = Color(0xFF50565B),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFBFAF6),
        surfaceContainer = Color(0xFFEFEEE8),
        surfaceContainerHigh = Color(0xFFE9E8E2),
        surfaceContainerHighest = Color(0xFFE3E2DC),
        outline = Color(0xFF787D80),
        outlineVariant = Color(0xFFD3D2CB),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFFF9A62),
        onPrimary = Color(0xFF4A1A00),
        primaryContainer = Color(0xFF6B2B08),
        onPrimaryContainer = Color(0xFFFFE2D3),
        secondary = Color(0xFFB6C0C8),
        onSecondary = Color(0xFF20262B),
        secondaryContainer = Color(0xFF353C42),
        onSecondaryContainer = Color(0xFFE4E5E2),
        tertiary = Color(0xFFF0C25C),
        onTertiary = Color(0xFF3F2B00),
        tertiaryContainer = Color(0xFF4D3700),
        onTertiaryContainer = Color(0xFFFBE7BC),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        // A soft graphite instead of pure black is easier on the eyes through long reading of figures.
        background = Color(0xFF15181B),
        onBackground = Color(0xFFE6E5E0),
        surface = Color(0xFF15181B),
        onSurface = Color(0xFFE6E5E0),
        surfaceVariant = Color(0xFF353A3F),
        onSurfaceVariant = Color(0xFFB9BEC2),
        surfaceContainerLowest = Color(0xFF101315),
        surfaceContainerLow = Color(0xFF1B1F22),
        surfaceContainer = Color(0xFF202427),
        surfaceContainerHigh = Color(0xFF2A2E32),
        surfaceContainerHighest = Color(0xFF34393D),
        outline = Color(0xFF878C90),
        outlineVariant = Color(0xFF3A3F44),
    )

private val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )

@OptIn(ExperimentalTextApi::class)
private fun plexSans(weight: Int) =
    Font(
        R.font.plex_sans_regular,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

private val PlexSans = FontFamily(plexSans(400), plexSans(500), plexSans(600))

private val PlexMono =
    FontFamily(
        Font(R.font.plex_mono_regular, FontWeight.Normal),
        Font(R.font.plex_mono_medium, FontWeight.Medium),
        Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
    )

/** Figures in a column line up only in a monospaced face, which is what makes them comparable at a glance. */
val SpecValueStyle = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)

val KeyFigureStyle = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp)

val PartNumberStyle = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)

/** Small capitals with open spacing, the way a data sheet labels its sections. */
val CapsLabelStyle =
    TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.9.sp,
    )

private val AppTypography =
    Typography().run {
        Typography(
            displaySmall = displaySmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold),
            headlineLarge = headlineLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold),
            headlineMedium = headlineMedium.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold),
            headlineSmall = headlineSmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold),
            titleLarge = titleLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold),
            titleMedium = titleMedium.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium),
            titleSmall = titleSmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium),
            bodyLarge = bodyLarge.copy(fontFamily = PlexSans, lineHeight = 25.sp, letterSpacing = 0.1.sp),
            bodyMedium = bodyMedium.copy(fontFamily = PlexSans, letterSpacing = 0.1.sp),
            bodySmall = bodySmall.copy(fontFamily = PlexSans, letterSpacing = 0.2.sp),
            labelLarge = labelLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium),
            labelMedium = labelMedium.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium),
            labelSmall = labelSmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium),
        )
    }

@Composable
fun Series76Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
