package com.mazin.wasensai.ui.screens.viewer

import android.content.Intent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.mazin.wasensai.data.model.Message
import com.mazin.wasensai.ui.components.ChatWallpaper
import com.mazin.wasensai.ui.theme.viewerColors
import com.mazin.wasensai.utils.DateUtils
import com.mazin.wasensai.viewmodel.ViewerViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

private val EmptyViewerMediaAvailability = ViewerMediaAvailability(
    file = null,
    isAvailable = false,
    isStorageFull = false,
    isPreparingFromBackup = false,
    isSkipped = false,
    isNotDownloaded = false,
    isMissingFromBackup = false
)

private fun saveMediaToDownloads(context: android.content.Context, file: File): Boolean {
    return try {
        val dl    = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val waDir = File(dl, "WASensai").also { it.mkdirs() }
        val dest  = File(waDir, if (file.extension.lowercase() == "opus") file.nameWithoutExtension + ".ogg" else file.name)
        file.copyTo(dest, overwrite = true)
        android.media.MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
        true
    } catch (_: Exception) { false }
}

private fun openViewerDocument(
    context: android.content.Context,
    sourceFile: File,
    ext: String
) {
    try {
        val mime = when (ext) {
            "PDF" -> "application/pdf"
            "DOC", "DOCX" -> "application/msword"
            "XLS", "XLSX" -> "application/vnd.ms-excel"
            "PPT", "PPTX" -> "application/vnd.ms-powerpoint"
            "TXT" -> "text/plain"
            "JPG", "JPEG" -> "image/jpeg"
            "PNG" -> "image/png"
            "MP4" -> "video/mp4"
            "MP3" -> "audio/mpeg"
            "OGG", "OPUS" -> "audio/ogg"
            else -> "application/octet-stream"
        }
        val preferredExt = when (ext) {
            "DOC", "DOCX" -> ".docx"
            "XLS", "XLSX" -> ".xlsx"
            "PPT", "PPTX" -> ".pptx"
            "PDF" -> ".pdf"
            else -> if (sourceFile.extension.isNotEmpty()) ".${sourceFile.extension}" else ".doc"
        }
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val outputDir = File(downloadsDir, "WASensai").also { it.mkdirs() }
        val destination = File(outputDir, sourceFile.nameWithoutExtension + preferredExt)
        sourceFile.copyTo(destination, overwrite = true)
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(destination.absolutePath),
            null,
            null
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", destination),
                    mime
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    } catch (_: Exception) {
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chatId: Long,
    onBackClick: () -> Unit,
    onMediaClick: (Int) -> Unit,
    onGalleryClick: () -> Unit,
    onContactInfoClick: () -> Unit,
    viewModel: ViewerViewModel
) {
    val viewerColors = MaterialTheme.viewerColors
    val currentChatUiState by viewModel.currentChatUiState.collectAsStateWithLifecycle()
    val messages = currentChatUiState.messages
    val currentChatId = currentChatUiState.chatId
    val chatLoadState = currentChatUiState.loadState
    val chatRenderData = currentChatUiState.renderData
    val currentChatAvailability = currentChatUiState.availabilityByMessageId
    val inChatSearchActive  by viewModel.inChatSearchActive.collectAsStateWithLifecycle()
    val inChatSearchQuery   by viewModel.inChatSearchQuery.collectAsStateWithLifecycle()
    val inChatMatchIds      by viewModel.inChatMatchIds.collectAsStateWithLifecycle()
    val inChatMatchIndex    by viewModel.inChatMatchIndex.collectAsStateWithLifecycle()
    val coroutineScope   = rememberCoroutineScope()
    var selectedMessage  by remember { mutableStateOf<Message?>(null) }

    val chat = rememberViewerChat(viewModel, chatId)

    // BUG 14: reply-scroll state — set to a keyId to trigger scroll to that message
    var scrollToKeyId       by remember { mutableStateOf<String?>(null) }
    // BUG 14: highlighted message ID — briefly highlight after scroll (cleared after 1.5s)
    var highlightedMsgId    by remember { mutableLongStateOf(-1L) }
    var pendingRestoreIndex by remember { mutableIntStateOf(-1) }
    var pendingRestoreOffset by remember { mutableIntStateOf(0) }
    var pendingRestoreCount by remember { mutableIntStateOf(-1) }
    var lastMessageCount by remember(chatId) { mutableIntStateOf(0) }
    var videoThumbnailVersion by remember(chatId) { mutableIntStateOf(0) }
    var allowDeferredChatWork by remember(chatId) { mutableStateOf(false) }

    val displayName = remember(chat?.id, chat?.subject, chat?.jid, chat?.isGroup) {
        if (chat == null) {
            "Chat"
        } else if (chat.isGroup) {
            chat.subject.ifEmpty { "Group" }
        } else {
            viewModel.getContactName(chat.jid).ifEmpty { "Chat" }
        }
    }

    val chatJid = chat?.jid
    val avatarFile = rememberAvatarFile(viewModel, chatJid)
    val avatarPath = avatarFile?.absolutePath
    val avatarModel = rememberViewerImageModel(avatarPath)

    val availabilityByMessageId = currentChatAvailability
    val currentRenderData = chatRenderData?.takeIf { it.sourceMessageCount == messages.size }
    val itemsWithDates = currentRenderData?.timelineItems.orEmpty()
    val keyIdToItemsIndex = currentRenderData?.keyIdToItemsIndex.orEmpty()
    val initialTimelineIndex = remember(chatId, currentRenderData != null) {
        if (currentRenderData != null) currentRenderData.timelineItems.lastIndex.coerceAtLeast(0) else 0
    }
    val listState = remember(chatId, currentRenderData != null) {
        LazyListState(firstVisibleItemIndex = initialTimelineIndex)
    }
    val showScrollButton by remember { derivedStateOf { listState.canScrollForward } }
    val isListScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    val atTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val isAtBottom by remember {
        derivedStateOf {
            !listState.canScrollForward
        }
    }
    val showGalleryMenuItem by remember(chat?.isGroup, messages) {
        derivedStateOf {
            chat?.isGroup == true || messages.any { message ->
                message.messageType in listOf(1, 2, 3, 9, 13, 20) && message.mediaFilePath.isNotEmpty()
            }
        }
    }
    val isCurrentRouteChatReady = currentChatId == chatId &&
        chatLoadState == com.mazin.wasensai.viewmodel.ChatLoadState.Ready &&
        currentRenderData != null

    LaunchedEffect(chatId, isCurrentRouteChatReady) {
        allowDeferredChatWork = false
        if (isCurrentRouteChatReady) {
            kotlinx.coroutines.delay(380)
            allowDeferredChatWork = true
        }
    }

    LaunchedEffect(messages.size, isAtBottom, itemsWithDates.size) {
        if (pendingRestoreIndex >= 0) return@LaunchedEffect
        val messageCount = messages.size
        val hadMessages = lastMessageCount > 0
        val hasNewMessages = messageCount > lastMessageCount
        if (messageCount > 0 && hadMessages && hasNewMessages && isAtBottom) {
            listState.scrollToItem(itemsWithDates.lastIndex.coerceAtLeast(0))
        }
        lastMessageCount = messageCount
    }
    val visibleMessageIndex by remember(itemsWithDates, listState.firstVisibleItemIndex) {
        derivedStateOf {
            if (currentRenderData == null || currentRenderData.messageCountsByItemIndex.isEmpty()) {
                0
            } else {
                val itemIndex = listState.firstVisibleItemIndex
                    .coerceIn(0, currentRenderData.messageCountsByItemIndex.lastIndex)
                (currentRenderData.messageCountsByItemIndex[itemIndex] - 1).coerceAtLeast(0)
            }
        }
    }
    val lastPrefetchIndex = remember { mutableIntStateOf(-1) }

    // BUG 14: Scroll to quoted message.
    // Must be placed AFTER keyIdToItemsIndex and itemsWithDates are defined (forward references not allowed).
    // Triggers on scrollToKeyId change OR when messages.size changes (after ensureAllMessagesLoaded).
    LaunchedEffect(scrollToKeyId, messages.size) {
        val keyId = scrollToKeyId ?: return@LaunchedEffect
        val idx = keyIdToItemsIndex[keyId]
        if (idx != null) {
            listState.animateScrollToItem(idx.coerceAtLeast(0))
            val targetMsg = itemsWithDates.getOrNull(idx)
            if (targetMsg is ChatTimelineMessageItem) highlightedMsgId = targetMsg.message.id
            scrollToKeyId = null
        } else {
            // Quoted message not in current page — load all messages then this effect re-fires
            viewModel.ensureAllMessagesLoaded()
        }
    }

    // BUG 14: Clear highlight after 1.5 seconds
    LaunchedEffect(highlightedMsgId) {
        if (highlightedMsgId >= 0L) {
            kotlinx.coroutines.delay(1500)
            highlightedMsgId = -1L
        }
    }

    // BUG 3: scroll to current in-chat search match
    LaunchedEffect(inChatMatchIds, inChatMatchIndex) {
        val targetId = viewModel.currentInChatMatchId() ?: return@LaunchedEffect
        val idx = currentRenderData?.itemIndexByMessageId?.get(targetId) ?: -1
        if (idx >= 0) listState.animateScrollToItem(idx)
    }
    LaunchedEffect(listState, visibleMessageIndex, allowDeferredChatWork) {
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { isScrolling ->
                if (!allowDeferredChatWork || isScrolling) return@collectLatest
                kotlinx.coroutines.delay(320)
                if (!allowDeferredChatWork) return@collectLatest
                val i = visibleMessageIndex
                if (kotlin.math.abs(i - lastPrefetchIndex.intValue) >= 5) {
                    lastPrefetchIndex.intValue = i
                    viewModel.prefetchAround(i)
                }
            }
    }
    LaunchedEffect(listState, itemsWithDates, availabilityByMessageId, isCurrentRouteChatReady, allowDeferredChatWork) {
        if (!isCurrentRouteChatReady) return@LaunchedEffect
        snapshotFlow {
            listState.isScrollInProgress to listState.layoutInfo.visibleItemsInfo.map { it.index }
        }.collectLatest { (isScrolling, visibleItemIndices) ->
            if (!allowDeferredChatWork || isScrolling || visibleItemIndices.isEmpty()) return@collectLatest
            kotlinx.coroutines.delay(240)
            if (!allowDeferredChatWork || listState.isScrollInProgress) return@collectLatest
            val lastVisibleIndex = visibleItemIndices.maxOrNull() ?: return@collectLatest
            val candidateMessages = buildList {
                visibleItemIndices.forEach { itemIndex ->
                    val item = itemsWithDates.getOrNull(itemIndex)
                    if (item is ChatTimelineMessageItem) add(item.message)
                }
                for (offset in 1..4) {
                    val item = itemsWithDates.getOrNull(lastVisibleIndex + offset)
                    if (item is ChatTimelineMessageItem) add(item.message)
                }
            }
            if (
                candidateMessages.isNotEmpty() &&
                warmViewerVideoThumbnails(candidateMessages, availabilityByMessageId)
            ) {
                videoThumbnailVersion += 1
            }
        }
    }
    LaunchedEffect(listState, pendingRestoreIndex, itemsWithDates.size) {
        snapshotFlow { listState.isScrollInProgress to atTop }
            .collectLatest { (isScrolling, atTopNow) ->
                if (isScrolling || !atTopNow || pendingRestoreIndex >= 0 || listState.layoutInfo.totalItemsCount <= 0) {
                    return@collectLatest
                }
                kotlinx.coroutines.delay(120)
                if (!listState.isScrollInProgress && atTop) {
                    pendingRestoreIndex = listState.firstVisibleItemIndex
                    pendingRestoreOffset = listState.firstVisibleItemScrollOffset
                    pendingRestoreCount = itemsWithDates.size
                    viewModel.loadMoreMessages()
                }
            }
    }

    LaunchedEffect(itemsWithDates.size, pendingRestoreIndex, pendingRestoreCount) {
        if (pendingRestoreIndex < 0 || pendingRestoreCount < 0) return@LaunchedEffect
        val addedCount = itemsWithDates.size - pendingRestoreCount
        if (addedCount > 0) {
            listState.scrollToItem(
                index = (pendingRestoreIndex + addedCount).coerceAtMost(itemsWithDates.lastIndex),
                scrollOffset = pendingRestoreOffset
            )
        }
        pendingRestoreIndex = -1
        pendingRestoreOffset = 0
        pendingRestoreCount = -1
    }

    Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
            if (inChatSearchActive) {
                // BUG 3: In-chat search bar
                val focusRequester = remember { FocusRequester() }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = viewerColors.header,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                            .statusBarsPadding().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.deactivateInChatSearch() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = viewerColors.textPrimary)
                        }
                        BasicTextField(
                            value = inChatSearchQuery,
                            onValueChange = { viewModel.updateInChatSearchQuery(it) },
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 16.sp, color = viewerColors.textPrimary),
                            decorationBox = { inner ->
                                if (inChatSearchQuery.isEmpty())
                                    Text("Search messages…", color = viewerColors.textSecondary, fontSize = 16.sp)
                                inner()
                            }
                        )
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        val countText = when {
                            inChatMatchIds.isEmpty() && inChatSearchQuery.isNotEmpty() -> "No results"
                            inChatMatchIds.isNotEmpty() -> "${inChatMatchIndex + 1} of ${inChatMatchIds.size}"
                            else -> ""
                        }
                        if (countText.isNotEmpty()) {
                            Text(countText, fontSize = 12.sp, color = viewerColors.textSecondary,
                                modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        IconButton(
                            onClick = { viewModel.navigateInChatSearch(forward = false) },
                            enabled = inChatMatchIds.size > 1
                        ) { Icon(Icons.Rounded.KeyboardArrowUp, null, tint = viewerColors.textPrimary) }
                        IconButton(
                            onClick = { viewModel.navigateInChatSearch(forward = true) },
                            enabled = inChatMatchIds.size > 1
                        ) { Icon(Icons.Rounded.KeyboardArrowDown, null, tint = viewerColors.textPrimary) }
                    }
                }
            } else if (selectedMessage != null) {
                val selMsg   = selectedMessage!!
                val hasMedia = selMsg.mediaFilePath.isNotEmpty() || (selMsg.messageType == 9 && selMsg.textData.isNotEmpty())
                TopAppBar(
                    title = { Text("1", color = viewerColors.textPrimary, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = { selectedMessage = null }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = viewerColors.textPrimary)
                        }
                    },
                    actions = {
                        if (selMsg.textData.isNotEmpty()) {
                            val ctx = LocalContext.current
                            IconButton(onClick = {
                                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("message", selMsg.textData))
                                android.widget.Toast.makeText(ctx, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                                selectedMessage = null
                            }) { Icon(Icons.Rounded.ContentCopy, null, tint = viewerColors.textPrimary) }
                        }
                        if (hasMedia) {
                            val ctx = LocalContext.current
                            IconButton(onClick = {
                                val f = viewModel.getMediaFileForMessage(selMsg.id)?.takeIf(::isValidViewerFile)
                                android.widget.Toast.makeText(ctx,
                                    if (f != null) {
                                        if (saveMediaToDownloads(ctx, f)) "Saved to Downloads/WASensai" else "Save failed"
                                    } else "File not available in export",
                                    android.widget.Toast.LENGTH_SHORT).show()
                                selectedMessage = null
                            }) { Icon(Icons.Rounded.Download, null, tint = viewerColors.textPrimary) }
                        }
                        var showInfo by remember { mutableStateOf(false) }
                        IconButton(onClick = { showInfo = true }) { Icon(Icons.Rounded.Info, null, tint = viewerColors.textPrimary) }
                        if (showInfo) {
                            val type = when (selMsg.messageType) { 1->"Image";2->"Audio";3->"Video";9->"Document";5->"Location";20->"Sticker";else->"Text" }
                            val from = if (selMsg.fromMe == 1) {
                                "You"
                            } else {
                                val senderJid = selMsg.senderJid.ifEmpty { selMsg.chatJid }
                                selMsg.senderName.ifEmpty { viewModel.getContactName(senderJid) }
                            }
                            AlertDialog(
                                onDismissRequest = { showInfo = false; selectedMessage = null },
                                containerColor = viewerColors.surface,
                                title = { Text("Message Info", color = viewerColors.textPrimary, fontWeight = FontWeight.Bold) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        InfoRow("Type", type); InfoRow("From", from)
                                        InfoRow("Time", DateUtils.formatMessageTimestamp(selMsg.timestamp))
                                        if (selMsg.textData.isNotEmpty()) InfoRow("Content", selMsg.textData)
                                        if (selMsg.mediaFilePath.isNotEmpty()) InfoRow("File", selMsg.mediaFilePath.substringAfterLast("/"))
                                    }
                                },
                                confirmButton = { TextButton(onClick = { showInfo = false; selectedMessage = null }) { Text("Close", color = viewerColors.action) } }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = viewerColors.header)
                )
            } else {
                // Normal top bar
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onContactInfoClick() }) {
                            if (avatarModel != null) {
                                AsyncImage(model = avatarModel, contentDescription = null,
                                    placeholder = ColorPainter(viewerColors.placeholder), error = ColorPainter(viewerColors.placeholder),
                                    modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            } else {
                                Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = viewerColors.placeholder) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(displayName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = viewerColors.textPrimary)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(displayName, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, color = viewerColors.textPrimary)
                                if (chat?.isGroup == true) Text("Group · tap for info", style = MaterialTheme.typography.labelSmall, color = viewerColors.textSecondary)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = viewerColors.textPrimary) }
                    },
                    actions = {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = viewerColors.textPrimary) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(viewerColors.surface)) {
                            DropdownMenuItem(text = { Text("Contact info", color = viewerColors.textPrimary, fontSize = 15.sp) }, onClick = { showMenu = false; onContactInfoClick() })
                            if (chat?.isGroup == true) {
                                DropdownMenuItem(text = { Text("Group media", color = viewerColors.textPrimary, fontSize = 15.sp) }, onClick = { showMenu = false; onGalleryClick() })
                            } else if (showGalleryMenuItem) {
                                DropdownMenuItem(text = { Text("Media, links and docs", color = viewerColors.textPrimary, fontSize = 15.sp) }, onClick = { showMenu = false; onGalleryClick() })
                            }
                            DropdownMenuItem(text = { Text("Search", color = viewerColors.textPrimary, fontSize = 15.sp) }, onClick = { showMenu = false; viewModel.activateInChatSearch() })
                            DropdownMenuItem(text = { Text("Mute notifications", color = viewerColors.textPrimary, fontSize = 15.sp) }, onClick = { showMenu = false })
                            DropdownMenuItem(text = { Text("Disappearing messages", color = viewerColors.textPrimary, fontSize = 15.sp) }, onClick = { showMenu = false })
                            DropdownMenuItem(text = { Text("Chat theme", color = viewerColors.textPrimary, fontSize = 15.sp) }, onClick = { showMenu = false })
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = viewerColors.header)
                )
            }
                },
                bottomBar = { ChatInputBar() },
                containerColor = Color.Transparent
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    ChatWallpaper(modifier = Modifier.fillMaxSize())

                    when {
                        isCurrentRouteChatReady -> {
                            LazyColumn(state = listState,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)) {
                                items(
                                    items = itemsWithDates,
                                    key = { item -> item.stableKey },
                                    contentType = { item -> item.contentType }
                                ) { item ->
                                    when (item) {
                                        is ChatTimelineStartItem -> ChatStartHeader()
                                        is ChatTimelineDateItem -> DateSeparator(item.label)
                                        is ChatTimelineMessageItem -> {
                                            val message = item.message
                                            MessageBubbleItem(
                                                message = message,
                                                isGroup          = chat?.isGroup == true,
                                                uiMeta = item.uiMeta,
                                                mediaAvailability = availabilityByMessageId[message.id] ?: EmptyViewerMediaAvailability,
                                                videoThumbnailVersion = videoThumbnailVersion,
                                                showAvatarSpace = chat?.isGroup == true && message.fromMe == 0,
                                                isSelected = selectedMessage?.id == message.id,
                                                isHighlighted = highlightedMsgId == message.id,
                                                onMediaClick = { if (item.mediaIndex >= 0) onMediaClick(item.mediaIndex) },
                                                onLongPress = { selectedMessage = message },
                                                onTap = { if (selectedMessage != null) selectedMessage = null },
                                                // BUG 14: set scrollToKeyId — LaunchedEffect handles the scroll
                                                onQuotedClick = { keyId -> scrollToKeyId = keyId }
                                            )
                                        }
                                    }
                                }
                            }

                            if (showScrollButton) {
                                Surface(shape = CircleShape, color = viewerColors.surface, shadowElevation = 4.dp,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp).size(40.dp)
                                        .clickable { coroutineScope.launch { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) } }) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = viewerColors.textPrimary, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                        else -> {
                            Box(modifier = Modifier.fillMaxSize().background(viewerColors.chatBackground.copy(alpha = 0.88f)), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = viewerColors.action, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar() {
    val viewerColors = MaterialTheme.viewerColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(viewerColors.footer)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = viewerColors.inputBackground,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.EmojiEmotions, null, tint = viewerColors.icon, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Message", color = viewerColors.textSecondary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.AttachFile, null, tint = viewerColors.icon, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Rounded.CameraAlt, null, tint = viewerColors.icon, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Surface(shape = CircleShape, color = viewerColors.action, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Mic, null, tint = viewerColors.fullScreenOnColor, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun DateSeparator(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.viewerColors.surface.copy(alpha = 0.92f)) {
            Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.viewerColors.textSecondary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleItem(
    message: Message,
    isGroup: Boolean,
    uiMeta: ChatMessageUiMeta,
    mediaAvailability: ViewerMediaAvailability,
    videoThumbnailVersion: Int,
    showAvatarSpace: Boolean,
    isSelected: Boolean,
    isHighlighted: Boolean,    // BUG 14: briefly highlighted after reply-scroll
    onMediaClick: () -> Unit,
    onLongPress: () -> Unit,
    onTap: () -> Unit,
    onQuotedClick: (keyId: String) -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    val isMe        = message.fromMe == 1
    val bubbleColor = if (isMe) viewerColors.bubblePrimary else viewerColors.bubbleSecondary
    val availability = mediaAvailability

    // BUG 14: highlight color shown briefly after scroll-to-reply
    val bgColor = when {
        isHighlighted -> viewerColors.highlight.copy(alpha = 0.35f)
        isSelected    -> viewerColors.fullScreenOnColor.copy(alpha = 0.1f)
        else          -> Color.Transparent
    }

    Box(modifier = Modifier.fillMaxWidth()
        .background(bgColor)
        .combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onTap() },
            onLongClick = { onLongPress() }
        )) {

        if (message.isSystem) {
            SystemMessageRow(message.textData)
            return@Box
        }

        // Skip truly empty/invisible messages — but never skip deleted or view-once (they have placeholders)
        if (message.messageType == 11) return@Box
        if (message.textData.isEmpty() && message.mediaFilePath.isEmpty() &&
            !message.isDeleted && !message.deletedForEveryone &&
            message.messageType !in listOf(1, 2, 3, 5, 9, 20, 84, 85)) return@Box

        // BUG 2: Deleted message placeholder
        if (message.isDeleted || message.deletedForEveryone || message.messageType == 15) {
            DeletedMessageBubble(isMe = isMe, bubbleColor = bubbleColor, timestampText = uiMeta.timestampText)
            return@Box
        }

        // BUG 2: View-once placeholder (type 84 = photo, 85 = video)
        if (message.messageType == 84 || message.messageType == 85) {
            ViewOnceExpiredBubble(
                isMe = isMe,
                bubbleColor = bubbleColor,
                isVideo = message.messageType == 85,
                timestampText = uiMeta.timestampText
            )
            return@Box
        }

        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).padding(
                start = if (showAvatarSpace) 4.dp else if (isMe) 56.dp else 8.dp,
                end   = if (isMe) 8.dp else 56.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {

            if (showAvatarSpace) {
                SenderAvatarSlot(
                    senderAvatarPath = uiMeta.senderAvatarPath,
                    senderName = uiMeta.senderName
                )
            }

            BubbleShell(isMe, bubbleColor) {
                // Show sender name in group bubbles — for received: contact name; for sent: "You"
                if (isGroup && uiMeta.senderName.isNotEmpty()) {
                    Text(uiMeta.senderName, style = MaterialTheme.typography.labelSmall,
                        color = if (isMe) viewerColors.textSecondary else viewerColors.action,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.height(2.dp))
                }

                if (message.quotedKeyId.isNotEmpty()) {
                    QuotedMessageBlock(
                        quotedSenderDisplay = uiMeta.quotedSenderDisplay,
                        quotedPreviewText = uiMeta.quotedPreviewText,
                        onClick = { onQuotedClick(message.quotedKeyId) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // All reads from cache — no produceState, no IO in composition
                when (message.messageType) {
                    1 -> ImageMessageContent(
                        mediaAvailability = availability,
                        captionText = uiMeta.mediaCaptionText,
                        onMediaClick = onMediaClick,
                        onLongPress = onLongPress
                    )
                    3 -> VideoMessageContent(
                        mediaAvailability = availability,
                        captionText = uiMeta.mediaCaptionText,
                        thumbnailVersion = videoThumbnailVersion,
                        onMediaClick = onMediaClick
                    )
                    2 -> AudioMessageContent(
                        mediaAvailability = availability,
                        mediaSize = message.mediaSize,
                        isMe = isMe,
                        audioAvatarPath = uiMeta.audioAvatarPath
                    )
                    9 -> DocumentMessageContent(
                        mediaAvailability = availability,
                        documentFileName = uiMeta.documentFileName,
                        documentExt = uiMeta.documentExt,
                        documentSizeText = uiMeta.documentSizeText,
                        onLongPress = onLongPress
                    )
                    5 -> LocationMessageContent(uiMeta.locationText)
                    20 -> StickerMessageContent(availability)
                    else -> TextMessageContent(uiMeta.messageText)
                }

                if (message.editVersion > 0 && !message.isDeleted) Text("Edited", style = MaterialTheme.typography.labelSmall, color = viewerColors.textSecondary)
                // BUG 6: Reactions — shown inside bubble above timestamp
                if (uiMeta.reactionChips.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                        uiMeta.reactionChips.forEach { chip ->
                            Surface(shape = RoundedCornerShape(10.dp), color = viewerColors.surface) {
                                Text(
                                    text = chip,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                TimestampText(uiMeta.timestampText, isMe)
            }
        }
    }
}

// BUG 9: Chat start sentinel — shown at top of every chat
@Composable
private fun SenderAvatarSlot(
    senderAvatarPath: String?,
    senderName: String
) {
    val viewerColors = MaterialTheme.viewerColors
    Box(
        modifier = Modifier.width(36.dp).padding(end = 4.dp, top = 2.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        val senderAvatarModel = rememberViewerImageModel(senderAvatarPath)
        if (senderAvatarModel != null) {
            AsyncImage(
                model = senderAvatarModel,
                contentDescription = null,
                modifier = Modifier.size(28.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(viewerColors.placeholder),
                error = ColorPainter(viewerColors.placeholder)
            )
        } else if (senderName.isNotEmpty()) {
            Box(
                modifier = Modifier.size(28.dp).background(viewerColors.placeholder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    senderName.take(1).uppercase(),
                    color = viewerColors.fullScreenOnColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun QuotedMessageBlock(
    quotedSenderDisplay: String,
    quotedPreviewText: String,
    onClick: () -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = viewerColors.surface,
        modifier = Modifier.fillMaxWidth().clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(viewerColors.action))
            Column(modifier = Modifier.padding(8.dp)) {
                if (quotedSenderDisplay.isNotEmpty()) {
                    Text(
                        quotedSenderDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        color = viewerColors.action,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    quotedPreviewText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = viewerColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun SystemMessageRow(text: String) {
    if (text.isEmpty()) return
    val viewerColors = MaterialTheme.viewerColors
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(10.dp), color = viewerColors.surface.copy(alpha = 0.92f)) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = viewerColors.textSecondary
            )
        }
    }
}

@Composable
private fun DeletedMessageBubble(
    isMe: Boolean,
    bubbleColor: Color,
    timestampText: String
) {
    val viewerColors = MaterialTheme.viewerColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        BubbleShell(isMe, bubbleColor) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Block, null, tint = viewerColors.textSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "This message was deleted",
                    style = MaterialTheme.typography.bodyMedium,
                    color = viewerColors.textSecondary,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp
                )
            }
            TimestampText(timestampText, isMe)
        }
    }
}

@Composable
private fun ViewOnceExpiredBubble(
    isMe: Boolean,
    bubbleColor: Color,
    isVideo: Boolean,
    timestampText: String
) {
    val viewerColors = MaterialTheme.viewerColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        BubbleShell(isMe, bubbleColor) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .background(viewerColors.fullScreenBackground.copy(alpha = 0.06f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.LockClock, null, tint = viewerColors.textSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isVideo) "View once video (expired)" else "View once photo (expired)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = viewerColors.textSecondary,
                    fontSize = 14.sp
                )
            }
            TimestampText(timestampText, isMe)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageMessageContent(
    mediaAvailability: ViewerMediaAvailability,
    captionText: String,
    onMediaClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    if (mediaAvailability.isAvailable) {
        AsyncImage(
            model = rememberViewerImageModel(mediaAvailability.file),
            contentDescription = null,
            placeholder = ColorPainter(viewerColors.placeholder),
            error = ColorPainter(viewerColors.placeholder),
            modifier = Modifier.widthIn(max = 240.dp).heightIn(max = 240.dp)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onMediaClick,
                    onLongClick = onLongPress
                ),
            contentScale = ContentScale.Crop
        )
    } else {
        SkippedPlaceholder(mediaUnavailableLabel("Image", mediaAvailability))
    }
    if (captionText.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(captionText, style = MaterialTheme.typography.bodyMedium, color = viewerColors.textPrimary)
    }
}

@Composable
private fun VideoMessageContent(
    mediaAvailability: ViewerMediaAvailability,
    captionText: String,
    thumbnailVersion: Int,
    onMediaClick: () -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    if (mediaAvailability.isAvailable) {
        VideoBubble(
            mediaFile = mediaAvailability.file,
            onMediaClick = onMediaClick,
            thumbnailVersion = thumbnailVersion
        )
    } else {
        SkippedPlaceholder(mediaUnavailableLabel("Video", mediaAvailability))
    }
    if (captionText.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(captionText, style = MaterialTheme.typography.bodyMedium, color = viewerColors.textPrimary)
    }
}

@Composable
private fun AudioMessageContent(
    mediaAvailability: ViewerMediaAvailability,
    mediaSize: Long,
    isMe: Boolean,
    audioAvatarPath: String?
) {
    if (mediaAvailability.isAvailable) {
        AudioBubble(
            mediaFile = mediaAvailability.file,
            mediaSize = mediaSize,
            isMe = isMe,
            avatarPath = audioAvatarPath
        )
    } else {
        SkippedPlaceholder(mediaUnavailableLabel("Audio", mediaAvailability))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentMessageContent(
    mediaAvailability: ViewerMediaAvailability,
    documentFileName: String,
    documentExt: String,
    documentSizeText: String,
    onLongPress: () -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    val context = LocalContext.current
    val extColor = when (documentExt) {
        "PDF" -> viewerColors.error
        "DOC", "DOCX" -> viewerColors.info
        "XLS", "XLSX" -> viewerColors.success
        "PPT", "PPTX" -> viewerColors.warning
        else -> viewerColors.icon
    }
    val statusLabel = mediaUnavailableLabel("Document", mediaAvailability)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(viewerColors.fullScreenBackground.copy(alpha = if (mediaAvailability.isNotDownloaded) 0.1f else 0.2f))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = onLongPress,
                onClick = {
                    val file = mediaAvailability.file ?: return@combinedClickable
                    if (!isValidViewerFile(file)) return@combinedClickable
                    openViewerDocument(context, file, documentExt)
                }
            )
            .padding(10.dp)
    ) {
        Surface(shape = RoundedCornerShape(8.dp), color = extColor, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(documentExt.take(4), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = viewerColors.fullScreenOnColor)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                documentFileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (mediaAvailability.isNotDownloaded) viewerColors.textSecondary else viewerColors.textPrimary
            )
            if (documentSizeText.isNotEmpty()) {
                Text(documentSizeText, style = MaterialTheme.typography.labelSmall, color = viewerColors.textSecondary)
            }
            if (!mediaAvailability.isAvailable) {
                val statusColor = if (mediaAvailability.isStorageFull || mediaAvailability.isSkipped || mediaAvailability.isMissingFromBackup) {
                    viewerColors.error.copy(0.7f)
                } else {
                    viewerColors.textSecondary
                }
                Text(statusLabel, fontSize = 10.sp, color = statusColor)
            }
        }
        when {
            mediaAvailability.isAvailable -> Icon(
                Icons.AutoMirrored.Rounded.OpenInNew,
                null,
                tint = viewerColors.fullScreenOnColor.copy(0.5f),
                modifier = Modifier.size(20.dp)
            )
            mediaAvailability.isStorageFull || mediaAvailability.isSkipped || mediaAvailability.isMissingFromBackup -> Icon(
                Icons.Rounded.Block,
                null,
                tint = viewerColors.error.copy(0.5f),
                modifier = Modifier.size(20.dp)
            )
            else -> Icon(
                Icons.Rounded.CloudOff,
                null,
                tint = viewerColors.textSecondary.copy(0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun LocationMessageContent(locationText: String) {
    val viewerColors = MaterialTheme.viewerColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(viewerColors.fullScreenBackground.copy(0.2f))
            .padding(10.dp)
    ) {
        Icon(Icons.Rounded.LocationOn, null, tint = viewerColors.action, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text("Location", style = MaterialTheme.typography.bodyMedium, color = viewerColors.fullScreenOnColor, fontWeight = FontWeight.Medium)
            Text(locationText, style = MaterialTheme.typography.labelSmall, color = viewerColors.fullScreenOnColor.copy(0.6f))
        }
    }
}

@Composable
private fun StickerMessageContent(mediaAvailability: ViewerMediaAvailability) {
    val viewerColors = MaterialTheme.viewerColors
    if (mediaAvailability.isAvailable) {
        AsyncImage(
            model = rememberViewerImageModel(mediaAvailability.file),
            contentDescription = null,
            placeholder = ColorPainter(Color.Transparent),
            modifier = Modifier.size(120.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.EmojiEmotions, null, tint = viewerColors.placeholder, modifier = Modifier.size(64.dp))
        }
    }
}

@Composable
private fun TextMessageContent(messageText: androidx.compose.ui.text.AnnotatedString?) {
    val viewerColors = MaterialTheme.viewerColors
    if (messageText != null) {
        Text(
            text = messageText,
            style = MaterialTheme.typography.bodyMedium,
            color = viewerColors.textPrimary,
            fontSize = 15.sp
        )
    }
}

@Composable private fun ChatStartHeader() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.viewerColors.surface.copy(alpha = 0.88f)) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.viewerColors.action, modifier = Modifier.size(12.dp))
                Text("Messages are end-to-end encrypted",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.viewerColors.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable private fun SkippedPlaceholder(label: String) {
    val viewerColors = MaterialTheme.viewerColors
    Row(modifier = Modifier.widthIn(min = 160.dp, max = 240.dp).clip(RoundedCornerShape(8.dp)).background(viewerColors.placeholder).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Block, null, tint = viewerColors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, color = viewerColors.textSecondary, fontStyle = FontStyle.Italic)
    }
}

@Composable private fun ImagePlaceholder(onClick: () -> Unit) {
    val viewerColors = MaterialTheme.viewerColors
    Box(modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(8.dp)).background(viewerColors.placeholder).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Image, null, modifier = Modifier.size(48.dp), tint = viewerColors.fullScreenOnColor.copy(0.3f))
    }
}

@Composable private fun ColumnScope.TimestampText(ts: String, isMe: Boolean) {
    val viewerColors = MaterialTheme.viewerColors
    Row(modifier = Modifier.align(Alignment.End).padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(ts, fontSize = 11.sp, color = viewerColors.textSecondary, style = MaterialTheme.typography.labelSmall)
        if (isMe) { Spacer(Modifier.width(2.dp)); Icon(Icons.Rounded.DoneAll, null, modifier = Modifier.size(15.dp), tint = viewerColors.seen) }
    }
}

@Composable private fun BubbleShell(isMe: Boolean, bubbleColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.wrapContentWidth().widthIn(min = 72.dp, max = 260.dp)
        .background(color = bubbleColor, shape = RoundedCornerShape(
            topStart = if (isMe) 16.dp else 4.dp, topEnd = if (isMe) 4.dp else 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
        .padding(horizontal = 10.dp, vertical = 6.dp)) { content() }
}

@Composable private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.viewerColors.textSecondary, style = MaterialTheme.typography.labelSmall)
        Text(value, fontSize = 13.sp, color = MaterialTheme.viewerColors.textPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun VideoBubble(
    mediaFile: File?,
    onMediaClick: () -> Unit,
    thumbnailVersion: Int
) {
    val viewerColors = MaterialTheme.viewerColors
    var showPlayer by remember { mutableStateOf(false) }
    val mediaPath = mediaFile?.absolutePath
    val thumbnail = remember(mediaPath, thumbnailVersion) {
        mediaPath?.let(ViewerVideoThumbnailCache::get)
    }
    Box(modifier = Modifier.widthIn(max = 260.dp).height(180.dp).clip(RoundedCornerShape(8.dp)).background(viewerColors.fullScreenBackground)
        .combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { if (isValidViewerFile(mediaFile)) showPlayer = true },
            onLongClick = {}
        ),
        contentAlignment = Alignment.Center) {
        if (thumbnail != null) {
            Image(bitmap = thumbnail.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(modifier = Modifier.fillMaxSize().background(viewerColors.surfaceVariant))
        }
        Box(modifier = Modifier.size(52.dp).background(viewerColors.fullScreenBackground.copy(0.6f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(34.dp), tint = viewerColors.fullScreenOnColor)
        }
    }
    if (showPlayer && isValidViewerFile(mediaFile)) VideoPlayerDialog(mediaFile = mediaFile!!, onDismiss = { showPlayer = false })
}

@Composable private fun VideoPlayerDialog(mediaFile: File, onDismiss: () -> Unit) {
    val viewerColors = MaterialTheme.viewerColors
    val context = LocalContext.current
    var isPlaying   by remember { mutableStateOf(true) }
    var positionMs  by remember { mutableLongStateOf(0L) }
    var durationMs  by remember { mutableLongStateOf(0L) }
    var isSeeking   by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(mediaFile))); prepare(); playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(s: Int) { if (s == Player.STATE_READY) durationMs = duration.coerceAtLeast(0L); if (s == Player.STATE_ENDED) { isPlaying = false; seekTo(0) } }
                override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            })
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(player, isSeeking, isPlaying) {
        if (!isSeeking && !player.isPlaying) return@LaunchedEffect
        while (isActive && (isSeeking || player.isPlaying)) {
            if (!isSeeking) {
                val dur = player.duration.takeIf { it > 0 } ?: 0L
                val pos = player.currentPosition.coerceAtLeast(0L)
                if (dur > 0) { durationMs = dur; positionMs = pos; seekProgress = pos.toFloat() / dur }
            }
            kotlinx.coroutines.delay(200)
        }
    }
    fun fmtMs(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)) {
        Box(modifier = Modifier.fillMaxSize().background(viewerColors.fullScreenBackground), contentAlignment = Alignment.Center) {
            AndroidView(factory = { ctx -> androidx.media3.ui.PlayerView(ctx).apply { this.player = player; useController = false; layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT) } }, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { if (player.isPlaying) player.pause() else player.play() })
            Row(modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(viewerColors.fullScreenBackground.copy(0.7f), Color.Transparent))).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = viewerColors.fullScreenOnColor) }
            }
            androidx.compose.animation.AnimatedVisibility(!isPlaying, enter = androidx.compose.animation.fadeIn(tween(200)), exit = androidx.compose.animation.fadeOut(tween(200))) {
                Box(modifier = Modifier.size(72.dp).background(viewerColors.fullScreenBackground.copy(0.55f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(46.dp), tint = viewerColors.fullScreenOnColor)
                }
            }
            Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, viewerColors.fullScreenBackground.copy(0.8f)))).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Slider(value = seekProgress,
                    onValueChange = { v -> isSeeking = true; seekProgress = v; positionMs = (v * durationMs).toLong() },
                    onValueChangeFinished = { val dur = player.duration.takeIf { it > 0 } ?: run { isSeeking = false; return@Slider }; player.seekTo((seekProgress * dur).toLong()); isSeeking = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = viewerColors.fullScreenOnColor, activeTrackColor = viewerColors.fullScreenOnColor, inactiveTrackColor = viewerColors.fullScreenOnColor.copy(0.3f)))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtMs(positionMs), color = viewerColors.fullScreenOnColor, fontSize = 12.sp)
                    Text(fmtMs(durationMs), color = viewerColors.fullScreenOnColor.copy(0.7f), fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(52.dp)) {
                        Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = viewerColors.fullScreenOnColor, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }
}

