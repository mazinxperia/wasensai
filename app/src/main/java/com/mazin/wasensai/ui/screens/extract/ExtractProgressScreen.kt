package com.mazin.wasensai.ui.screens.extract

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazin.wasensai.ui.theme.LocalAppPalette
import com.mazin.wasensai.viewmodel.ExtractState
import com.mazin.wasensai.viewmodel.ExtractViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SlideEasing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
private val LogCardShape = RoundedCornerShape(24.dp)

@Composable
fun ExtractProgressContent(viewModel: ExtractViewModel, onOpenViewer: () -> Unit) {
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val exportLog by viewModel.exportLog.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isDark = extractIsDark()
    val cardGradient = extractCardGradient(isDark)
    val textOnCard = extractTextOnCard(isDark)
    val textOnBg = extractTextOnBg(isDark)
    val accent = extractAccent(isDark)
    val palette = LocalAppPalette.current
    val logDotColor = if (isDark) palette.darkTextOnCard else palette.lightTextOnCard
    val logLabelColor = (if (isDark) palette.darkTextOnCard else palette.lightTextOnCard).copy(alpha = 0.4f)
    val logTextColor = (if (isDark) palette.darkTextOnCard else palette.lightTextOnCard).copy(alpha = 0.8f)
    val progressTrack = if (isDark) Color(0xFF2A2928) else Color(0xFF1A1A19)
    val shareIconTint = if (isDark) palette.darkTextOnCard else palette.lightTextOnCard

    val isUserScrolling = remember { mutableStateOf(false) }
    val lastUserInteraction = remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) { viewModel.startExport() }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isUserScrolling.value = true
            lastUserInteraction.value = System.currentTimeMillis()
        }
    }

    LaunchedEffect(exportLog.size) {
        if (exportLog.isNotEmpty()) {
            val idleTime = System.currentTimeMillis() - lastUserInteraction.value
            val shouldAutoScroll = !isUserScrolling.value || idleTime > 3000
            if (shouldAutoScroll) {
                scope.launch {
                    listState.animateScrollToItem(exportLog.size - 1)
                    isUserScrolling.value = false
                }
            }
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(30); visible = true }
    val ox by animateFloatAsState(targetValue = if (visible) 0f else 120f, animationSpec = tween(400, easing = SlideEasing), label = "ox")

    Column(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = ox }) {
        Spacer(Modifier.height(8.dp))

        when (val state = exportState) {
            is ExtractState.Success -> {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CheckCircle, null, tint = ExtractColors.SoftGreen, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("EXPORT COMPLETE!", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = textOnBg, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text("Saved to Downloads as .waview", fontFamily = ExtractFonts.SpaceGrotesk, fontSize = 13.sp, color = textOnBg.copy(alpha = 0.55f))
                }
            }
            is ExtractState.Error -> {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = ExtractColors.SoftRed, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("EXPORT FAILED", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = textOnBg)
                    Spacer(Modifier.height(4.dp))
                    Text(state.message, fontFamily = ExtractFonts.SpaceGrotesk, fontSize = 13.sp, color = textOnBg.copy(alpha = 0.55f), textAlign = TextAlign.Center)
                }
            }
            else -> {
                Text("EXPORTING YOUR DATA...", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = textOnBg)
                Spacer(Modifier.height(4.dp))
                Text("Please keep the app open", fontFamily = ExtractFonts.SpaceGrotesk, fontSize = 13.sp, color = textOnBg.copy(alpha = 0.55f))
            }
        }

        Spacer(Modifier.height(6.dp))

        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50.dp)).background(progressTrack)) {
            Box(modifier = Modifier.fillMaxWidth(exportProgress).fillMaxHeight().clip(RoundedCornerShape(50.dp)).background(ExtractColors.ProgressGreen))
        }
        Spacer(Modifier.height(4.dp))
        Text("${(exportProgress * 100).toInt()}%", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp, color = textOnBg.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .graphicsLayer { shadowElevation = if (isDark) 40f else 20f; shape = LogCardShape; clip = true }
                .background(cardGradient, LogCardShape)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(logDotColor))
                    Spacer(Modifier.width(8.dp))
                    Text("OPERATION LOG", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 2.sp, color = logLabelColor)
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(exportLog.size) { index ->
                        Text("› ${exportLog[index]}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = logTextColor, lineHeight = 16.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (exportState is ExtractState.Success) {
            val successState = exportState as ExtractState.Success
            SlabButton(text = "OPEN IN VIEWER", fontSize = 15, onClick = onOpenViewer)
            Spacer(Modifier.height(8.dp))
            SlabButtonWithIcon(text = "SHARE FILE", secondary = true, icon = {
                Icon(Icons.Filled.Share, null, tint = shareIconTint, modifier = Modifier.size(16.dp))
            }) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", successState.file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share .waview"))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
