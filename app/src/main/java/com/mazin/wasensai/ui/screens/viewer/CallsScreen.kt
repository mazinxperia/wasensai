package com.mazin.wasensai.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mazin.wasensai.data.model.CallLog
import com.mazin.wasensai.ui.theme.viewerColors
import com.mazin.wasensai.utils.DateUtils
import com.mazin.wasensai.viewmodel.ViewerViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CallsScreen(
    viewModel: ViewerViewModel,
    onChatClick: (Long) -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    val callLogs by viewModel.callLogs.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val chatIdByJid = remember(chats) { chats.associate { it.jid to it.id } }

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Calls",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = viewerColors.textPrimary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CameraAlt, null, tint = viewerColors.textPrimary, modifier = Modifier.size(22.dp))
                Icon(Icons.Rounded.Search, null, tint = viewerColors.textPrimary, modifier = Modifier.size(22.dp))
                Icon(Icons.Rounded.MoreVert, null, tint = viewerColors.textPrimary, modifier = Modifier.size(22.dp))
            }
        }

        // ── Scrollable content ──
        if (callLogs.isEmpty()) {
            // Action buttons + empty state
            CallActionButtons()
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No recent calls", color = viewerColors.textSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // ── Action buttons row ──
                item(key = "action_buttons") {
                    CallActionButtons()
                }

                // ── Recent label ──
                item(key = "recent_label") {
                    Text(
                        "Recent",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = viewerColors.textPrimary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 16.dp)
                    )
                }

                // ── Call log items ──
                items(callLogs, key = { it.id }, contentType = { "call_item" }) { call ->
                    val avatarFile = rememberAvatarFile(viewModel, call.jid)
                    val avatarFallbackColor = remember(call.jid, viewerColors) {
                        viewerColors.avatarFallbackColors[(call.jid.hashCode().and(0x7FFFFFFF) % viewerColors.avatarFallbackColors.size)]
                    }
                    CallLogItem(
                        call = call,
                        displayName = viewModel.getContactName(call.jid),
                        avatarFile = avatarFile,
                        avatarFallbackColor = avatarFallbackColor,
                        onClick = {
                            chatIdByJid[call.jid]?.let(onChatClick)
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp),
                        thickness = 0.5.dp,
                        color = viewerColors.divider
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun CallActionButtons() {
    val viewerColors = MaterialTheme.viewerColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        CallActionButton(icon = Icons.Rounded.Call, label = "Call")
        CallActionButton(icon = Icons.Rounded.CalendarMonth, label = "Schedule")
        CallActionButton(icon = Icons.Rounded.Dialpad, label = "Keypad")
        CallActionButton(icon = Icons.Rounded.FavoriteBorder, label = "Favorites")
    }
}

@Composable
private fun CallActionButton(icon: ImageVector, label: String) {
    val viewerColors = MaterialTheme.viewerColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(viewerColors.inputBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = viewerColors.textPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            label,
            fontSize = 12.sp,
            color = viewerColors.icon
        )
    }
}

@Composable
private fun CallLogItem(
    call: CallLog,
    displayName: String,
    avatarFile: File?,
    avatarFallbackColor: Color,
    onClick: () -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    val avatarModel = rememberViewerImageModel(avatarFile)
    // callResult: 0,4,7 = missed/no answer, 2 = declined, 5 = completed
    val isMissed = !call.fromMe && call.callResult in listOf(0, 2, 4, 7)
    val isOutgoing = call.fromMe
    val nameColor = if (isMissed) viewerColors.error else viewerColors.textPrimary

    // Format date+time for call log display
    val dateStr = remember(call.timestamp) {
        formatCallDate(call.timestamp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Avatar ──
        if (avatarModel != null) {
            AsyncImage(
                model = avatarModel,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(avatarFallbackColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    displayName.take(1).uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = viewerColors.fullScreenOnColor
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // ── Name + Arrow + Date ──
        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = nameColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Directional arrow
                CallArrow(isMissed = isMissed, isOutgoing = isOutgoing)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    dateStr,
                    fontSize = 13.sp,
                    color = if (isMissed) viewerColors.error else viewerColors.textSecondary
                )
            }
        }

        // ── Phone icon ──
        Icon(
            if (call.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Phone,
            contentDescription = null,
            tint = viewerColors.icon,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun CallArrow(isMissed: Boolean, isOutgoing: Boolean) {
    val viewerColors = MaterialTheme.viewerColors
    // Missed = incoming red arrow pointing bottom-left ↙
    // Outgoing = green arrow pointing top-right ↗
    // Received = grey arrow pointing bottom-left ↙
    val (arrowIcon, arrowTint) = when {
        isOutgoing -> Icons.AutoMirrored.Rounded.CallMade to viewerColors.action
        isMissed -> Icons.AutoMirrored.Rounded.CallReceived to viewerColors.error
        else -> Icons.AutoMirrored.Rounded.CallReceived to viewerColors.textSecondary
    }
    Icon(
        imageVector = arrowIcon,
        contentDescription = null,
        tint = arrowTint,
        modifier = Modifier.size(14.dp)
    )
}

private fun formatCallDate(timestampMs: Long): String {
    val now = Calendar.getInstance()
    val callCal = Calendar.getInstance().apply { timeInMillis = timestampMs }

    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - callCal.get(Calendar.DAY_OF_YEAR)
    val yearDiff = now.get(Calendar.YEAR) - callCal.get(Calendar.YEAR)

    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))

    return when {
        yearDiff == 0 && dayDiff == 0 -> "Today, $timeStr"
        yearDiff == 0 && dayDiff == 1 -> "Yesterday, $timeStr"
        yearDiff == 0 -> {
            val dateStr = SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(timestampMs))
            "$dateStr, $timeStr"
        }
        else -> {
            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestampMs))
            "$dateStr, $timeStr"
        }
    }
}
