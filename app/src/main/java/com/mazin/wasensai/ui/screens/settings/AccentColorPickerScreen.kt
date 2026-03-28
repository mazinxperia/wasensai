package com.mazin.wasensai.ui.screens.settings

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazin.wasensai.R
import com.mazin.wasensai.ui.theme.AccentColor
import com.mazin.wasensai.ui.theme.ThemeMode
import com.mazin.wasensai.ui.theme.toPalette
import com.mazin.wasensai.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import kotlin.math.*

private val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
    Font(R.font.space_grotesk_bold, FontWeight.ExtraBold)
)
private val FFPath = FontFamily(Font(R.font.led_dot_matrix, FontWeight.Normal))
private val accentList = AccentColor.entries.toList()
private const val TOTAL = 9

@Composable
fun AccentColorPickerScreen(
    onBackClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val savedAccent by viewModel.accentColor.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    var selectedIndex by remember { mutableIntStateOf(accentList.indexOf(savedAccent).coerceAtLeast(0)) }
    val stepAngle = 360f / TOTAL
    var rawAngle by remember { mutableFloatStateOf(selectedIndex * stepAngle) }

    // Snaps to nearest step with spring
    val animatedKnobAngle by animateFloatAsState(
        targetValue = selectedIndex * stepAngle,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 300f),
        label = "knob"
    )

    val currentAccent = accentList[selectedIndex]
    val currentPalette = currentAccent.toPalette()

    val bgTop      by animateColorAsState(if (isDark) currentPalette.darkBgTop   else currentPalette.lightBgTop,   tween(500), label = "bt")
    val bgBot      by animateColorAsState(if (isDark) currentPalette.darkBgBot   else currentPalette.lightBgBot,   tween(500), label = "bb")
    val cardTop    by animateColorAsState(if (isDark) currentPalette.darkCardTop  else currentPalette.lightCardTop, tween(500), label = "ct")
    val cardBot    by animateColorAsState(if (isDark) currentPalette.darkCardBot  else currentPalette.lightCardBot, tween(500), label = "cb")
    val textOnCard by animateColorAsState(if (isDark) currentPalette.darkTextOnCard else currentPalette.lightTextOnCard, tween(500), label = "tc")
    val textOnBg   by animateColorAsState(if (isDark) currentPalette.darkTextOnBg   else currentPalette.lightTextOnBg,   tween(500), label = "tb")
    val primary    by animateColorAsState(currentPalette.primary, tween(500), label = "pr")

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(30); visible = true }
    val entryY by animateFloatAsState(if (visible) 0f else 80f, tween(400, easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)), label = "ey")
    val entryA by animateFloatAsState(if (visible) 1f else 0f, tween(400), label = "ea")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBot)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = entryY; alpha = entryA },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        .background(Brush.linearGradient(listOf(cardTop, cardBot)))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onBackClick() }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(); backPressed = true
                                waitForUpOrCancellation(); backPressed = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = textOnCard, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("ACCENT COLOR", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.sp, color = textOnBg)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.weight(0.6f))

            // Knob + color dots ring
            KnobWithColorRing(
                selectedIndex = selectedIndex,
                knobAngle = animatedKnobAngle,
                cardTop = cardTop,
                cardBot = cardBot,
                textOnCard = textOnCard,
                primary = primary,
                onDrag = { deltaX ->
                    rawAngle += deltaX * 0.5f
                    val normalised = ((rawAngle % 360f) + 360f) % 360f
                    val nearest = ((normalised / stepAngle).roundToInt() % TOTAL + TOTAL) % TOTAL
                    if (nearest != selectedIndex) selectedIndex = nearest
                },
                onDotClick = { index ->
                    selectedIndex = index
                    rawAngle = index * stepAngle
                }
            )

            Spacer(Modifier.height(28.dp))

            // Color name
            Text(
                currentAccent.label.uppercase(),
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 2.sp,
                color = textOnBg
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${selectedIndex + 1} / $TOTAL",
                fontFamily = FFPath,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                color = textOnBg.copy(alpha = 0.4f)
            )

            Spacer(Modifier.weight(0.6f))

            // Preview card
            PreviewCard(cardTop = cardTop, cardBot = cardBot, textOnCard = textOnCard, primary = primary, isDark = isDark)

            Spacer(Modifier.weight(0.6f))

            // Apply button
            var confirmPressed by remember { mutableStateOf(false) }
            val confirmScale by animateFloatAsState(if (confirmPressed) 0.97f else 1f, spring(0.6f, 400f), label = "cs")
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .graphicsLayer {
                        scaleX = confirmScale; scaleY = confirmScale
                        shadowElevation = 40f
                        shape = RoundedCornerShape(50.dp)
                        clip = true
                    }
                    .background(Brush.linearGradient(listOf(cardTop, cardBot)), RoundedCornerShape(50.dp))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        viewModel.setAccentColor(currentAccent)
                        onBackClick()
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(); confirmPressed = true
                            waitForUpOrCancellation(); confirmPressed = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("APPLY", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 3.sp, color = textOnCard)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun KnobWithColorRing(
    selectedIndex: Int,
    knobAngle: Float,
    cardTop: Color,
    cardBot: Color,
    textOnCard: Color,
    primary: Color,
    onDrag: (Float) -> Unit,
    onDotClick: (Int) -> Unit
) {
    val ringDiameter = 290.dp
    val knobDiameter = 180.dp
    val dotSize = 24.dp
    val stepAngle = 360f / TOTAL

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringDiameter)) {

        // Colored dot ring
        ColorDotRing(
            selectedIndex = selectedIndex,
            ringDiameter = ringDiameter,
            dotSize = dotSize,
            stepAngle = stepAngle,
            onDotClick = onDotClick
        )

        // Draggable knob
        Box(
            modifier = Modifier
                .size(knobDiameter)
                .graphicsLayer {
                    rotationZ = knobAngle
                    shadowElevation = 80f
                    shape = CircleShape
                    clip = true
                }
                .background(Brush.radialGradient(listOf(cardTop, cardBot)))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        do {
                            val event = awaitPointerEvent()
                            val drag = event.changes.firstOrNull()
                            if (drag != null && drag.pressed) {
                                onDrag(drag.positionChange().x)
                                drag.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Indicator notch
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(30.dp)
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .background(primary, RoundedCornerShape(2.dp))
            )

            // Center accent circle (counter-rotates to stay upright)
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        rotationZ = -knobAngle
                        shadowElevation = 24f
                        shape = CircleShape
                        clip = true
                    }
                    .background(primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .background(cardBot, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (selectedIndex + 1).toString().padStart(2, '0'),
                        fontFamily = FFPath,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorDotRing(
    selectedIndex: Int,
    ringDiameter: Dp,
    dotSize: Dp,
    stepAngle: Float,
    onDotClick: (Int) -> Unit
) {
    Layout(
        content = {
            accentList.forEachIndexed { index, ac ->
                val isSelected = index == selectedIndex
                val dotScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.5f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
                    label = "ds$index"
                )
                val ringAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = tween(250),
                    label = "ra$index"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(dotSize * 1.8f) // hit area
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            onDotClick(index)
                        }
                ) {
                    // Selection ring
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(dotSize + 6.dp)
                                .graphicsLayer { alpha = ringAlpha }
                                .background(Color.Transparent, CircleShape)
                                // White/light ring around selected dot
                                .clip(CircleShape)
                        )
                    }
                    // The dot itself
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .graphicsLayer {
                                scaleX = dotScale
                                scaleY = dotScale
                                shadowElevation = if (isSelected) 24f else 8f
                                shape = CircleShape
                                clip = true
                            }
                            .background(ac.color, CircleShape)
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val itemSize = (dotSize * 1.8f).roundToPx()
        val placeables = measurables.map {
            it.measure(constraints.copy(minWidth = itemSize, maxWidth = itemSize, minHeight = itemSize, maxHeight = itemSize))
        }
        val totalSize = ringDiameter.roundToPx()
        val radius = totalSize / 2f - itemSize / 2f

        layout(totalSize, totalSize) {
            placeables.forEachIndexed { i, placeable ->
                val angleDeg = i * stepAngle - 90f
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val cx = totalSize / 2f + radius * cos(angleRad).toFloat()
                val cy = totalSize / 2f + radius * sin(angleRad).toFloat()
                placeable.placeRelative(
                    x = (cx - itemSize / 2f).roundToInt(),
                    y = (cy - itemSize / 2f).roundToInt()
                )
            }
        }
    }
}

@Composable
private fun PreviewCard(
    cardTop: Color,
    cardBot: Color,
    textOnCard: Color,
    primary: Color,
    isDark: Boolean
) {
    val cardShape = RoundedCornerShape(32.dp)
    Box(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = if (isDark) 40f else 24f
                shape = cardShape
                clip = true
            }
            .background(Brush.linearGradient(listOf(cardTop, cardBot)), cardShape)
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Column {
            Text("PREVIEW", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 3.sp, color = textOnCard.copy(alpha = 0.5f))
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.weight(1f).height(80.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(primary.copy(alpha = if (isDark) 0.2f else 0.12f))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                        Text("COPY FROM DEVICE", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 1.sp, color = primary.copy(alpha = 0.7f))
                        Text("EXTRACT\nDATA", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp, color = primary)
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).height(80.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(primary.copy(alpha = if (isDark) 0.2f else 0.12f))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                        Text("OPEN .WAVIEW FILE", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 1.sp, color = primary.copy(alpha = 0.7f))
                        Text("VIEW\nARCHIVE", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp, color = primary)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(20.dp).background(primary, CircleShape))
                Box(modifier = Modifier.size(14.dp).background(primary.copy(alpha = 0.55f), CircleShape))
                Box(modifier = Modifier.size(10.dp).background(primary.copy(alpha = 0.25f), CircleShape))
                Spacer(Modifier.weight(1f))
                Text("PRIMARY · MUTED · SUBTLE", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.sp, color = textOnCard.copy(alpha = 0.35f))
            }
        }
    }
}
