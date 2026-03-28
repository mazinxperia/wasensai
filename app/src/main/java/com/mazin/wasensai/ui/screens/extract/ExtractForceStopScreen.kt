package com.mazin.wasensai.ui.screens.extract

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val SlideEasing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
private val CardShape = RoundedCornerShape(28.dp)

@Composable
fun ExtractForceStopContent(onNext: () -> Unit) {
    val isDark = extractIsDark()
    val cardGradient = extractCardGradient(isDark)
    val textOnCard = extractTextOnCard(isDark)
    val mutedOnCard = extractMutedOnCard(isDark)
    val textOnBg = extractTextOnBg(isDark)
    val numBadgeGradient = extractCardGradient(isDark)
    val numColor = extractAccent(isDark)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(30); visible = true }
    val ox by animateFloatAsState(targetValue = if (visible) 0f else 120f, animationSpec = tween(400, easing = SlideEasing), label = "ox")

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.2f))

        Box(
            modifier = Modifier.fillMaxWidth()
                .graphicsLayer { translationX = ox; shadowElevation = if (isDark) 40f else 20f; shape = CardShape; clip = true }
                .background(cardGradient, CardShape)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Warning, null, tint = ExtractColors.SoftRed, modifier = Modifier.size(52.dp))
                Spacer(Modifier.height(12.dp))
                Text("FORCE STOP WHATSAPP", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = textOnCard, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Close WhatsApp completely to prevent database corruption during extraction.", fontFamily = ExtractFonts.SpaceGrotesk, fontSize = 13.sp, color = mutedOnCard, textAlign = TextAlign.Center, lineHeight = 18.sp)
            }
        }

        Spacer(Modifier.weight(0.15f))

        val steps = listOf("Open phone Settings", "Go to Apps", "Find WhatsApp", "Tap Force Stop", "Come back and tap Next")
        Column(
            modifier = Modifier.fillMaxWidth().graphicsLayer { translationX = ox },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Box(
                        modifier = Modifier.size(28.dp)
                            .graphicsLayer { shadowElevation = if (isDark) 16f else 8f; shape = androidx.compose.foundation.shape.CircleShape; clip = true }
                            .background(numBadgeGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = numColor)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(step.uppercase(), fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textOnBg)
                }
            }
        }

        Spacer(Modifier.weight(0.4f))
        Box(modifier = Modifier.graphicsLayer { translationX = ox }.fillMaxWidth()) {
            SlabButton(text = "I\u2019VE FORCE STOPPED WHATSAPP", onClick = onNext)
        }
        Spacer(Modifier.height(16.dp))
    }
}
