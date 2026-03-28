package com.mazin.wasensai.ui.screens.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.delay

private val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
    Font(R.font.space_grotesk_bold, FontWeight.Black),
    Font(R.font.space_grotesk_bold, FontWeight.ExtraBold)
)

private val SlideEasing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
private const val SLIDE_DURATION = 400
private const val SLIDE_OFFSET = 120f

@Composable
private fun slideOffset(visible: Boolean): Float {
    val ox by animateFloatAsState(
        targetValue = if (visible) 0f else SLIDE_OFFSET,
        animationSpec = tween(durationMillis = SLIDE_DURATION, easing = SlideEasing),
        label = "ox"
    )
    return ox
}

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onAboutClick: () -> Unit,
    onAccentColorClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val palette          = LocalAppPalette.current
    val cardTop          = if (isDark) palette.darkCardTop    else palette.lightCardTop
    val cardBot          = if (isDark) palette.darkCardBot    else palette.lightCardBot
    val textOnBg         = if (isDark) palette.darkTextOnBg   else palette.lightTextOnBg
    val textOnCard       = if (isDark) palette.darkTextOnCard else palette.lightTextOnCard
    val accent           = palette.primary
    val btnTop           = cardTop
    val btnBot           = cardBot
    val btnIcon          = textOnCard
    val labelColor       = textOnBg.copy(alpha = 0.5f)
    val separatorColor   = textOnCard.copy(alpha = 0.15f)
    val mutedOnCard      = textOnCard.copy(alpha = 0.75f)
    val toggleBg         = textOnCard.copy(alpha = 0.15f)
    val chipUnselectedTop  = cardTop
    val chipUnselectedBot  = cardBot
    val chipUnselectedText = textOnCard.copy(alpha = 0.5f)
    val chipSelectedBg     = if (isDark) palette.darkBgTop else accent
    val chipSelectedText   = if (isDark) accent else palette.lightTextOnBg
    val arrowCircleBg    = textOnCard.copy(alpha = 0.1f)
    val arrowIcon        = textOnCard

    val cardShape = RoundedCornerShape(40.dp)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(30); visible = true }
    val ox = slideOffset(visible)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .graphicsLayer { translationX = ox }
        ) {
            Spacer(Modifier.height(16.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var backPressed by remember { mutableStateOf(false) }
                val backScale by animateFloatAsState(if (backPressed) 0.90f else 1f, spring(0.6f, 400f), label = "bs")
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = backScale
                            scaleY = backScale
                            shadowElevation = if (isDark) 40f else 16f
                            shape = CircleShape
                            clip = true
                        }
                        .background(Brush.linearGradient(listOf(btnTop, btnBot)))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onBackClick() }
                        .pointerInput(Unit) {
                            awaitEachGesture { awaitFirstDown(); backPressed = true; waitForUpOrCancellation(); backPressed = false }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = btnIcon, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("SETTINGS", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 1.sp, color = textOnBg)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.height(32.dp))

            // APPEARANCE label
            Text(
                "APPEARANCE",
                fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
                fontSize = 11.sp, letterSpacing = 2.sp, color = labelColor,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
            )

            // Appearance card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        shadowElevation = if (isDark) 40f else 24f
                        shape = cardShape
                        clip = true
                    }
                    .background(brush = Brush.linearGradient(listOf(cardTop, cardBot)), shape = cardShape)
            ) {
                Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 28.dp)) {

                    // Theme section
                    Text("Theme", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textOnCard)
                    Spacer(Modifier.height(4.dp))
                    Text("Choose your preferred visual style", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = mutedOnCard)
                    Spacer(Modifier.height(16.dp))

                    // Theme toggle chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50.dp))
                            .background(toggleBg)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("DARK" to ThemeMode.DARK, "LIGHT" to ThemeMode.LIGHT, "SYSTEM" to ThemeMode.SYSTEM).forEach { (label, mode) ->
                            val isSelected = themeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(50.dp))
                                    .then(
                                        if (isSelected) Modifier.background(chipSelectedBg)
                                        else Modifier.background(Brush.linearGradient(listOf(chipUnselectedTop, chipUnselectedBot)))
                                    )
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { viewModel.setThemeMode(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp,
                                    color = if (isSelected) chipSelectedText else chipUnselectedText
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                    Spacer(Modifier.height(24.dp))

                    // Accent Color row — tappable, navigates to picker screen
                    var accentPressed by remember { mutableStateOf(false) }
                    val accentScale by animateFloatAsState(if (accentPressed) 0.97f else 1f, spring(0.6f, 400f), label = "acs")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { scaleX = accentScale; scaleY = accentScale }
                            .clip(RoundedCornerShape(20.dp))
                            .background(textOnCard.copy(alpha = 0.06f))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onAccentColorClick() }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(); accentPressed = true
                                    waitForUpOrCancellation(); accentPressed = false
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Current accent color dot
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .graphicsLayer {
                                    shadowElevation = 12f
                                    shape = CircleShape
                                    clip = true
                                }
                                .background(accentColor.color, CircleShape)
                        )

                        // Labels
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Accent Color",
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = textOnCard
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                accentColor.label,
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                color = mutedOnCard
                            )
                        }

                        // Arrow
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(arrowCircleBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                null,
                                tint = arrowIcon,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // ABOUT label
            Text(
                "ABOUT",
                fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
                fontSize = 11.sp, letterSpacing = 2.sp, color = labelColor,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
            )

            // About card
            var aboutPressed by remember { mutableStateOf(false) }
            val aboutScale by animateFloatAsState(if (aboutPressed) 0.98f else 1f, spring(0.6f, 400f), label = "as")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = aboutScale
                        scaleY = aboutScale
                        shadowElevation = if (isDark) 40f else 24f
                        shape = cardShape
                        clip = true
                    }
                    .background(brush = Brush.linearGradient(listOf(cardTop, cardBot)), shape = cardShape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onAboutClick() }
                    .pointerInput(Unit) {
                        awaitEachGesture { awaitFirstDown(); aboutPressed = true; waitForUpOrCancellation(); aboutPressed = false }
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("About WA Sensai", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textOnCard)
                        Spacer(Modifier.height(2.dp))
                        Text("Version 1.0.0 · Developer info", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = mutedOnCard)
                    }
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(arrowCircleBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = arrowIcon, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}