@Composable private fun AudioBubble(mediaFile: File?, mediaSize: Long, isMe: Boolean, avatarPath: String?) {
    val viewerColors = MaterialTheme.viewerColors
    val context    = LocalContext.current
    var isPlaying  by remember { mutableStateOf(false) }
    var progress   by remember { mutableFloatStateOf(0f) }
    var posMs      by remember { mutableLongStateOf(0L) }
    var durMs      by remember { mutableLongStateOf(0L) }
    var speedIndex by remember { mutableIntStateOf(0) }
    val speeds      = listOf(1f, 1.5f, 2f)
    val speedLabels = listOf("1×", "1.5×", "2×")
    var player by remember(mediaFile?.absolutePath) { mutableStateOf<ExoPlayer?>(null) }
    fun ensurePlayer(): ExoPlayer? {
        val existing = player
        if (existing != null) return existing
        if (!isValidViewerFile(mediaFile)) return null
        return ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true
            )
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(mediaFile)))
            prepare()
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        durMs = duration.takeIf { it > 0 } ?: durMs
                    } else if (state == Player.STATE_ENDED) {
                        isPlaying = false
                        progress = 0f
                        posMs = 0L
                        seekTo(0)
                        pause()
                    }
                }
            })
        }.also { player = it }
    }
    LaunchedEffect(player, isPlaying) {
        val activePlayer = player ?: return@LaunchedEffect
        if (!activePlayer.isPlaying) return@LaunchedEffect
        while (isActive && activePlayer.isPlaying) {
            if (activePlayer.isPlaying) {
                val dur = activePlayer.duration.takeIf { it > 0 } ?: 0L
                val pos = activePlayer.currentPosition.coerceAtLeast(0L)
                isPlaying = true; posMs = pos; durMs = dur
                if (dur > 0) progress = pos.toFloat() / dur
            } else {
                isPlaying = false
                if (activePlayer.playbackState == Player.STATE_ENDED) {
                    progress = 0f; posMs = 0; activePlayer.seekTo(0); activePlayer.pause()
                }
            }
            kotlinx.coroutines.delay(100)
        }
    }
    DisposableEffect(player) {
        val managedPlayer = player
        onDispose {
            managedPlayer?.release()
        }
    }
    fun fmtMs(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
    val displayDur = if (durMs > 0) fmtMs(durMs) else if (mediaSize > 0) "" else "0:00"

    Row(modifier = Modifier.widthIn(min = 180.dp, max = 260.dp).clip(RoundedCornerShape(8.dp))
        .background(viewerColors.fullScreenBackground.copy(0.15f)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val avatarModel = rememberViewerImageModel(avatarPath)
        if (avatarModel != null) {
            AsyncImage(model = avatarModel, contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop,
                placeholder = ColorPainter(viewerColors.placeholder), error = ColorPainter(viewerColors.placeholder))
        } else {
            Box(modifier = Modifier.size(36.dp).background(viewerColors.placeholder, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, null, tint = viewerColors.fullScreenOnColor, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(modifier = Modifier.size(32.dp), onClick = {
                    val currentPlayer = ensurePlayer() ?: return@IconButton
                    if (currentPlayer.isPlaying) {
                        currentPlayer.pause()
                    } else {
                        if (currentPlayer.playbackState == Player.STATE_ENDED) {
                            currentPlayer.seekTo(0)
                        }
                        currentPlayer.playWhenReady = true
                        currentPlayer.play()
                    }
                }) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                        tint = if (isMe) viewerColors.action else viewerColors.textPrimary, modifier = Modifier.size(24.dp))
                }
                Slider(value = progress,
                    onValueChange = { v ->
                        progress = v
                        val currentPlayer = player ?: return@Slider
                        currentPlayer.seekTo((v * (currentPlayer.duration.takeIf { it > 0 } ?: 1L)).toLong())
                    },
                    modifier = Modifier.weight(1f).height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = if (isMe) viewerColors.action else viewerColors.textPrimary,
                        activeTrackColor = if (isMe) viewerColors.action else viewerColors.textSecondary,
                        inactiveTrackColor = viewerColors.textSecondary.copy(0.3f)))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (posMs > 0) fmtMs(posMs) else displayDur, fontSize = 11.sp, color = viewerColors.textSecondary)
                Text(speedLabels[speedIndex], fontSize = 11.sp, color = viewerColors.textSecondary,
                    modifier = Modifier.clickable {
                        speedIndex = (speedIndex + 1) % speeds.size
                        player?.setPlaybackSpeed(speeds[speedIndex])
                    })
            }
        }
    }
}
