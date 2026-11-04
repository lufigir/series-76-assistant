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
 * An engineering tool: white or near-black surfaces, cool grays, hairlines in place of shadows, and one blue for what
 * can be pressed. Amber and red mean a problem and nothing else. Dynamic color stays off, because a wallpaper-derived
 * palette would not keep these contrast pairs, and the status colors have to mean the same on every phone.
 */
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF0B57A4),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDCE8F6),
        onPrimaryContainer = Color(0xFF07305C),
        secondary = Color(0xFF4A5563),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8EBEF),
        onSecondaryContainer = Color(0xFF1F2730),
        tertiary = Color(0xFF7F5300),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFCEFD2),
        onTertiaryContainer = Color(0xFF2E1E00),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF14181D),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF14181D),
        surfaceVariant = Color(0xFFE8EBEF),
        onSurfaceVariant = Color(0xFF56606B),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F8FA),
        surfaceContainer = Color(0xFFF1F3F5),
        surfaceContainerHigh = Color(0xFFEBEDF0),
        surfaceContainerHighest = Color(0xFFE3E6EA),
        outline = Color(0xFF7C8691),
        outlineVariant = Color(0xFFDADEE3),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF9CC4F0),
        onPrimary = Color(0xFF00315C),
        primaryContainer = Color(0xFF16406E),
        onPrimaryContainer = Color(0xFFDCE8F6),
        secondary = Color(0xFFB7C0CB),
        onSecondary = Color(0xFF1F2730),
        secondaryContainer = Color(0xFF2F3740),
        onSecondaryContainer = Color(0xFFE8EBEF),
        tertiary = Color(0xFFEFC36A),
        onTertiary = Color(0xFF3F2B00),
        tertiaryContainer = Color(0xFF4D3700),
        onTertiaryContainer = Color(0xFFFCEFD2),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        // A soft near-black instead of pure black is easier on the eyes through long reading of figures.
        background = Color(0xFF121519),
        onBackground = Color(0xFFE5E8EC),
        surface = Color(0xFF121519),
        onSurface = Color(0xFFE5E8EC),
        surfaceVariant = Color(0xFF30363D),
        onSurfaceVariant = Color(0xFFB4BCC5),
        surfaceContainerLowest = Color(0xFF0D1013),
        surfaceContainerLow = Color(0xFF191D21),
        surfaceContainer = Color(0xFF1E2227),
        surfaceContainerHigh = Color(0xFF282D32),
        surfaceContainerHighest = Color(0xFF32383E),
        outline = Color(0xFF858E98),
        outlineVariant = Color(0xFF363C43),
    )

private val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(12.dp),
    )

@OptIn(ExperimentalTextApi::class)
private fun plexSans(weight: Int) =
    Font(
        R.font.plex_sans_regular,
        FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

private val PlexSans = FontFamily(plexSans(400), plexSans(500), plexSans(600))

/** Every Plex Sans digit has the same width, so figures in a column line up without a monospaced face. */
val SpecValueStyle = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)

/** The slashed zero keeps 0 and O apart in a code someone may copy by hand. */
val PartNumberStyle =
    TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = "zero",
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
            bodyLarge = bodyLarge.copy(fontFamily = PlexSans, lineHeight = 24.sp, letterSpacing = 0.1.sp),
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
