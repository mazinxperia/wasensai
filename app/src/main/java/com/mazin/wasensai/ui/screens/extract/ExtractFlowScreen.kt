package com.mazin.wasensai.ui.screens.extract

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import com.mazin.wasensai.viewmodel.ExtractViewModel
import com.mazin.wasensai.viewmodel.HomeViewModel

private val SlideEasing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
private val ProgressGreen = Color(0xFF046307)

private val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
    Font(R.font.space_grotesk_bold, FontWeight.Black),
    Font(R.font.space_grotesk_bold, FontWeight.ExtraBold)
)

private val stepNames = listOf("FORCE STOP", "PERMISSIONS", "SCAN", "EXPORT", "PROGRESS")

@Composable
fun ExtractFlowScreen(
    onBackToHome: () -> Unit,
    onOpenViewer: () -> Unit,
    viewModel: ExtractViewModel = hiltViewModel()
) {
    val themeViewModel: HomeViewModel = hiltViewModel()
    val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val palette = LocalAppPalette.current
    val btnTop = if (isDark) palette.darkCardTop else palette.lightCardTop
    val btnBot = if (isDark) palette.darkCardBot else palette.lightCardBot
    val btnIcon = if (isDark) palette.darkTextOnCard else palette.lightTextOnCard
    val textOnBg = if (isDark) palette.darkTextOnBg else palette.lightTextOnBg
    val progressTrack = if (isDark) Color(0xFF2A2928) else Color(0xFF1A1A19)
    val dotPassed = if (isDark) Color.White.copy(alpha = 0.6f) else palette.lightTextOnBg
    val dotFuture = if (isDark) Color.White.copy(alpha = 0.2f) else palette.lightTextOnBg.copy(alpha = 0.2f)

    var currentStep by remember { mutableIntStateOf(1) }

    val animatedProgress by animateFloatAsState(
        targetValue = currentStep.toFloat() / 5f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                var backPressed by remember { mutableStateOf(false) }
                val backScale by animateFloatAsState(if (backPressed) 0.90f else 1f, spring(0.6f, 400f), label = "bs")
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer {
                            scaleX = backScale; scaleY = backScale
                            shadowElevation = if (isDark) 40f else 16f
                            shape = CircleShape; clip = true
                        }
                        .background(Brush.linearGradient(listOf(btnTop, btnBot)))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            if (currentStep > 1) currentStep-- else onBackToHome()
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture { awaitFirstDown(); backPressed = true; waitForUpOrCancellation(); backPressed = false }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = btnIcon, modifier = Modifier.size(20.dp))
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("STEP %02d / 05".format(currentStep), fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 2.sp, color = textOnBg.copy(alpha = 0.5f))
                    Text(stepNames.getOrElse(currentStep - 1) { "" }, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textOnBg)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Progress bar
            Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50.dp)).background(progressTrack)) {
                Box(modifier = Modifier.fillMaxWidth(animatedProgress).fillMaxHeight().clip(RoundedCornerShape(50.dp)).background(ProgressGreen))
            }

            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val totalWidth = maxWidth
                for (i in 1..4) {
                    val fraction = i.toFloat() / 5f
                    val dotOffset = totalWidth * fraction - 3.dp
                    Box(
                        modifier = Modifier.offset(x = dotOffset).size(6.dp).clip(CircleShape)
                            .background(when { i < currentStep -> dotPassed; i == currentStep -> ProgressGreen; else -> dotFuture })
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(400, easing = SlideEasing)) togetherWith
                            slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(400, easing = SlideEasing))
                        } else {
                            slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(400, easing = SlideEasing)) togetherWith
                            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(400, easing = SlideEasing))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "step"
                ) { step ->
                    when (step) {
                        1 -> ExtractForceStopContent(onNext = { currentStep = 2 })
                        2 -> ExtractRootContent(viewModel = viewModel, onNext = { currentStep = 3 })
                        3 -> ExtractScanContent(viewModel = viewModel, onNext = { currentStep = 4 })
                        4 -> ExtractOptionsContent(viewModel = viewModel, onNext = { currentStep = 5 })
                        5 -> ExtractProgressContent(viewModel = viewModel, onOpenViewer = onOpenViewer)
                        else -> {}
                    }
                }
            }
        }
    }
}
