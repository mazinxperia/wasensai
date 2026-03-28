package com.mazin.wasensai.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mazin.wasensai.data.model.Chat
import com.mazin.wasensai.data.repository.SyncState
import com.mazin.wasensai.utils.DateUtils
import com.mazin.wasensai.ui.theme.viewerColors
import com.mazin.wasensai.viewmodel.SearchResults
import com.mazin.wasensai.viewmodel.ViewerViewModel
import java.io.File

@Composable
private fun ChatListBody(
    filteredChatItems: List<ChatListUiModel>,
    searchQuery: String,
    matchedSnippets: Map<Long, String>,
    onChatClick: (Long) -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    if (searchQuery.isNotBlank()) {
        SearchResultsList(
            filteredChatItems = filteredChatItems,
            searchQuery = searchQuery,
            matchedSnippets = matchedSnippets,
            onChatClick = onChatClick
        )
        return
    }
    if (filteredChatItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No chats found", color = viewerColors.textSecondary)
        }
        return
    }
    val listState = rememberLazyListState()
    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
        items(filteredChatItems, key = { it.chatId }, contentType = { 0 }) { item ->
            val fallback = remember(item.chatId, viewerColors) {
                viewerColors.avatarFallbackColors[(item.chatId % viewerColors.avatarFallbackColors.size).toInt().coerceAtLeast(0)]
            }
            ChatListItemContent(
                item = item,
                avatarFallbackColor = fallback,
                searchSnippet = null,
                searchQuery = "",
                onClick = remember(item.chatId) { { onChatClick(item.chatId) } }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 80.dp), thickness = 0.5.dp, color = viewerColors.divider)
        }
    }
}

@Composable
private fun ViewerChatListLoading() {
    val viewerColors = MaterialTheme.viewerColors
    Box(
        modifier = Modifier.fillMaxSize().background(viewerColors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = viewerColors.action, modifier = Modifier.size(40.dp))
            Text("Loading chats...", fontSize = 16.sp, color = viewerColors.textPrimary, fontWeight = FontWeight.Medium)
            Text("Loading profile photos...", fontSize = 13.sp, color = viewerColors.textSecondary)
        }
    }
}

@Composable
private fun ChatListHeaderSection(
    searchQuery: String,
    chatFilter: Int,
    syncState: SyncState,
    onShowSyncSheet: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onChatFilterChange: (Int) -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    Column(modifier = Modifier.fillMaxWidth().background(viewerColors.header).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("WhatsApp", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = viewerColors.textPrimary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                when {
                    syncState.isSyncing -> {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onShowSyncSheet() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = viewerColors.textSecondary
                            )
                            Text("Syncing...", fontSize = 12.sp, color = viewerColors.textSecondary)
                        }
                    }
                    syncState.isComplete -> {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = viewerColors.action,
                            modifier = Modifier.size(18.dp).clickable { onShowSyncSheet() }
                        )
                    }
                }
                Icon(Icons.Rounded.CameraAlt, null, tint = viewerColors.textPrimary, modifier = Modifier.size(22.dp))
                Icon(Icons.Rounded.MoreVert, null, tint = viewerColors.textPrimary, modifier = Modifier.size(22.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(viewerColors.inputBackground)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = viewerColors.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = viewerColors.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(viewerColors.action),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text("Search", fontSize = 15.sp, color = viewerColors.textSecondary)
                        }
                        inner()
                    },
                    modifier = Modifier.weight(1f)
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close,
                        null,
                        tint = viewerColors.textSecondary,
                        modifier = Modifier.size(18.dp).clickable { onSearchQueryChange("") }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Personal", "Groups").forEachIndexed { idx, label ->
                val selected = chatFilter == idx
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) viewerColors.selectedPill else Color.Transparent)
                        .clickable { onChatFilterChange(idx) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) viewerColors.textPrimary else viewerColors.textSecondary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ViewerViewModel,
    onChatClick: (Long) -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    val isInitialReady by viewModel.isInitialReady.collectAsStateWithLifecycle()
    val filteredChats  by viewModel.filteredChats.collectAsStateWithLifecycle()
    val chatListUiModels by viewModel.chatListUiModels.collectAsStateWithLifecycle()
    val chatFilter     by viewModel.chatFilter.collectAsStateWithLifecycle()
    val searchResults  by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchQuery    by viewModel.searchQuery.collectAsStateWithLifecycle()
    val syncState      by viewModel.syncState.collectAsStateWithLifecycle()

    if (!isInitialReady) {
        ViewerChatListLoading()
        return
    }

    var showSyncSheet by remember { mutableStateOf(false) }

    val filteredChatItems = remember(filteredChats, chatListUiModels) {
        filteredChats.mapNotNull { chat -> chatListUiModels[chat.id] }
    }
    val matchedSnippets = remember(searchResults.messages) {
        searchResults.messages
            .groupBy { it.chatId }
            .mapValues { (_, msgs) -> msgs.last().textData }
    }

    if (showSyncSheet) {
        SyncBottomSheet(syncState = syncState, onDismiss = { showSyncSheet = false })
    }

    Column(modifier = Modifier.fillMaxSize().background(viewerColors.background)) {
        ChatListHeaderSection(
            searchQuery = searchQuery,
            chatFilter = chatFilter,
            syncState = syncState,
            onShowSyncSheet = { showSyncSheet = true },
            onSearchQueryChange = viewModel::setSearchQuery,
            onChatFilterChange = viewModel::setChatFilter
        )

        // ── List / Search ──────────────────────────────────────────────────────
        ChatListBody(
            filteredChatItems = filteredChatItems,
            searchQuery = searchQuery,
            matchedSnippets = matchedSnippets,
            onChatClick = onChatClick
        )
    }
}

