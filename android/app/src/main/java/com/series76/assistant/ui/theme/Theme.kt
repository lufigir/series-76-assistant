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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.series76.assistant.R

/*
 * An engineering tool with some warmth: ivory and warm-gray surfaces in place of pure white and cool grays, a petrol
 * blue for what can be pressed and what was met, and amber for what needs attention. Red means an error and nothing
 * else. Dynamic color stays off, because a wallpaper-derived palette would not keep these contrast pairs, and the
 * status colors have to mean the same on every phone.
 */
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF0E5C6B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCFE6EA),
        onPrimaryContainer = Color(0xFF00313A),
        secondary = Color(0xFF5B5853),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEAE4DA),
        onSecondaryContainer = Color(0xFF26231E),
        tertiary = Color(0xFF8A5800),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFE3B3),
        onTertiaryContainer = Color(0xFF2C1C00),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        background = Color(0xFFFBF9F5),
        onBackground = Color(0xFF1C1B18),
        surface = Color(0xFFFBF9F5),
        onSurface = Color(0xFF1C1B18),
        surfaceVariant = Color(0xFFEAE4DA),
        onSurfaceVariant = Color(0xFF5D5950),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F2EC),
        surfaceContainer = Color(0xFFF0ECE5),
        surfaceContainerHigh = Color(0xFFEAE5DD),
        surfaceContainerHighest = Color(0xFFE3DED5),
        outline = Color(0xFF7E796F),
        outlineVariant = Color(0xFFE0DACF),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF8FD0DC),
        onPrimary = Color(0xFF00363F),
        primaryContainer = Color(0xFF104E5B),
        onPrimaryContainer = Color(0xFFCFE6EA),
        secondary = Color(0xFFCDC6BB),
        onSecondary = Color(0xFF34302A),
        secondaryContainer = Color(0xFF3A362F),
        onSecondaryContainer = Color(0xFFEAE4DA),
        tertiary = Color(0xFFF2C06B),
        onTertiary = Color(0xFF452B00),
        tertiaryContainer = Color(0xFF573A00),
        onTertiaryContainer = Color(0xFFFFE3B3),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        // A soft warm near-black instead of pure black is easier on the eyes through long reading of figures.
        background = Color(0xFF151412),
        onBackground = Color(0xFFE8E3DB),
        surface = Color(0xFF151412),
        onSurface = Color(0xFFE8E3DB),
        surfaceVariant = Color(0xFF3A362F),
        onSurfaceVariant = Color(0xFFBDB6AA),
        surfaceContainerLowest = Color(0xFF100F0D),
        surfaceContainerLow = Color(0xFF1D1B18),
        surfaceContainer = Color(0xFF221F1C),
        surfaceContainerHigh = Color(0xFF2C2925),
        surfaceContainerHighest = Color(0xFF37332E),
        outline = Color(0xFF8B857A),
        outlineVariant = Color(0xFF3D3933),
    )

private val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
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
val SpecValueStyle = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)

/** The slashed zero keeps 0 and O apart in a code someone may copy by hand. */
val PartNumberStyle =
    TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "zero",
    )

// One step below Material's defaults: engineers scan figures here more than they read prose, and a phone set to a
// large font scale multiplies every size, so the default scale left room for little more than one answer per screen.
private val AppTypography =
    Typography().run {
        Typography(
            displaySmall = displaySmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold),
            headlineLarge = headlineLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold),
            headlineMedium = headlineMedium.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
            headlineSmall = headlineSmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold),
            titleLarge = titleLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
            titleMedium = titleMedium.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
            titleSmall = titleSmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
            bodyLarge = bodyLarge.copy(fontFamily = PlexSans, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
            bodyMedium = bodyMedium.copy(fontFamily = PlexSans, fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.1.sp),
            bodySmall = bodySmall.copy(fontFamily = PlexSans, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
            labelLarge = labelLarge.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium),
            labelMedium = labelMedium.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium),
            labelSmall = labelSmall.copy(fontFamily = PlexSans, fontWeight = FontWeight.Medium),
        )
    }

/** The widest the conversation gets. On a tablet, longer lines and wider tables only spread the figures apart. */
val ReadingWidth = 720.dp

/** Room on each side that centers [ReadingWidth] of content on a wide screen, and none on a phone. */
fun sideRoom(available: Dp): Dp = ((available - ReadingWidth) / 2).coerceAtLeast(0.dp)

/** Below Material's 64 dp: the bars hold a title and an icon or two, and the answers need the height more. */
val TopBarHeight = 52.dp

@Composable
fun Series76Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
