package com.mazin.wasensai.ui.screens.extract

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazin.wasensai.ui.theme.LocalAppPalette
import com.mazin.wasensai.viewmodel.ExtractViewModel
import kotlinx.coroutines.delay

private val SlideEasing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
private val CardShape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)

@Composable
fun ExtractRootContent(viewModel: ExtractViewModel, onNext: () -> Unit) {
    val rootGranted by viewModel.rootGranted.collectAsStateWithLifecycle()
    val isDark = extractIsDark()
    val cardGradient = extractCardGradient(isDark)
    val textOnCard = extractTextOnCard(isDark)
    val mutedOnCard = extractMutedOnCard(isDark)
    val textOnBg = extractTextOnBg(isDark)
    val accent = extractAccent(isDark)
    val palette = LocalAppPalette.current
    val separatorColor = (if (isDark) palette.darkTextOnCard else palette.lightTextOnCard).copy(alpha = 0.12f)
    val monoColor = (if (isDark) palette.darkTextOnCard else palette.lightTextOnCard).copy(alpha = 0.5f)
    val iconTint = when (rootGranted) { true -> ExtractColors.SoftGreen; false -> ExtractColors.SoftRed; null -> textOnBg.copy(alpha = 0.5f) }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(30); visible = true }
    val ox by animateFloatAsState(targetValue = if (visible) 0f else 120f, animationSpec = tween(400, easing = SlideEasing), label = "ox")
    val iconScale by animateFloatAsState(targetValue = if (rootGranted == true) 1.1f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "is")

    Column(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = ox }, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))

        Icon(
            imageVector = when (rootGranted) { true -> Icons.Rounded.CheckCircle; false -> Icons.Rounded.Error; null -> Icons.Rounded.Security },
            contentDescription = null, tint = iconTint,
            modifier = Modifier.size(64.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }
        )

        Spacer(Modifier.height(16.dp))
        Text("ROOT ACCESS REQUIRED", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.5).sp, color = textOnBg, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            when (rootGranted) {
                true -> "Root access granted! Database is accessible."
                false -> "Root access denied. Ensure your device is rooted and try again."
                null -> "Sensai needs deep-level system permissions to extract the WhatsApp database."
            },
            fontFamily = ExtractFonts.SpaceGrotesk, fontSize = 14.sp, color = textOnBg.copy(alpha = 0.65f), textAlign = TextAlign.Center, lineHeight = 20.sp
        )

        Spacer(Modifier.height(18.dp))

        Box(
            modifier = Modifier.fillMaxWidth()
                .graphicsLayer { shadowElevation = if (isDark) 40f else 20f; shape = CardShape; clip = true }
                .background(cardGradient, CardShape)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when (rootGranted) {
                            true -> "Superuser permissions verified. Ready to proceed."
                            false -> "Superuser binaries detected but permission denied. Grant access via your root manager."
                            null -> "Root access copies WhatsApp databases from protected system storage."
                        },
                        fontFamily = ExtractFonts.SpaceGrotesk, fontSize = 12.sp, color = mutedOnCard, lineHeight = 18.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                Spacer(Modifier.height(10.dp))
                Text("SECURITY STATUS", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 2.sp, color = accent)
                Spacer(Modifier.height(2.dp))
                Text(
                    when (rootGranted) { true -> "STATUS: ROOT_GRANTED"; false -> "CODE: E_ROOT_DENIED"; null -> "STATUS: AWAITING_PERMISSION" },
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = monoColor
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (rootGranted == true) {
            Text("\u2713 ROOT ACCESS GRANTED", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp, color = ExtractColors.SoftGreen)
            Spacer(Modifier.height(12.dp))
            SlabButton(text = "CONTINUE", fontSize = 15, onClick = onNext)
        } else {
            SlabButton(text = if (rootGranted == false) "RETRY ROOT ACCESS" else "GRANT ROOT ACCESS", fontSize = 15, onClick = { viewModel.requestRootAccess() })
        }
        Spacer(Modifier.height(16.dp))
    }
}
