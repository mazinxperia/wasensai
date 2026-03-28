package com.mazin.wasensai.ui.screens.home

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
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
import kotlinx.coroutines.delay

private val FFPath = FontFamily(Font(R.font.led_dot_matrix, FontWeight.Normal))
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

@OptIn(ExperimentalTextApi::class)
@Composable
fun HomeScreen(
    onExtractClick: () -> Unit = {},
    onViewClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val palette    = LocalAppPalette.current
    val cardTop    = if (isDark) palette.darkCardTop    else palette.lightCardTop
    val cardBot    = if (isDark) palette.darkCardBot    else palette.lightCardBot
    val textOnCard = if (isDark) palette.darkTextOnCard else palette.lightTextOnCard
    val textOnBg   = if (isDark) palette.darkTextOnBg   else palette.lightTextOnBg
    val gearBgTop  = cardTop
    val gearBgBot  = cardBot
    val gearIcon   = textOnCard
    val iconOnCard = textOnCard

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(30); visible = true }
    val ox = slideOffset(visible)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .graphicsLayer { translationX = ox },
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo + SENSAI
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.accent_logo),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(90.dp)
                    )
                    val accent by animateColorAsState(
                        targetValue = palette.primary,
                        animationSpec = tween(400),
                        label = "accent"
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.accent_tint),
                        contentDescription = "WA Sensai",
                        tint = accent,
                        modifier = Modifier.size(90.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "SENSAI",
                    fontFamily = FFPath,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 3.sp,
                    color = textOnBg
                )
            }

            Spacer(Modifier.height(4.dp))

            // Extract card
            SlabCard(
                label = "COPY FROM DEVICE",
                title = "EXTRACT\nDATA",
                cardTop = cardTop,
                cardBot = cardBot,
                textColor = textOnCard,
                iconColor = iconOnCard,
                modifier = Modifier.fillMaxWidth(),
                onClick = onExtractClick
            )

            // View card
            SlabCard(
                label = "OPEN .WAVIEW FILE",
                title = "VIEW\nARCHIVE",
                cardTop = cardTop,
                cardBot = cardBot,
                textColor = textOnCard,
                iconColor = iconOnCard,
                modifier = Modifier.fillMaxWidth(),
                onClick = onViewClick
            )

            Spacer(Modifier.height(4.dp))

            // Settings gear
            var settingsPressed by remember { mutableStateOf(false) }
            val settingsScale by animateFloatAsState(
                if (settingsPressed) 0.90f else 1f, spring(0.6f, 400f), label = "ss"
            )
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = settingsScale
                        scaleY = settingsScale
                        shadowElevation = 50f
                        shape = CircleShape
                        clip = true
                    }
                    .background(Brush.linearGradient(listOf(gearBgTop, gearBgBot)))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSettingsClick() }
                    .pointerInput(Unit) {
                        awaitEachGesture { awaitFirstDown(); settingsPressed = true; waitForUpOrCancellation(); settingsPressed = false }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Settings, null, tint = gearIcon, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun SlabCard(
    label: String,
    title: String,
    cardTop: Color,
    cardBot: Color,
    textColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(0.6f, 400f), label = "sc")
    val cardShape = RoundedCornerShape(40.dp)

    Box(
        modifier = modifier
            .height(200.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                shadowElevation = 60f
                shape = cardShape
                clip = true
            }
            .background(
                brush = Brush.linearGradient(listOf(cardTop, cardBot)),
                shape = cardShape
            )
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .pointerInput(Unit) {
                awaitEachGesture { awaitFirstDown(); pressed = true; waitForUpOrCancellation(); pressed = false }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    label,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SpaceGrotesk,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = textColor.copy(alpha = 0.9f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SpaceGrotesk,
                    fontSize = 44.sp,
                    lineHeight = 46.sp,
                    letterSpacing = (-1).sp,
                    color = textColor
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(48.dp).align(Alignment.End)
            )
        }
    }
}
