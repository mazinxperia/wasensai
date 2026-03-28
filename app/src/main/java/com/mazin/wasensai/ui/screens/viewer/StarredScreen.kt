package com.mazin.wasensai.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mazin.wasensai.data.model.Message
import com.mazin.wasensai.ui.theme.viewerColors
import com.mazin.wasensai.utils.DateUtils
import com.mazin.wasensai.viewmodel.ViewerViewModel
import java.io.File

@Composable
fun StarredScreen(
    viewModel: ViewerViewModel,
    onChatClick: (Long) -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    val starred by viewModel.starredMessages.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(viewerColors.background)
    ) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(viewerColors.header)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {}) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = viewerColors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                "Starred",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = viewerColors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.Search, null, tint = viewerColors.textPrimary, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.MoreVert, null, tint = viewerColors.textPrimary, modifier = Modifier.size(22.dp))
            }
        }

        if (starred.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Star, null,
                        modifier = Modifier.size(48.dp),
                        tint = viewerColors.textSecondary.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No starred messages", color = viewerColors.textSecondary, fontSize = 14.sp)
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(viewerColors.surface)
        ) {
            items(starred, key = { it.id }) { message ->
                val chat = viewModel.getChatById(message.chatId)
                val chatName = chat?.let {
                    if (it.isGroup) it.subject.ifEmpty { "Group" }
                    else viewModel.getContactName(it.jid)
                } ?: ""
                val isSent = message.fromMe == 1

                // Contact avatar
                val avatarFile = rememberAvatarFile(viewModel, chat?.jid)
                val avatarFallbackColor = remember(chat?.id, viewerColors) {
                    viewerColors.avatarFallbackColors[((chat?.id ?: 0) % viewerColors.avatarFallbackColors.size).toInt().coerceAtLeast(0)]
                }

                StarredItem(
                    message = message,
                    chatName = chatName,
                    isSent = isSent,
                    avatarFile = avatarFile,
                    avatarFallbackColor = avatarFallbackColor,
                    onClick = { chat?.let { onChatClick(it.id) } }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StarredItem(
    message: Message,
    chatName: String,
    isSent: Boolean,
    avatarFile: File?,
    avatarFallbackColor: Color,
    onClick: () -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    val avatarModel = rememberViewerImageModel(avatarFile)
    val timestampStr = remember(message.timestamp) {
        DateUtils.formatMessageTimestamp(message.timestamp)
    }
    val dateStr = remember(message.timestamp) {
        DateUtils.formatChatTimestamp(message.timestamp)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Direction header row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(viewerColors.background)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            if (avatarModel != null) {
                AsyncImage(
                    model = avatarModel,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(avatarFallbackColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        chatName.take(1).uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = viewerColors.fullScreenOnColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Direction text
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSent) {
                    Text("You", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = viewerColors.textSecondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = viewerColors.textSecondary, modifier = Modifier.size(10.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(chatName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = viewerColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(chatName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = viewerColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = viewerColors.textSecondary, modifier = Modifier.size(10.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("You", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = viewerColors.textSecondary)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Text(dateStr, fontSize = 12.sp, color = viewerColors.textSecondary)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = viewerColors.textSecondary, modifier = Modifier.size(12.dp))
        }

        // ── Message bubble card ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSent) viewerColors.bubblePrimary else viewerColors.bubbleSecondary)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {

                // Forwarded label
                if (message.isForwarded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.Reply, null, tint = viewerColors.textSecondary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Forwarded",
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            color = viewerColors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Message content by type
                when (message.messageType) {
                    0 -> {
                        // Text
                        if (message.textData.isNotEmpty()) {
                            Text(message.textData, fontSize = 14.sp, color = viewerColors.textPrimary)
                        }
                    }
                    1 -> {
                        // Image placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(viewerColors.placeholder),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Image, null, tint = viewerColors.textSecondary, modifier = Modifier.size(40.dp))
                        }
                        if (message.mediaCaption.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(message.mediaCaption, fontSize = 14.sp, color = viewerColors.textPrimary)
                        }
                    }
                    9 -> {
                        // Document
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(viewerColors.surfaceVariant)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(viewerColors.error),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("PDF", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = viewerColors.fullScreenOnColor)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val filename = message.mediaFilePath.substringAfterLast("/")
                                    .ifEmpty { "Document" }
                                Text(
                                    filename,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = viewerColors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (message.mediaSize > 0) {
                                    val sizeKb = message.mediaSize / 1024
                                    val sizeStr = if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb kB"
                                    Text("$sizeStr • PDF", fontSize = 12.sp, color = viewerColors.textSecondary)
                                }
                            }
                        }
                    }
                    2 -> {
                        // Audio
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PlayCircle, null, tint = viewerColors.action, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Audio message", fontSize = 14.sp, color = viewerColors.textPrimary)
                        }
                    }
                    3 -> {
                        // Video placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(viewerColors.fullScreenBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.PlayCircle, null, tint = viewerColors.fullScreenOnColor, modifier = Modifier.size(40.dp))
                        }
                    }
                    else -> {
                        if (message.textData.isNotEmpty()) {
                            Text(message.textData, fontSize = 14.sp, color = viewerColors.textPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Timestamp + star + ticks ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Star, null,
                        tint = viewerColors.warning,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(timestampStr, fontSize = 11.sp, color = viewerColors.textSecondary)
                    if (isSent) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(Icons.Rounded.DoneAll, null, tint = viewerColors.seen, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Forward button on right edge
        // (In real WA it floats outside — keeping it inside for simplicity in Compose)
    }
}
