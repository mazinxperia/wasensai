package com.mazin.wasensai.ui.screens.extract

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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

@Composable
fun ExtractOptionsContent(viewModel: ExtractViewModel, onNext: () -> Unit) {
    val includeImages      by viewModel.includeImages.collectAsStateWithLifecycle()
    val includeVideos      by viewModel.includeVideos.collectAsStateWithLifecycle()
    val includeAudio       by viewModel.includeAudio.collectAsStateWithLifecycle()
    val includeDocs        by viewModel.includeDocs.collectAsStateWithLifecycle()
    val includeAvatars     by viewModel.includeAvatars.collectAsStateWithLifecycle()
    val includeOthers      by viewModel.includeOthers.collectAsStateWithLifecycle()
    val includeThumbnails  by viewModel.includeThumbnails.collectAsStateWithLifecycle()
    val scanResult         by viewModel.scanResult.collectAsStateWithLifecycle()

    val includeAnyMedia = includeImages || includeVideos || includeAudio || includeDocs || includeOthers

    val estimatedSize = (scanResult?.msgstoreSize ?: 0) + (scanResult?.waDbSize ?: 0) +
            (if (includeAvatars)    scanResult?.avatarSize ?: 0 else 0) +
            (if (includeImages)     (scanResult?.imagesSizeMb     ?: 0) * 1024 * 1024 else 0) +
            (if (includeVideos)     (scanResult?.videosSizeMb     ?: 0) * 1024 * 1024 else 0) +
            (if (includeAudio)      (scanResult?.audioSizeMb      ?: 0) * 1024 * 1024 else 0) +
            (if (includeDocs)       (scanResult?.docsSizeMb       ?: 0) * 1024 * 1024 else 0) +
            (if (includeOthers)     (scanResult?.othersSizeMb     ?: 0) * 1024 * 1024 else 0) +
            (if (includeThumbnails) (scanResult?.thumbnailsSizeMb ?: 0) * 1024 * 1024 else 0)

    val isDark = extractIsDark()
    val cardGradient = extractCardGradient(isDark)
    val textOnCard = extractTextOnCard(isDark)
    val mutedOnCard = extractMutedOnCard(isDark)
    val textOnBg = extractTextOnBg(isDark)
    val accent = extractAccent(isDark)
    val palette = LocalAppPalette.current
    val separatorColor = (if (isDark) palette.darkTextOnCard else palette.lightTextOnCard).copy(alpha = 0.12f)
    val checkUnchecked = (if (isDark) palette.darkTextOnCard else palette.lightTextOnCard).copy(alpha = 0.3f)
    val sizeColor = if (isDark) palette.darkTextOnCard else palette.lightTextOnCard
    val sizeSubColor = (if (isDark) palette.darkTextOnCard else palette.lightTextOnCard).copy(alpha = 0.5f)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(30); visible = true }
    val ox by animateFloatAsState(targetValue = if (visible) 0f else 120f, animationSpec = tween(400, easing = SlideEasing), label = "ox")

    Column(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = ox }, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Text("WHAT TO EXPORT", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = textOnBg, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text("Choose what to include in your archive.", fontFamily = ExtractFonts.SpaceGrotesk, fontSize = 13.sp, color = textOnBg.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))

        // ─── Single scrollable card — exactly like ScanScreen ────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer { shadowElevation = if (isDark) 40f else 20f; shape = CardShape; clip = true }
                .background(cardGradient, CardShape)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                // Messages & Contacts (always included, no checkbox)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(40.dp))
                    Text("MESSAGES & CONTACTS", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp, color = textOnCard, modifier = Modifier.weight(1f))
                    Text(FileUtils.formatFileSize((scanResult?.msgstoreSize ?: 0) + (scanResult?.waDbSize ?: 0)), fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textOnCard.copy(alpha = 0.45f))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                OptionRow("PROFILE PHOTOS", FileUtils.formatFileSize(scanResult?.avatarSize ?: 0), includeAvatars, true, textOnCard, separatorColor, accent, checkUnchecked) { viewModel.setIncludeAvatars(it) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))

                // Media section header
                Spacer(Modifier.height(8.dp))
                Text("MEDIA", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 3.sp, color = textOnCard.copy(alpha = 0.4f))
                Spacer(Modifier.height(4.dp))

                OptionRow("IMAGES",    FileUtils.formatFileSize((scanResult?.imagesSizeMb ?: 0) * 1024 * 1024),    includeImages,    true, textOnCard, separatorColor, accent, checkUnchecked) { viewModel.setIncludeImages(it) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                OptionRow("VIDEOS",    FileUtils.formatFileSize((scanResult?.videosSizeMb ?: 0) * 1024 * 1024),    includeVideos,    true, textOnCard, separatorColor, accent, checkUnchecked) { viewModel.setIncludeVideos(it) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                OptionRow("AUDIO",     FileUtils.formatFileSize((scanResult?.audioSizeMb ?: 0) * 1024 * 1024),     includeAudio,     true, textOnCard, separatorColor, accent, checkUnchecked) { viewModel.setIncludeAudio(it) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                OptionRow("DOCUMENTS", FileUtils.formatFileSize((scanResult?.docsSizeMb ?: 0) * 1024 * 1024),      includeDocs,      true, textOnCard, separatorColor, accent, checkUnchecked) { viewModel.setIncludeDocs(it) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                OptionRow("OTHERS",    FileUtils.formatFileSize((scanResult?.othersSizeMb ?: 0) * 1024 * 1024),    includeOthers,    true, textOnCard, separatorColor, accent, checkUnchecked) { viewModel.setIncludeOthers(it) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
                OptionRow("THUMBNAILS",FileUtils.formatFileSize((scanResult?.thumbnailsSizeMb ?: 0) * 1024 * 1024),includeThumbnails,true, textOnCard, separatorColor, accent, checkUnchecked) { viewModel.setIncludeThumbnails(it) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))

                // What will be exported preview
                Spacer(Modifier.height(10.dp))
                Text("WHAT WILL BE EXPORTED", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 3.sp, color = textOnCard.copy(alpha = 0.4f))
                Spacer(Modifier.height(6.dp))
                ExportPreviewRow("Messages & metadata", true, "always included", textOnCard, accent)
                ExportPreviewRow("msgstore.db + wa.db", true, "raw database backup", textOnCard, accent)
                ExportPreviewRow("Profile photos", includeAvatars,
                    if (includeAvatars) FileUtils.formatFileSize(scanResult?.avatarSize ?: 0) + " · ${scanResult?.avatarCount ?: 0} contacts" else "skipped",
                    textOnCard, accent)
                if (includeImages || includeVideos || includeAudio || includeDocs || includeOthers) {
                    Spacer(Modifier.height(4.dp))
                    Text("MEDIA", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 2.sp, color = textOnCard.copy(alpha = 0.3f))
                    Spacer(Modifier.height(2.dp))
                }
                ExportPreviewRow("Images",    includeImages,    if (includeImages)    FileUtils.formatFileSize((scanResult?.imagesSizeMb  ?: 0) * 1024 * 1024) + " · ${scanResult?.imagesCount  ?: 0} files" else "skipped", textOnCard, accent)
                ExportPreviewRow("Videos",    includeVideos,    if (includeVideos)    FileUtils.formatFileSize((scanResult?.videosSizeMb  ?: 0) * 1024 * 1024) + " · ${scanResult?.videosCount  ?: 0} files" else "skipped", textOnCard, accent)
                ExportPreviewRow("Audio",     includeAudio,     if (includeAudio)     FileUtils.formatFileSize((scanResult?.audioSizeMb   ?: 0) * 1024 * 1024) + " · ${scanResult?.audioCount   ?: 0} files" else "skipped", textOnCard, accent)
                ExportPreviewRow("Documents", includeDocs,      if (includeDocs)      FileUtils.formatFileSize((scanResult?.docsSizeMb    ?: 0) * 1024 * 1024) + " · ${scanResult?.docsCount    ?: 0} files" else "skipped", textOnCard, accent)
                ExportPreviewRow("Others",    includeOthers,    if (includeOthers)    FileUtils.formatFileSize((scanResult?.othersSizeMb  ?: 0) * 1024 * 1024) + " · ${scanResult?.othersCount  ?: 0} files" else "skipped", textOnCard, accent)
                ExportPreviewRow("Thumbnails",includeThumbnails,if (includeThumbnails)FileUtils.formatFileSize((scanResult?.thumbnailsSizeMb ?: 0) * 1024 * 1024) + " · ${scanResult?.thumbnailsCount ?: 0} files" else "skipped", textOnCard, accent)
                Spacer(Modifier.height(4.dp))
            }

            // ─── Estimated size pinned at bottom of card ──────────────────────
            Box(Modifier.fillMaxWidth().height(1.dp).background(separatorColor))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ESTIMATED SIZE", fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 3.sp, color = sizeColor.copy(alpha = 0.7f))
                    Text("SAVED TO DOWNLOADS AS .WAVIEW", fontFamily = ExtractFonts.SpaceGrotesk, fontSize = 9.sp, letterSpacing = 0.5.sp, color = sizeSubColor)
                }
                Text(FileUtils.formatFileSize(estimatedSize), fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Black, fontSize = 28.sp, color = sizeColor, letterSpacing = (-1).sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        SlabButton(text = "START EXPORT", fontSize = 17, onClick = onNext)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ExportPreviewRow(
    label: String, included: Boolean, detail: String,
    textColor: Color, accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (included) "✓" else "✗",
            fontFamily = ExtractFonts.SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (included) accentColor else textColor.copy(alpha = 0.25f),
            modifier = Modifier.width(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (included) textColor else textColor.copy(alpha = 0.3f), modifier = Modifier.weight(1f))
        Text(detail, fontFamily = ExtractFonts.SpaceGrotesk, fontSize = 10.sp, color = if (included) textColor.copy(alpha = 0.5f) else textColor.copy(alpha = 0.2f))
    }
}

@Composable
private fun OptionRow(
    title: String, info: String, checked: Boolean, enabled: Boolean,
    textColor: Color, infoColor: Color, accent: Color, uncheckedColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)
            .then(if (enabled) Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onCheckedChange(!checked) } else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            if (checked) {
                Canvas(Modifier.size(26.dp)) { drawCircle(color = accent, style = Stroke(width = 2.dp.toPx())) }
                Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(16.dp))
            } else {
                Canvas(Modifier.size(26.dp)) { drawCircle(color = uncheckedColor, style = Stroke(width = 2.dp.toPx())) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(title, fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp, color = textColor, modifier = Modifier.weight(1f))
        Text(info, fontFamily = ExtractFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textColor.copy(alpha = 0.45f))
    }
}
