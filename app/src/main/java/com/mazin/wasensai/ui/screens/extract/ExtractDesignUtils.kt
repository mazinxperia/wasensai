package com.mazin.wasensai.ui.screens.extract

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazin.wasensai.R
import com.mazin.wasensai.ui.theme.LocalAppPalette
import com.mazin.wasensai.ui.theme.ThemeMode
import com.mazin.wasensai.viewmodel.HomeViewModel

// Static colors that don't change with theme
object ExtractColors {
    val SoftRed = Color(0xFFE57373)
    val SoftGreen = Color(0xFF81C784)
    val ProgressGreen = Color(0xFF046307)
    val ProgressTrack = Color(0xFF1A1A19)
}

object ExtractFonts {
    val SpaceGrotesk = FontFamily(
        Font(R.font.space_grotesk_regular, FontWeight.Normal),
        Font(R.font.space_grotesk_bold, FontWeight.Bold),
        Font(R.font.space_grotesk_bold, FontWeight.Black),
        Font(R.font.space_grotesk_bold, FontWeight.ExtraBold)
    )
}

// Composable to get current theme state in extract screens
@Composable
fun extractIsDark(): Boolean {
    val viewModel: HomeViewModel = hiltViewModel()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
}

// Theme-aware card gradient
@Composable
fun extractCardGradient(isDark: Boolean): Brush {
    val palette = LocalAppPalette.current
    return if (isDark) Brush.linearGradient(listOf(palette.darkCardTop, palette.darkCardBot))
           else Brush.linearGradient(listOf(palette.lightCardTop, palette.lightCardBot))
}

// Theme-aware text color on card
@Composable
fun extractTextOnCard(isDark: Boolean): Color {
    val palette = LocalAppPalette.current
    return if (isDark) palette.darkTextOnCard else palette.lightTextOnCard
}

// Theme-aware muted text on card
@Composable
fun extractMutedOnCard(isDark: Boolean): Color {
    val palette = LocalAppPalette.current
    return if (isDark) palette.darkTextOnCard.copy(alpha = 0.6f) else palette.lightTextOnCard.copy(alpha = 0.55f)
}

// Theme-aware text on background
@Composable
fun extractTextOnBg(isDark: Boolean): Color {
    val palette = LocalAppPalette.current
    return if (isDark) palette.darkTextOnBg else palette.lightTextOnBg
}

// Theme-aware accent
@Composable
fun extractAccent(isDark: Boolean): Color {
    val palette = LocalAppPalette.current
    return palette.primary
}

// Keep slabShadow as no-op for legacy
fun Modifier.slabShadow(
    cornerRadius: Float = 80f, blur: Float = 30f,
    offsetX: Float = 14f, offsetY: Float = 18f, alpha: Int = 230
): Modifier = this

fun Modifier.slabBg(): Modifier = this

private val ButtonShape = RoundedCornerShape(50.dp)

@OptIn(ExperimentalTextApi::class)
@Composable
fun SlabButton(text: String, fontSize: Int = 13, onClick: () -> Unit) {
    val isDark = extractIsDark()
    val palette = LocalAppPalette.current
    val btnTop = if (isDark) palette.darkCardTop else palette.lightCardTop
    val btnBot = if (isDark) palette.darkCardBot else palette.lightCardBot
    val textColor = if (isDark) palette.darkTextOnCard else palette.lightTextOnCard
    val textBrush = Brush.verticalGradient(listOf(textColor, textColor))
    val shadowElev = if (isDark) 40f else 20f

    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(0.6f, 400f), label = "btn")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                shadowElevation = shadowElev
                shape = ButtonShape; clip = true
            }
            .background(Brush.linearGradient(listOf(btnTop, btnBot)), ButtonShape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .pointerInput(Unit) {
                awaitEachGesture { awaitFirstDown(); pressed = true; waitForUpOrCancellation(); pressed = false }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold,
            fontSize = fontSize.sp, letterSpacing = 1.sp,
            style = TextStyle(brush = textBrush)
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun SlabButtonWithIcon(
    text: String, fontSize: Int = 12, secondary: Boolean = false,
    icon: @Composable () -> Unit, onClick: () -> Unit
) {
    val isDark = extractIsDark()
    val palette = LocalAppPalette.current
    val btnTop = if (isDark) palette.darkCardTop else palette.lightCardTop
    val btnBot = if (isDark) palette.darkCardBot else palette.lightCardBot
    val textColor = if (isDark) palette.darkTextOnCard else palette.lightTextOnCard
    val textBrush = Brush.verticalGradient(listOf(textColor, textColor))
    val shadowElev = if (isDark) 40f else if (secondary) 14f else 20f
    val height = if (secondary) 50.dp else 56.dp

    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(0.6f, 400f), label = "btn2")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                shadowElevation = shadowElev
                shape = ButtonShape; clip = true
            }
            .background(Brush.linearGradient(listOf(btnTop, btnBot)), ButtonShape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .pointerInput(Unit) {
                awaitEachGesture { awaitFirstDown(); pressed = true; waitForUpOrCancellation(); pressed = false }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(
                text, fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold,
                fontSize = fontSize.sp, letterSpacing = 2.sp,
                style = TextStyle(brush = textBrush)
            )
        }
    }
}
