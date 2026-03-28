package com.mazin.wasensai.ui.screens.viewer

import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.mazin.wasensai.ui.theme.viewerColors
import com.mazin.wasensai.utils.DateUtils
import com.mazin.wasensai.viewmodel.ViewerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenMediaScreen(
    chatId: Long,
    startIndex: Int,
    onBackClick: () -> Unit,
    viewModel: ViewerViewModel
) {
    val viewerColors = MaterialTheme.viewerColors
    val mediaMessages = remember(chatId) { viewModel.getMediaMessages(chatId) }
    val pagerState = rememberPagerState(initialPage = startIndex) { mediaMessages.size }
    var barsVisible by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // Auto-hide bars after 3 seconds of no interaction
    LaunchedEffect(barsVisible) {
        if (barsVisible) {
            delay(3000)
            barsVisible = false
        }
    }

    val currentMessage = mediaMessages.getOrNull(pagerState.currentPage)
    val isVideo = currentMessage?.messageType == 3
    val currentMediaFile = remember(currentMessage?.id) {
        currentMessage?.id?.let(viewModel::getMediaFileForMessage)
    }

    // Sender name for header
    val senderName = remember(currentMessage) {
        currentMessage?.let { msg ->
            val chat = viewModel.getChatById(chatId)
            when {
                msg.fromMe == 1 -> "You"
                chat?.isGroup == true -> viewModel.getContactName(msg.senderJid.ifEmpty { msg.chatJid })
                else -> if (chat != null) viewModel.getContactName(chat.jid) else "Unknown"
            }
        } ?: ""
    }

    val timestampStr = remember(currentMessage) {
        currentMessage?.let { DateUtils.formatMessageTimestamp(it.timestamp) } ?: ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(viewerColors.fullScreenBackground)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val message = mediaMessages.getOrNull(page)
            val mediaFile = remember(message?.id) {
                message?.id?.let(viewModel::getMediaFileForMessage)
            }
            val isPageVideo = message?.messageType == 3

            if (isPageVideo) {
                if (isValidViewerFile(mediaFile)) {
                    VideoPlayer(
                        mediaFile = mediaFile,
                        onTap = {
                            barsVisible = !barsVisible
                        },
                        barsVisible = barsVisible
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Media not found in backup", color = viewerColors.fullScreenOnColor.copy(0.5f), fontSize = 14.sp)
                    }
                }
            } else {
                // Image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                barsVisible = !barsVisible
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val f = mediaFile
                    if (isValidViewerFile(f)) {
                        AsyncImage(
                            model = rememberViewerImageModel(f?.absolutePath),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text("Media not found in backup", color = viewerColors.fullScreenOnColor.copy(0.5f), fontSize = 14.sp)
                    }

                    // Caption overlay at bottom
                    val caption = message?.mediaCaption ?: ""
                    if (caption.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, viewerColors.fullScreenBackground.copy(0.75f))
                                    )
                                )
                                .padding(horizontal = 16.dp, vertical = 56.dp)
                        ) {
                            Column {
                                Text(
                                    text = caption,
                                    color = viewerColors.fullScreenOnColor,
                                    fontSize = 14.sp,
                                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis
                                )
                                if (!expanded && caption.length > 80) {
                                    Text(
                                        "Read more",
                                        color = viewerColors.fullScreenOnColor.copy(0.7f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) { expanded = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Top Bar ──
        if (barsVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(viewerColors.fullScreenBackground.copy(0.7f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = viewerColors.fullScreenOnColor, modifier = Modifier.size(22.dp))
                    }

                    // Sender + timestamp
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            senderName,
                            color = viewerColors.fullScreenOnColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (timestampStr.isNotEmpty()) {
                            Text(
                                timestampStr,
                                color = viewerColors.fullScreenOnColor.copy(0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Star button
                    IconButton(onClick = {}) {
                        Icon(Icons.Rounded.StarBorder, null, tint = viewerColors.fullScreenOnColor, modifier = Modifier.size(22.dp))
                    }

                    // Forward button
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Rounded.Reply, null, tint = viewerColors.fullScreenOnColor, modifier = Modifier.size(22.dp))
                    }

                    // Download button
                    IconButton(onClick = {
                        val f = currentMediaFile
                        if (isValidViewerFile(f)) {
                            val currentFile = f ?: return@IconButton
                            try {
                                val destName = if (currentFile.extension.lowercase() == "opus")
                                    currentFile.nameWithoutExtension + ".ogg" else currentFile.name
                                val dest = File(
                                    File(Environment.getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_DOWNLOADS), "WASensai").also { it.mkdirs() },
                                    destName
                                )
                                currentFile.copyTo(dest, overwrite = true)
                                android.media.MediaScannerConnection.scanFile(
                                    context, arrayOf(dest.absolutePath), null, null
                                )
                                android.widget.Toast.makeText(context, "Saved to Downloads/WASensai", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Save failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Rounded.Download, null, tint = viewerColors.fullScreenOnColor, modifier = Modifier.size(22.dp))
                    }

                    // 3-dot
                    IconButton(onClick = {}) {
                        Icon(Icons.Rounded.MoreVert, null, tint = viewerColors.fullScreenOnColor, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

// ── Video Player ──
@Composable
private fun VideoPlayer(
    mediaFile: File?,
    onTap: () -> Unit,
    barsVisible: Boolean
) {
    val viewerColors = MaterialTheme.viewerColors
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    LaunchedEffect(mediaFile) {
        if (isValidViewerFile(mediaFile)) {
            player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(mediaFile)))
            player.prepare()
            player.play()
        }
    }

    // Poll position every 200ms
    LaunchedEffect(player, isSeeking, isPlaying) {
        if (!isSeeking && !player.isPlaying) return@LaunchedEffect
        while (isActive && (isSeeking || player.isPlaying)) {
            isPlaying = player.isPlaying
            if (!isSeeking) {
                positionMs = player.currentPosition.coerceAtLeast(0)
                durationMs = player.duration.takeIf { it > 0 } ?: 0
                if (durationMs > 0) seekProgress = positionMs.toFloat() / durationMs.toFloat()
            }
            delay(200)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.stop()
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(viewerColors.fullScreenBackground)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (player.isPlaying) player.pause() else player.play()
                    onTap()
                })
            }
    ) {
        // Video surface
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Center play/pause button — fades after 2s of not touching
        if (barsVisible || !isPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .background(viewerColors.fullScreenBackground.copy(0.5f), CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (player.isPlaying) player.pause() else player.play()
                        onTap()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    null,
                    tint = viewerColors.fullScreenOnColor,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Bottom controls — scrollable time bar
        if (barsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, viewerColors.fullScreenBackground.copy(0.8f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Seek bar
                Slider(
                    value = seekProgress,
                    onValueChange = { v ->
                        isSeeking = true
                        seekProgress = v
                        positionMs = (v * durationMs).toLong()
                    },
                    onValueChangeFinished = {
                        val dur = player.duration.takeIf { it > 0 } ?: run { isSeeking = false; return@Slider }
                        player.seekTo((seekProgress * dur).toLong())
                        isSeeking = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = viewerColors.action,
                        activeTrackColor = viewerColors.action,
                        inactiveTrackColor = viewerColors.fullScreenOnColor.copy(0.3f)
                    )
                )

                // Time row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatMs(positionMs), color = viewerColors.fullScreenOnColor, fontSize = 12.sp)
                    Text(formatMs(durationMs), color = viewerColors.fullScreenOnColor.copy(0.6f), fontSize = 12.sp)
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
