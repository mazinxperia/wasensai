package com.mazin.wasensai.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode { DARK, LIGHT, SYSTEM }

enum class AccentColor(val color: Color, val label: String) {
    YELLOW(Color(0xFFFFD308), "Amber Graphite"),
    GREEN( Color(0xFF246FF7), "Neptune Core"),
    BLUE(  Color(0xFF9D4EDD), "Nebula Vibe"),
    PURPLE(Color(0xFF1AAF97), "Mint Matrix"),
    RED(   Color(0xFFDE3C3E), "Crimson Edge"),
    AMBER( Color(0xFFA6C307), "Hyper Lime"),
    CYAN(  Color(0xFFD46D03), "Orange Fox"),
    ROSE(  Color(0xFF04A6BB), "Cyanogen"),
    SLATE( Color(0xFFFFFFFF), "Ghost"),
}

data class AppPalette(
    val primary: Color,
    val darkBgTop: Color,
    val darkBgBot: Color,
    val darkCardTop: Color,
    val darkCardBot: Color,
    val darkTextOnCard: Color,
    val darkTextOnBg: Color,
    val lightBgTop: Color,
    val lightBgBot: Color,
    val lightCardTop: Color,
    val lightCardBot: Color,
    val lightTextOnCard: Color,
    val lightTextOnBg: Color,
)

val yellowPalette = AppPalette(
    primary         = Color(0xFFFFD308),
    darkBgTop       = Color(0xFF363534),
    darkBgBot       = Color(0xFF18171B),
    darkCardTop     = Color(0xFFFFD308),
    darkCardBot     = Color(0xFFFBD410),
    darkTextOnCard  = Color(0xFF1A1A19),
    darkTextOnBg    = Color(0xFFFFFFFF),
    lightBgTop      = Color(0xFFFDD835),
    lightBgBot      = Color(0xFFE6B800),
    lightCardTop    = Color(0xFF3D3C3B),
    lightCardBot    = Color(0xFF1A1A19),
    lightTextOnCard = Color(0xFFFFD905),
    lightTextOnBg   = Color(0xFF1A1A19),
)

val bluePalette = AppPalette(
    primary         = Color(0xFF246FF7),
    darkBgTop       = Color(0xFF0A1825),
    darkBgBot       = Color(0xFF020911),
    darkCardTop     = Color(0xFF458CFD),
    darkCardBot     = Color(0xFF246FF7),
    darkTextOnCard  = Color(0xFF02070C),
    darkTextOnBg    = Color(0xFFFFFFFF),
    lightBgTop      = Color(0xFFC9DAFD),
    lightBgBot      = Color(0xFFB2C8F5),
    lightCardTop    = Color(0xFF14212E),
    lightCardBot    = Color(0xFF050D17),
    lightTextOnCard = Color(0xFF1069DA),
    lightTextOnBg   = Color(0xFF050D17),
)

val purplePalette = AppPalette(
    primary         = Color(0xFF9D4EDD),
    darkBgTop       = Color(0xFF151318),
    darkBgBot       = Color(0xFF070408),
    darkCardTop     = Color(0xFFAA66E1),
    darkCardBot     = Color(0xFF7F1BC5),
    darkTextOnCard  = Color(0xFF060008),
    darkTextOnBg    = Color(0xFFFFFFFF),
    lightBgTop      = Color(0xFFDDCCF4),
    lightBgBot      = Color(0xFFCFBBE9),
    lightCardTop    = Color(0xFF1E1D1E),
    lightCardBot    = Color(0xFF0D0C0E),
    lightTextOnCard = Color(0xFF803DC0),
    lightTextOnBg   = Color(0xFF0D0C0E),
)

val greenPalette = AppPalette(
    primary         = Color(0xFF1AAF97),
    darkBgTop       = Color(0xFF0C1B1C),
    darkBgBot       = Color(0xFF020807),
    darkCardTop     = Color(0xFF61D9BC),
    darkCardBot     = Color(0xFF1AAF97),
    darkTextOnCard  = Color(0xFF000000),
    darkTextOnBg    = Color(0xFFFFFFFF),
    lightBgTop      = Color(0xFFD7F3EF),
    lightBgBot      = Color(0xFFB3D3CD),
    lightCardTop    = Color(0xFF142222),
    lightCardBot    = Color(0xFF071110),
    lightTextOnCard = Color(0xFF38AC97),
    lightTextOnBg   = Color(0xFF071110),
)

val redPalette = AppPalette(
    primary         = Color(0xFFDE3C3E),
    darkBgTop       = Color(0xFF171717),
    darkBgBot       = Color(0xFF060606),
    darkCardTop     = Color(0xFFF85457),
    darkCardBot     = Color(0xFFDE3C3E),
    darkTextOnCard  = Color(0xFF030002),
    darkTextOnBg    = Color(0xFFFFFFFF),
    lightBgTop      = Color(0xFFFDDBDE),
    lightBgBot      = Color(0xFFE9BCC3),
    lightCardTop    = Color(0xFF1C1B1D),
    lightCardBot    = Color(0xFF0D0C0C),
    lightTextOnCard = Color(0xFFEE4649),
    lightTextOnBg   = Color(0xFF0D0C0C),
)

