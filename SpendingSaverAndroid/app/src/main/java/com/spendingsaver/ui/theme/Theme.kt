package com.spendingsaver.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.spendingsaver.R

// --- Accent palettes ---------------------------------------------------------

private data class AccentPalette(val primary: Color, val onPrimary: Color, val container: Color, val onContainer: Color)

private val ACCENTS = mapOf(
    "teal" to AccentPalette(
        primary = Color(0xFF006A60), onPrimary = Color.White,
        container = Color(0xFF6FF6E8), onContainer = Color(0xFF00201C),
    ),
    "blue" to AccentPalette(
        primary = Color(0xFF0061A4), onPrimary = Color.White,
        container = Color(0xFFD1E4FF), onContainer = Color(0xFF001D36),
    ),
    "indigo" to AccentPalette(
        primary = Color(0xFF455CA9), onPrimary = Color.White,
        container = Color(0xFFDCE1FF), onContainer = Color(0xFF00174E),
    ),
    "green" to AccentPalette(
        primary = Color(0xFF386A20), onPrimary = Color.White,
        container = Color(0xFFB7F29A), onContainer = Color(0xFF082100),
    ),
    "purple" to AccentPalette(
        primary = Color(0xFF6750A4), onPrimary = Color.White,
        container = Color(0xFFEADDFF), onContainer = Color(0xFF22005D),
    ),
    "orange" to AccentPalette(
        primary = Color(0xFF8B5000), onPrimary = Color.White,
        container = Color(0xFFFFDDBE), onContainer = Color(0xFF2B1500),
    ),
    "red" to AccentPalette(
        primary = Color(0xFFBA1A1A), onPrimary = Color.White,
        container = Color(0xFFFFDAD6), onContainer = Color(0xFF410002),
    ),
    "pink" to AccentPalette(
        primary = Color(0xFFC81D75), onPrimary = Color.White,
        container = Color(0xFFFFD8E4), onContainer = Color(0xFF3F0021),
    ),
    "slate" to AccentPalette(
        primary = Color(0xFF4A5568), onPrimary = Color.White,
        container = Color(0xFFE2E8F0), onContainer = Color(0xFF1A202C),
    ),
)

private val DefaultAccent: AccentPalette
    get() = ACCENTS["teal"]!!

val ACCENTS_PUBLIC: List<String> = ACCENTS.keys.toList()

fun accentColor(name: String): Color = ACCENTS[name]?.primary ?: DefaultAccent.primary

// --- Base color schemes ------------------------------------------------------

private val BaseDarkScheme = darkColorScheme(
    primary = Color(0xFF80F0E6),
    onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF004F46),
    onPrimaryContainer = Color(0xFF6FF6E8),
    inversePrimary = Color(0xFF006A60),
    secondary = Color(0xFFBBC7C8),
    onSecondary = Color(0xFF263233),
    secondaryContainer = Color(0xFF3C494A),
    onSecondaryContainer = Color(0xFFD7E3E3),
    tertiary = Color(0xFFC5C2DC),
    onTertiary = Color(0xFF2E2D41),
    tertiaryContainer = Color(0xFF45435B),
    onTertiaryContainer = Color(0xFFE2DFFF),
    background = Color(0xFF0F1415),
    onBackground = Color(0xFFE1E3E3),
    surface = Color(0xFF0F1415),
    onSurface = Color(0xFFE1E3E3),
    surfaceVariant = Color(0xFF1F2728),
    onSurfaceVariant = Color(0xFFBFD6D4),
    surfaceTint = Color(0xFF80F0E6),
    inverseSurface = Color(0xFFE1E3E3),
    inverseOnSurface = Color(0xFF1A2324),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF6F7978),
    outlineVariant = Color(0xFF3F4948),
    scrim = Color(0xFF000000),
)

