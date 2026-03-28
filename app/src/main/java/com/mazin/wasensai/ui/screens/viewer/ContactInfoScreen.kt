package com.mazin.wasensai.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mazin.wasensai.ui.theme.viewerColors
import com.mazin.wasensai.viewmodel.ViewerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactInfoScreen(
    chatId: Long,
    onBackClick: () -> Unit,
    onChatClick: () -> Unit,
    viewModel: ViewerViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val viewerColors = MaterialTheme.viewerColors
    val chat = rememberViewerChat(viewModel, chatId)
    val displayName = chat?.let {
        if (it.isGroup) it.subject.ifEmpty { "Group" }
        else viewModel.getContactName(it.jid)
    } ?: ""

    val avatarFile = rememberAvatarFile(viewModel, chat?.jid)
    val avatarModel = rememberViewerImageModel(avatarFile)

    val contact = chat?.let { viewModel.contacts.value[it.jid] }
    val phone = chat?.jid?.substringBefore("@")?.let { "+$it" } ?: ""
    // BUG 13: WhatsApp Business verified badge condition
    // Shown for the official WhatsApp Business identity (phone == "0") or any business-type contact
    val isBusinessVerified = remember(chat?.jid) {
        chat?.jid?.substringBefore("@") == "0"
    }

    // Stats from real data
    val allMessages = remember(chatId) { viewModel.getMediaMessages(chatId) }
    val stats = remember(allMessages) {
        var mediaCount = 0
        var docCount = 0
        var audioCount = 0
        allMessages.forEach { message ->
            when (message.messageType) {
                1, 3 -> mediaCount += 1
                9 -> docCount += 1
                2 -> audioCount += 1
            }
        }
        Triple(mediaCount, docCount, audioCount)
    }
    val totalMessages = allMessages.size
    val mediaCount = stats.first
    val docCount = stats.second
    val audioCount = stats.third

    val avatarFallbackColor = remember(chat?.id) {
        viewerColors.avatarFallbackColors[((chat?.id ?: 0) % viewerColors.avatarFallbackColors.size).toInt().coerceAtLeast(0)]
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(viewerColors.header)
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
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = viewerColors.textPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.MoreVert, null, tint = viewerColors.textPrimary, modifier = Modifier.size(22.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Avatar + Name + Phone ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(viewerColors.background)
                    .padding(top = 24.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                if (avatarModel != null) {
                    AsyncImage(
                        model = avatarModel,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(avatarFallbackColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            displayName.take(1).uppercase(),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = viewerColors.fullScreenOnColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text(
                        displayName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = viewerColors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    // BUG 13: Custom WA-style verified badge — blue circle + white checkmark
                    if (isBusinessVerified) {
                        VerifiedBadge()
                    }
                }

                if (phone.isNotEmpty() && chat?.isGroup == false) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        phone,
                        fontSize = 15.sp,
                        color = viewerColors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                if (chat?.isGroup == true && chat.memberCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Group · ${chat.memberCount} participants",
                        fontSize = 14.sp,
                        color = viewerColors.textSecondary
                    )
                }

                if (contact?.status?.isNotEmpty() == true) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        contact.status,
                        fontSize = 14.sp,
                        color = viewerColors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Action Buttons — View Chat ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(viewerColors.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    icon = Icons.AutoMirrored.Rounded.Message,
                    label = "Message",
                    modifier = Modifier.weight(1f),
                    onClick = onChatClick
                )
                ActionButton(
                    icon = Icons.Rounded.Search,
                    label = "Search",
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Chat Stats ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(viewerColors.background)
            ) {
                StatsSectionHeader("CHAT STATISTICS")

                StatsRow(
                    icon = Icons.Rounded.ChatBubbleOutline,
                    label = "Total messages",
                    value = totalMessages.toString()
                )
                StatsDivider()
                StatsRow(
                    icon = Icons.Rounded.Image,
                    label = "Photos & videos",
                    value = mediaCount.toString()
                )
                StatsDivider()
                StatsRow(
                    icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                    label = "Documents",
                    value = docCount.toString()
                )
                StatsDivider()
                StatsRow(
                    icon = Icons.Rounded.Mic,
                    label = "Voice messages",
                    value = audioCount.toString()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Export Info ──
            val exportInfo = viewModel.exportInfo.value
            if (exportInfo != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(viewerColors.background)
                ) {
                    StatsSectionHeader("ARCHIVE INFO")
                    if (exportInfo.exportDate.isNotEmpty()) {
                        StatsRow(
                            icon = Icons.Rounded.CalendarMonth,
                            label = "Exported on",
                            value = exportInfo.exportDate.take(10)
                        )
                        StatsDivider()
                    }
                    StatsRow(
                        icon = Icons.Rounded.Info,
                        label = "App version",
                        value = exportInfo.appVersion
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Group participants list ──
            if (chat?.isGroup == true) {
                val group = remember(chatId) { viewModel.getGroupForChat(chatId) }
                val participants = group?.participants ?: emptyList()
                if (participants.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(viewerColors.background)
                    ) {
                        StatsSectionHeader("${participants.size} PARTICIPANTS")
                        participants.forEachIndexed { index, participant ->
                            val isMe = participant.jid == "lid_me"
                            val phone = if (isMe) "" else participant.jid.substringBefore("@")
                            val name  = if (isMe) "You" else viewModel.getContactName(participant.jid)
                            val avatarFile = rememberAvatarFile(viewModel, if (isMe) "me" else participant.jid)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar
                                val currentAvatarFile = avatarFile
                                if (currentAvatarFile != null && currentAvatarFile.exists()) {
                                    AsyncImage(
                                        model = currentAvatarFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(viewerColors.placeholder),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            name.take(1).uppercase(),
                                            color = viewerColors.fullScreenOnColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = viewerColors.textPrimary,
                                        maxLines = 1
                                    )
                                    if (phone.isNotEmpty()) {
                                        Text(
                                            "+$phone",
                                            fontSize = 12.sp,
                                            color = viewerColors.textSecondary
                                        )
                                    }
                                }
                            }
                            if (index < participants.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 68.dp),
                                    thickness = 0.5.dp,
                                    color = viewerColors.divider
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatsSectionHeader(label: String) {
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.viewerColors.textSecondary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun StatsRow(icon: ImageVector, label: String, value: String) {
    val viewerColors = MaterialTheme.viewerColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = viewerColors.icon,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            label,
            fontSize = 15.sp,
            color = viewerColors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = viewerColors.textPrimary
        )
    }
}

@Composable
private fun StatsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.viewerColors.divider
    )
}

// BUG 13: Custom WhatsApp-style verified badge — blue circle with white checkmark
// Does NOT use a Material Icon (which would be a generic icon, not WA-style)
@Composable
private fun VerifiedBadge() {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.viewerColors.verified),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = "Verified",
            tint = MaterialTheme.viewerColors.fullScreenOnColor,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(viewerColors.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = viewerColors.textPrimary,
            modifier = Modifier.size(22.dp)
        )
        Text(
            label,
            fontSize = 13.sp,
            color = viewerColors.textPrimary
        )
    }
}