// ── Sync Progress Bottom Sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncBottomSheet(syncState: SyncState, onDismiss: () -> Unit) {
    val viewerColors = MaterialTheme.viewerColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showFullLog by remember { mutableStateOf(false) }
    val s = syncState.logSummary
    val v = syncState.verificationState

    if (showFullLog) {
        FullLogDialog(syncState = syncState, onDismiss = { showFullLog = false })
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = viewerColors.background) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {

            // ── Title + step ───────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (syncState.isSyncing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = viewerColors.action)
                else Icon(Icons.Rounded.CheckCircle, null, tint = viewerColors.action, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (syncState.isComplete) "Sync Complete" else "Syncing…",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = viewerColors.textPrimary)
            }
            Spacer(Modifier.height(4.dp))
            Text(syncState.currentStep, fontSize = 12.sp, color = viewerColors.textSecondary)

            if (syncState.total > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (syncState.progress.toFloat() / syncState.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = viewerColors.action, trackColor = viewerColors.divider
                )
                Text("${syncState.progress} / ${syncState.total}", fontSize = 11.sp, color = viewerColors.textSecondary)
            } else if (syncState.isSyncing) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = viewerColors.action, trackColor = viewerColors.divider)
            }

            Spacer(Modifier.height(16.dp))

            // ── Step indicators ────────────────────────────────────────────────
            SyncStepRow("Messages & contacts parsed",
                done = s.messagesLoaded > 0 || syncState.isComplete, inProgress = syncState.isSyncing && s.messagesLoaded == 0)
            SyncStepRow("Profile photos synced (${s.avatarsSynced})",
                done = s.avatarsSynced > 0 || syncState.isComplete, inProgress = syncState.isSyncing && s.avatarsSynced == 0)
            SyncStepRow("Media paths resolved (${v.resolvedMedia}/${v.totalMedia})",
                done = syncState.isComplete, inProgress = syncState.isSyncing && v.resolvedMedia < v.totalMedia)

            Spacer(Modifier.height(14.dp))

            // ── Overview cards ────────────────────────────────────────────────
            if (s.messagesLoaded > 0) {
                Surface(shape = RoundedCornerShape(10.dp), color = viewerColors.surface,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("DATA OVERVIEW", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = viewerColors.textSecondary, letterSpacing = 1.sp)
                        Spacer(Modifier.height(2.dp))
                        SyncOverviewRow("Messages", "${s.messagesLoaded}", viewerColors.textPrimary)
                        SyncOverviewRow("Chats", "${s.chatsLoaded} (${s.groupsLoaded} groups)", viewerColors.textPrimary)
                        SyncOverviewRow("Contacts", "${s.contactsLoaded}", viewerColors.textPrimary)
                        SyncOverviewRow("Profile photos", "${s.avatarsSynced}", viewerColors.textPrimary)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (s.mediaTotal > 0) {
                Surface(shape = RoundedCornerShape(10.dp), color = viewerColors.surface,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("MEDIA (${s.mediaTotal} total)", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = viewerColors.textSecondary, letterSpacing = 1.sp)
                        Spacer(Modifier.height(2.dp))
                        SyncOverviewRow("In archive ✓", "${s.mediaInArchive}", viewerColors.success)
                        if (s.mediaSkippedByUser > 0)
                            SyncOverviewRow("Not extracted (your choice)", "${s.mediaSkippedByUser}", viewerColors.textSecondary)
                        if (s.mediaNeverDownloaded > 0)
                            SyncOverviewRow("Never downloaded in WhatsApp", "${s.mediaNeverDownloaded}", viewerColors.textSecondary)
                        if (s.mediaMissing > 0)
                            SyncOverviewRow("⚠ Missing (error)", "${s.mediaMissing}", viewerColors.warning)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (s.unusedDataItems.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(10.dp), color = viewerColors.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("IN ARCHIVE — NOT YET SHOWN IN VIEWER", fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, color = viewerColors.warning, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        s.unusedDataItems.take(4).forEach { item ->
                            Text("• $item", fontSize = 12.sp, color = viewerColors.textSecondary)
                        }
                        if (s.unusedDataItems.size > 4)
                            Text("• …and ${s.unusedDataItems.size - 4} more", fontSize = 12.sp, color = viewerColors.textSecondary)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Surface(
                shape = RoundedCornerShape(10.dp), color = viewerColors.surface,
                modifier = Modifier.fillMaxWidth().clickable { showFullLog = true }
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Full Sync Log", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = viewerColors.textPrimary)
                        Text("${syncState.logs.size} entries — tap to view", fontSize = 12.sp, color = viewerColors.textSecondary)
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = viewerColors.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun FullLogDialog(syncState: SyncState, onDismiss: () -> Unit) {
    val viewerColors = MaterialTheme.viewerColors
    val logListState = rememberLazyListState()
    val recoverLogs  = syncState.logSummary.smartResolveLogs

    // Scroll to bottom of regular log on new entries (only if no recovery logs to show first)
    LaunchedEffect(syncState.logs.size) {
        if (syncState.logs.isNotEmpty() && recoverLogs.isEmpty()) {
            logListState.animateScrollToItem(syncState.logs.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = viewerColors.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Full Sync Log", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = viewerColors.textPrimary)
                        val deletedCount  = recoverLogs.count { it.result == "DELETED" }
                        val recoveredCount = recoverLogs.count { it.result != "DELETED" }
                        val subtitle = buildString {
                            append("${syncState.logs.size} log entries")
                            if (recoveredCount > 0) append(" · $recoveredCount rescued")
                            if (deletedCount > 0)   append(" · $deletedCount deleted")
                        }
                        Text(subtitle, fontSize = 12.sp, color = when {
                            deletedCount > 0  -> viewerColors.warning
                            recoveredCount > 0 -> viewerColors.success
                            else              -> viewerColors.textSecondary
                        })
                    }
                    Icon(Icons.Rounded.Close, null, tint = viewerColors.textSecondary,
                        modifier = Modifier.size(22.dp).clickable { onDismiss() })
                }

                Spacer(Modifier.height(12.dp))

                // Legend
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LogLegendChip("[OK]",   viewerColors.success)
                    LogLegendChip("[SYNC]", viewerColors.action)
                    LogLegendChip("[WARN]", viewerColors.warning)
                    LogLegendChip("[SKIP]", viewerColors.textSecondary)
                    LogLegendChip("[INFO]", viewerColors.info)
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = viewerColors.divider)
                Spacer(Modifier.height(4.dp))

                LazyColumn(
                    state = logListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // ── Smart Recovery Logs section (shown first if present) ──
                    if (recoverLogs.isNotEmpty()) {
                        val deletedLogs   = recoverLogs.filter { it.result == "DELETED" }
                        val recoveredLogs = recoverLogs.filter { it.result != "DELETED" }
                        val byClass = recoveredLogs.groupBy { it.result }

                        item {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = viewerColors.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        "Smart Recovery Logs — ${recoverLogs.size} non-exact resolutions",
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        color = viewerColors.success
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    if (recoveredLogs.isNotEmpty()) {
                                        Text("Rescued via fallback: ${recoveredLogs.size}", fontSize = 11.sp, color = viewerColors.success)
                                        byClass["MOVED"]?.let     { Text("  • MOVED: ${it.size} — found at different folder path",  fontSize = 10.sp, color = viewerColors.success) }
                                        byClass["RENAMED"]?.let   { Text("  • RENAMED: ${it.size} — matched by pattern/prefix",     fontSize = 10.sp, color = viewerColors.success) }
                                        byClass["EXT_FIXED"]?.let { Text("  • EXT_FIXED: ${it.size} — extension added from mime",    fontSize = 10.sp, color = viewerColors.success) }
                                    }
                                    if (deletedLogs.isNotEmpty()) {
                                        Text("DELETED (all steps failed): ${deletedLogs.size}", fontSize = 11.sp, color = viewerColors.warning, modifier = Modifier.padding(top = 2.dp))
                                    }
                                    Text("Full step-by-step details below ↓", fontSize = 10.sp, color = viewerColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }

                        // Show DELETED items first, then recovered
                        items(deletedLogs + recoveredLogs) { entry ->
                            val isDeleted = entry.result == "DELETED"
                            val headerColor = if (isDeleted) viewerColors.warning else viewerColors.success
                            val bgColor = viewerColors.surface
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = bgColor,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    // Header row
                                    Row(horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            "[${entry.result}]  msgId: ${entry.messageId}",
                                            fontSize = 10.sp, color = headerColor,
                                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold
                                        )
                                    }
                                    // Original path
                                    Text(
                                        "data.json: ${entry.originalPath.ifEmpty { "<empty>" }}",
                                        fontSize = 10.sp, color = viewerColors.textPrimary,
                                        fontFamily = FontFamily.Monospace, lineHeight = 14.sp
                                    )
                                    // Resolved path if available
                                    if (entry.resolvedPath != null) {
                                        Text(
                                            "resolved:  ${entry.resolvedPath}",
                                            fontSize = 10.sp, color = viewerColors.success,
                                            fontFamily = FontFamily.Monospace, lineHeight = 14.sp
                                        )
                                    }
                                    // Steps
                                    Spacer(Modifier.height(3.dp))
                                    entry.steps.forEach { step ->
                                        val stepColor = when {
                                            "HIT"  in step -> viewerColors.success
                                            "MISS" in step -> viewerColors.warning
                                            "SKIP" in step -> viewerColors.textSecondary
                                            else           -> viewerColors.textSecondary
                                        }
                                        Text(
                                            step,
                                            fontSize = 9.5.sp, color = stepColor,
                                            fontFamily = FontFamily.Monospace, lineHeight = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            HorizontalDivider(color = viewerColors.divider, modifier = Modifier.padding(vertical = 8.dp))
                            Text("── Regular Sync Log ──", fontSize = 11.sp, color = viewerColors.textSecondary,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }

                    // ── Regular log entries ──
                    items(syncState.logs) { line ->
                        val color = when {
                            line.contains("[OK]")    -> viewerColors.success
                            line.contains("[WARN]")  -> viewerColors.warning
                            line.contains("[ERROR]") -> viewerColors.error
                            line.contains("[SKIP]")  -> viewerColors.textSecondary
                            line.contains("[CACHE]") -> viewerColors.info
                            line.contains("[SYNC]")  -> viewerColors.action
                            line.contains("[INFO]")  -> viewerColors.info
                            else -> viewerColors.textSecondary
                        }
                        Text(
                            text = line,
                            fontSize = 11.sp,
                            color = color,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLegendChip(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 10.sp, color = color,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

@Composable
private fun SyncOverviewRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.viewerColors.textSecondary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
private fun SyncStepRow(label: String, done: Boolean, inProgress: Boolean = false) {
    val viewerColors = MaterialTheme.viewerColors
    Row(modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            done       -> Icon(Icons.Rounded.CheckCircle, null, tint = viewerColors.action, modifier = Modifier.size(16.dp))
            inProgress -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = viewerColors.action)
            else       -> Icon(Icons.Rounded.RadioButtonUnchecked, null, tint = viewerColors.textSecondary, modifier = Modifier.size(16.dp))
        }
        Text(label, fontSize = 13.sp, color = if (done) viewerColors.textPrimary else viewerColors.textSecondary)
    }
}

// ── Search Results List ────────────────────────────────────────────────────────

@Composable
private fun SearchResultsList(
    filteredChatItems: List<ChatListUiModel>,
    searchQuery: String,
    matchedSnippets: Map<Long, String>,
    onChatClick: (Long) -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    val q           = searchQuery.lowercase()
    val nameMatched = remember(filteredChatItems, searchQuery) {
        filteredChatItems.filter { it.displayName.lowercase().contains(q) }
    }
    val msgMatched  = remember(filteredChatItems, matchedSnippets, searchQuery) {
        filteredChatItems.filter { !it.displayName.lowercase().contains(q) && it.chatId in matchedSnippets }
    }

    if (nameMatched.isEmpty() && msgMatched.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No results for \"$searchQuery\"", color = viewerColors.textSecondary)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (nameMatched.isNotEmpty()) {
            item(key = "header_chats") {
                Text("CHATS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = viewerColors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            items(nameMatched, key = { "n_${it.chatId}" }, contentType = { 0 }) { item ->
                val fallback = remember(item.chatId, viewerColors) {
                    viewerColors.avatarFallbackColors[(item.chatId % viewerColors.avatarFallbackColors.size).toInt().coerceAtLeast(0)]
                }
                ChatListItemContent(item, fallback, null, searchQuery) {
                    onChatClick(item.chatId)
                }
                HorizontalDivider(modifier = Modifier.padding(start = 80.dp), thickness = 0.5.dp, color = viewerColors.divider)
            }
        }
        if (msgMatched.isNotEmpty()) {
            item(key = "header_messages") {
                Text("MESSAGES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = viewerColors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            items(msgMatched, key = { "m_${it.chatId}" }, contentType = { 0 }) { item ->
                val fallback = remember(item.chatId, viewerColors) {
                    viewerColors.avatarFallbackColors[(item.chatId % viewerColors.avatarFallbackColors.size).toInt().coerceAtLeast(0)]
                }
                ChatListItemContent(item, fallback, matchedSnippets[item.chatId], searchQuery) {
                    onChatClick(item.chatId)
                }
                HorizontalDivider(modifier = Modifier.padding(start = 80.dp), thickness = 0.5.dp, color = viewerColors.divider)
            }
        }
    }
}

// ── Pure stateless chat row — no IO, no coroutines ────────────────────────────

@Composable
private fun ChatListItemContent(
    item: ChatListUiModel,
    avatarFallbackColor: Color,
    searchSnippet: String?,
    searchQuery: String,
    onClick: () -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    val displayName = item.displayName
    val avatarModel = rememberViewerImageModel(item.avatarPath)

    Row(
        modifier = Modifier.fillMaxWidth().background(viewerColors.background)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatarModel != null) {
            AsyncImage(
                model = avatarModel, contentDescription = null,
                placeholder = ColorPainter(avatarFallbackColor),
                error       = ColorPainter(avatarFallbackColor),
                modifier    = Modifier.size(52.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(avatarFallbackColor),
                contentAlignment = Alignment.Center) {
                Text(displayName.take(1).uppercase(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = viewerColors.fullScreenOnColor)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(displayName, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = viewerColors.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(item.timestampText, fontSize = 11.sp, color = viewerColors.textSecondary)
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (searchSnippet != null && searchQuery.isNotEmpty()) {
                val sq    = searchQuery.lowercase()
                val lower = searchSnippet.lowercase()
                val idx   = lower.indexOf(sq)
                val annotated = buildAnnotatedString {
                    if (idx >= 0) {
                        append(searchSnippet.substring(0, idx))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = viewerColors.action)) {
                            append(searchSnippet.substring(idx, minOf(idx + sq.length, searchSnippet.length)))
                        }
                        append(searchSnippet.substring(minOf(idx + sq.length, searchSnippet.length)))
                    } else append(searchSnippet)
                }
                Text(annotated, fontSize = 14.sp, color = viewerColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(
                    item.previewText.ifEmpty { "No messages" },
                    fontSize = 14.sp,
                    color = viewerColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