private val BaseLightScheme = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6FF6E8),
    onPrimaryContainer = Color(0xFF00201C),
    inversePrimary = Color(0xFF80F0E6),
    secondary = Color(0xFF4C6363),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE8E7),
    onSecondaryContainer = Color(0xFF071F1D),
    tertiary = Color(0xFF5E6178),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2DFFF),
    onTertiaryContainer = Color(0xFF1C1940),
    background = Color(0xFFF8FDFB),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFF8FDFB),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDCE6E4),
    onSurfaceVariant = Color(0xFF3F4948),
    surfaceTint = Color(0xFF006A60),
    inverseSurface = Color(0xFF2E3131),
    inverseOnSurface = Color(0xFFEFF1F1),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6F7978),
    outlineVariant = Color(0xFFBFC9C7),
    scrim = Color(0xFF000000),
)

// --- Typography --------------------------------------------------------------

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

// --- Accent-tinted scheme builder -------------------------------------------

/**
 * The accent palettes are tuned for light surfaces (dark primary, pale
 * container). On a dark surface that gives low-contrast text and glaring
 * containers, so dark mode uses the inverse tones: a lightened primary and a
 * deepened container.
 */
private fun forDarkSurface(accent: AccentPalette): AccentPalette {
    // Material 3 dark primaries sit at roughly the light scheme's container tone;
    // pulling slightly toward the accent keeps it from looking neon.
    val primary = lerp(accent.container, accent.primary, 0.3f)
    return AccentPalette(
        primary = primary,
        onPrimary = lerp(accent.primary, Color.Black, 0.7f),
        container = lerp(accent.primary, Color.Black, 0.45f),
        onContainer = accent.container,
    )
}

private fun tintScheme(baseScheme: ColorScheme, lightAccent: AccentPalette, darkTheme: Boolean): ColorScheme {
    val accent = if (darkTheme) forDarkSurface(lightAccent) else lightAccent
    val containerAlpha = if (darkTheme) 0.45f else 0.18f
    return baseScheme.copy(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryContainer = accent.container,
        onPrimaryContainer = accent.onContainer,
        inversePrimary = accent.primary,
        surfaceTint = accent.primary,
        secondaryContainer = accent.container.copy(alpha = containerAlpha),
        onSecondaryContainer = accent.onContainer.copy(alpha = 0.8f),
        tertiaryContainer = accent.container.copy(alpha = containerAlpha * 0.6f),
        onTertiaryContainer = accent.onContainer.copy(alpha = 0.8f),
    )
}

// --- Theme composition -------------------------------------------------------

private val LocalAccent = compositionLocalOf { DefaultAccent }

@Composable
fun SpendingSaverTheme(
    themeMode: String = "system",
    accentColor: String = "teal",
    textScale: Float = 1f,
    darkTheme: Boolean = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit,
) {
    val accent = ACCENTS[accentColor] ?: DefaultAccent
    val baseScheme = if (darkTheme) BaseDarkScheme else BaseLightScheme
    val colorScheme = tintScheme(baseScheme, accent, darkTheme)

    // Scale every style (size and line height together, so larger text is not clipped).
    fun TextStyle.scaled() = copy(fontSize = fontSize * textScale, lineHeight = lineHeight * textScale)
    val typography = AppTypography.run {
        copy(
            displayLarge = displayLarge.scaled(), displayMedium = displayMedium.scaled(), displaySmall = displaySmall.scaled(),
            headlineLarge = headlineLarge.scaled(), headlineMedium = headlineMedium.scaled(), headlineSmall = headlineSmall.scaled(),
            titleLarge = titleLarge.scaled(), titleMedium = titleMedium.scaled(), titleSmall = titleSmall.scaled(),
            bodyLarge = bodyLarge.scaled(), bodyMedium = bodyMedium.scaled(), bodySmall = bodySmall.scaled(),
            labelLarge = labelLarge.scaled(), labelMedium = labelMedium.scaled(), labelSmall = labelSmall.scaled(),
        )
    }

    CompositionLocalProvider(LocalAccent provides accent) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as androidx.activity.ComponentActivity).window
                // Match the top app bar so the status bar blends in instead of showing a coloured strip.
                window.statusBarColor = colorScheme.surfaceVariant.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }

        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}
