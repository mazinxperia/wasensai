package com.mazin.wasensai.ui.screens.about

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
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
    val textOnBg   = if (isDark) palette.darkTextOnBg   else palette.lightTextOnBg
    val textOnCard = if (isDark) palette.darkTextOnCard else palette.lightTextOnCard
    val mutedOnCard = textOnCard.copy(alpha = 0.6f)
    val bodyOnCard = textOnCard.copy(alpha = 0.75f)
    val separatorColor = textOnCard.copy(alpha = 0.12f)
    val btnTop = cardTop
    val btnBot = cardBot
    val btnIcon = textOnCard
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
                .padding(horizontal = 24.dp)
                .graphicsLayer { translationX = ox },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                var backPressed by remember { mutableStateOf(false) }
                val backScale by animateFloatAsState(if (backPressed) 0.90f else 1f, spring(0.6f, 400f), label = "bs")
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = backScale; scaleY = backScale
                            shadowElevation = if (isDark) 40f else 16f
                            shape = CircleShape; clip = true
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
            }

            Spacer(Modifier.weight(0.3f))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painter = painterResource(id = R.drawable.wa_sensai_logo_3d), contentDescription = "WA Sensai", modifier = Modifier.size(90.dp))
                Spacer(Modifier.height(8.dp))
                Text("SENSAI", fontFamily = FFPath, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 3.sp, color = textOnBg)
            }

            Spacer(Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        shadowElevation = if (isDark) 40f else 24f
                        shape = cardShape; clip = true
                    }
                    .background(brush = Brush.linearGradient(listOf(cardTop, cardBot)), shape = cardShape)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("VERSION 1.0.0", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 3.sp, color = textOnCard)
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                    Spacer(Modifier.height(20.dp))
                    Text("DEVELOPED BY", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = 3.sp, color = mutedOnCard)
                    Spacer(Modifier.height(6.dp))
                    Text("Mazin Ruknuddin", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = textOnCard)
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "WA Sensai lets you extract, archive, and beautifully view your WhatsApp data. Fully offline, no data leaves your device.",
                        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal, fontSize = 14.sp,
                        color = bodyOnCard, textAlign = TextAlign.Center, lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                    Spacer(Modifier.height(16.dp))
                    Text("MIN SDK: ANDROID 8.0 (API 26)", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = 2.sp, color = mutedOnCard)
                    Spacer(Modifier.height(4.dp))
                    Text("REQUIRES ROOT ACCESS FOR EXTRACTION", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = 2.sp, color = mutedOnCard)
                }
            }

            Spacer(Modifier.weight(0.5f))
        }
    }
}
