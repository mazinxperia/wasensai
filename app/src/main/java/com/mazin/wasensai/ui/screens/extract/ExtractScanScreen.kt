package com.mazin.wasensai.ui.screens.extract

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazin.wasensai.ui.theme.LocalAppPalette
import com.mazin.wasensai.utils.FileUtils
import com.mazin.wasensai.viewmodel.ExtractViewModel
import kotlinx.coroutines.delay

private val SlideEasing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
private val CardShape = RoundedCornerShape(32.dp)

@OptIn(ExperimentalTextApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExtractScanContent(viewModel: ExtractViewModel, onNext: () -> Unit) {
    val scanResult by viewModel.scanResult.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    var showInfoSheet by remember { mutableStateOf<String?>(null) }

    val isDark = extractIsDark()
    val cardGradient = extractCardGradient(isDark)
    val textOnCard = extractTextOnCard(isDark)
    val textOnBg = extractTextOnBg(isDark)
    val accent = extractAccent(isDark)
    val palette = LocalAppPalette.current
    val separatorColor = (if (isDark) palette.darkTextOnCard else palette.lightTextOnCard).copy(alpha = 0.12f)
    val subValueColor = (if (isDark) palette.darkTextOnCard else palette.lightTextOnCard).copy(alpha = 0.4f)
    val valColor = if (isDark) palette.darkTextOnCard else palette.lightTextOnCard
    val valueGradient = Brush.verticalGradient(listOf(valColor, valColor))

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(30); visible = true }
    val ox by animateFloatAsState(targetValue = if (visible) 0f else 120f, animationSpec = tween(400, easing = SlideEasing), label = "ox")

    Column(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = ox }, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Text("SCAN FILES", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = textOnBg, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text("Discover what WhatsApp data is available on this device.", fontFamily = ExtractFonts.SpaceGrotesk, fontSize = 13.sp, color = textOnBg.copy(alpha = 0.55f), lineHeight = 18.sp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))

        if (isScanning) {
            Spacer(Modifier.weight(0.3f))
            val infiniteTransition = rememberInfiniteTransition(label = "scan")
            val rotation by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "rot")
            Box(
                modifier = Modifier.size(100.dp)
                    .graphicsLayer { shadowElevation = if (isDark) 40f else 20f; shape = CircleShape; clip = true }
                    .background(cardGradient),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(80.dp).graphicsLayer { rotationZ = rotation }) {
                    drawArc(color = accent, startAngle = 0f, sweepAngle = 70f, useCenter = false, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                }
                Box(Modifier.size(5.dp).clip(CircleShape).background(accent))
            }
            Spacer(Modifier.height(24.dp))
            Text("SYSTEM STATUS", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 4.sp, color = textOnBg.copy(alpha = 0.45f))
            Spacer(Modifier.height(6.dp))
            Text("SCANNING DATABASE...", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = textOnBg)
            Spacer(Modifier.weight(0.7f))
        } else if (scanResult != null) {
            val result = scanResult!!
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .graphicsLayer { shadowElevation = if (isDark) 40f else 20f; shape = CardShape; clip = true }
                    .background(cardGradient, CardShape)
            ) {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
                    ScanRow("msgstore.db", FileUtils.formatFileSize(result.msgstoreSize), null, textOnCard, separatorColor, subValueColor, valueGradient, accent) { showInfoSheet = "msgstore.db contains all your messages, chats, and multimedia references." }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                    ScanRow("wa.db", FileUtils.formatFileSize(result.waDbSize), null, textOnCard, separatorColor, subValueColor, valueGradient, accent) { showInfoSheet = "wa.db contains your WhatsApp contact names and phone numbers." }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                    ScanRow("Avatars", "${result.avatarCount} FILES", FileUtils.formatFileSize(result.avatarSize), textOnCard, separatorColor, subValueColor, valueGradient, accent) { showInfoSheet = "Profile photos of your WhatsApp contacts stored on your device." }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                    ScanRow("Media", "${result.mediaCount} FILES", FileUtils.formatFileSize(result.mediaSize), textOnCard, separatorColor, subValueColor, valueGradient, accent) { showInfoSheet = "All media files received and sent via WhatsApp." }
                    if (result.imagesCount > 0 || result.videosCount > 0 || result.audioCount > 0 || result.docsCount > 0 || result.othersCount > 0) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                        ScanRow("  Images", "${result.imagesCount} FILES", "${result.imagesSizeMb} MB", textOnCard, separatorColor, subValueColor, valueGradient, accent) { showInfoSheet = "JPG, JPEG, PNG and WEBP images." }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                        ScanRow("  Videos", "${result.videosCount} FILES", "${result.videosSizeMb} MB", textOnCard, separatorColor, subValueColor, valueGradient, accent) { showInfoSheet = "MP4, MKV, 3GP and AVI video files." }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                        ScanRow("  Audio", "${result.audioCount} FILES", "${result.audioSizeMb} MB", textOnCard, separatorColor, subValueColor, valueGradient, accent) { showInfoSheet = "MP3, OPUS, M4A and other audio files." }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                        ScanRow("  Docs", "${result.docsCount} FILES", "${result.docsSizeMb} MB", textOnCard, separatorColor, subValueColor, valueGradient, accent) { showInfoSheet = "PDF, DOC, XLS, ZIP, HTML and other documents." }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                        ScanRow("  Others", "${result.othersCount} FILES", "${result.othersSizeMb} MB", textOnCard, separatorColor, subValueColor, valueGradient, accent) { showInfoSheet = "Other files: TMP, CRYPT14, CHCK, NOMEDIA, etc." }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("TOTAL", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 3.sp, color = textOnCard.copy(alpha = 0.5f), modifier = Modifier.weight(1f))
                    Text(FileUtils.formatFileSize(result.totalSize), fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Black, fontSize = 22.sp, color = textOnCard)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (scanResult == null && !isScanning) SlabButton(text = "SCAN FILES", onClick = { viewModel.scanFiles() })
        else if (scanResult != null) SlabButton(text = "CONTINUE", onClick = onNext)
        Spacer(Modifier.height(16.dp))
    }

    showInfoSheet?.let { info ->
        ModalBottomSheet(onDismissRequest = { showInfoSheet = null }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(24.dp)) { Text(info, fontSize = 14.sp, modifier = Modifier.padding(bottom = 32.dp)) }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun ScanRow(
    name: String, value: String, subValue: String? = null,
    textOnCard: androidx.compose.ui.graphics.Color,
    separatorColor: androidx.compose.ui.graphics.Color,
    subValueColor: androidx.compose.ui.graphics.Color,
    valueGradient: Brush,
    accent: androidx.compose.ui.graphics.Color,
    onInfoClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Info, null, tint = accent, modifier = Modifier.size(18.dp).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onInfoClick() })
        Spacer(Modifier.width(12.dp))
        Text(name, fontFamily = ExtractFonts.SpaceGrotesk, fontSize = 14.sp, color = textOnCard, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp, style = TextStyle(brush = valueGradient))
            if (subValue != null) Text(subValue, fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.sp, color = subValueColor)
        }
    }
}