val amberPalette = AppPalette(
    primary         = Color(0xFFA6C307),
    darkBgTop       = Color(0xFF0B0B0A),
    darkBgBot       = Color(0xFF030202),
    darkCardTop     = Color(0xFFCAE605),
    darkCardBot     = Color(0xFFA6C307),
    darkTextOnCard  = Color(0xFF000200),
    darkTextOnBg    = Color(0xFFFFFFFF),
    lightBgTop      = Color(0xFFEDF6CB),
    lightBgBot      = Color(0xFFD0DAAB),
    lightCardTop    = Color(0xFF161616),
    lightCardBot    = Color(0xFF080807),
    lightTextOnCard = Color(0xFFC3DD01),
    lightTextOnBg   = Color(0xFF080807),
)

val cyanPalette = AppPalette(
    primary         = Color(0xFFD46D03),
    darkBgTop       = Color(0xFF150F0A),
    darkBgBot       = Color(0xFF080401),
    darkCardTop     = Color(0xFFF69107),
    darkCardBot     = Color(0xFFD46D03),
    darkTextOnCard  = Color(0xFF000000),
    darkTextOnBg    = Color(0xFFFFFFFF),
    lightBgTop      = Color(0xFFFBE0BF),
    lightBgBot      = Color(0xFFEBC99F),
    lightCardTop    = Color(0xFF1A1510),
    lightCardBot    = Color(0xFF110C07),
    lightTextOnCard = Color(0xFFE48904),
    lightTextOnBg   = Color(0xFF110C07),
)

val rosePalette = AppPalette(
    primary         = Color(0xFF01A6BC),
    darkBgTop       = Color(0xFF090D0F),
    darkBgBot       = Color(0xFF020304),
    darkCardTop     = Color(0xFF01D1E3),
    darkCardBot     = Color(0xFF01A6BC),
    darkTextOnCard  = Color(0xFF02070C),
    darkTextOnBg    = Color(0xFFFFFFFF),
    lightBgTop      = Color(0xFFD7F4FA),
    lightBgBot      = Color(0xFFB5D9DE),
    lightCardTop    = Color(0xFF0C0F0F),
    lightCardBot    = Color(0xFF060708),
    lightTextOnCard = Color(0xFF00BEDA),
    lightTextOnBg   = Color(0xFF060708),
)

val slatePalette = AppPalette(
    primary         = Color(0xFFFFFFFF),
    darkBgTop       = Color(0xFF0D0D0D),
    darkBgBot       = Color(0xFF020202),
    darkCardTop     = Color(0xFF30302F),
    darkCardBot     = Color(0xFF121212),
    darkTextOnCard  = Color(0xFFE7E7E6),
    darkTextOnBg    = Color(0xFFFFFFFF),
    lightBgTop      = Color(0xFFE5E4E4),
    lightBgBot      = Color(0xFFC4C5C4),
    lightCardTop    = Color(0xFF1A1A19),
    lightCardBot    = Color(0xFF040403),
    lightTextOnCard = Color(0xFFDEDCDD),
    lightTextOnBg   = Color(0xFF040403),
)

val LocalAppPalette = compositionLocalOf { yellowPalette }

fun AccentColor.toPalette(): AppPalette = when (this) {
    AccentColor.YELLOW -> yellowPalette
    AccentColor.GREEN  -> bluePalette
    AccentColor.BLUE   -> purplePalette
    AccentColor.PURPLE -> greenPalette
    AccentColor.RED    -> redPalette
    AccentColor.AMBER  -> amberPalette
    AccentColor.CYAN   -> cyanPalette
    AccentColor.ROSE   -> rosePalette
    AccentColor.SLATE  -> slatePalette
}

fun buildDarkColorScheme(accent: Color): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = Color.Black,
    primaryContainer = accent.copy(alpha = 0.25f),
    onPrimaryContainer = accent,
    secondary = accent.copy(alpha = 0.7f),
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color.White.copy(alpha = 0.6f),
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    outline = Color.White.copy(alpha = 0.2f),
    outlineVariant = Color.White.copy(alpha = 0.12f),
    error = Color(0xFFCF6679),
    onError = Color.Black,
    scrim = Color.Black.copy(alpha = 0.6f),
)

fun buildLightColorScheme(accent: Color): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.15f),
    onPrimaryContainer = accent,
    secondary = accent.copy(alpha = 0.7f),
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF1A1A1A),
    surface = LightSurface,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF5A5A5A),
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    outline = Color.Black.copy(alpha = 0.2f),
    outlineVariant = Color.Black.copy(alpha = 0.12f),
    error = Color(0xFFB00020),
    onError = Color.White,
    scrim = Color.Black.copy(alpha = 0.4f),
)

@Composable
fun WASensaiTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    accentColor: AccentColor = AccentColor.YELLOW,
    content: @Composable () -> Unit
) {
    val palette = accentColor.toPalette()
    val colorScheme = when (themeMode) {
        ThemeMode.DARK -> buildDarkColorScheme(accentColor.color)
        ThemeMode.LIGHT -> buildLightColorScheme(accentColor.color)
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) buildDarkColorScheme(accentColor.color) else buildLightColorScheme(accentColor.color)
    }

    val view = LocalView.current
    val isDark = themeMode != ThemeMode.LIGHT
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
