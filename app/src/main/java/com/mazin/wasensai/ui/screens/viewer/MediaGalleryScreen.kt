package com.mazin.wasensai.ui.screens.viewer

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mazin.wasensai.data.model.Message
import com.mazin.wasensai.ui.theme.viewerColors
import com.mazin.wasensai.viewmodel.ViewerViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

private data class MediaSection(
    val label: String,
    val rows: List<List<Message>>
)

private fun buildMediaSections(mediaMessages: List<Message>): List<MediaSection> {
    if (mediaMessages.isEmpty()) return emptyList()

    val now = Calendar.getInstance()
    val recentMessages = ArrayList<Message>()
    val lastWeekMessages = ArrayList<Message>()
    val olderMessages = ArrayList<Message>()

    mediaMessages.forEach { msg ->
        val cal = Calendar.getInstance().apply { timeInMillis = msg.timestamp }
        val diff = now.get(Calendar.WEEK_OF_YEAR) - cal.get(Calendar.WEEK_OF_YEAR)
        when {
            now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) && diff == 0 -> recentMessages += msg
            now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) && diff == 1 -> lastWeekMessages += msg
            else -> olderMessages += msg
        }
    }

    return buildList {
        if (recentMessages.isNotEmpty()) add(MediaSection("RECENT", recentMessages.chunked(3)))
        if (lastWeekMessages.isNotEmpty()) add(MediaSection("LAST WEEK", lastWeekMessages.chunked(3)))
        if (olderMessages.isNotEmpty()) add(MediaSection("OLDER", olderMessages.chunked(3)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(
    chatId: Long,
    onBackClick: () -> Unit,
    onMediaClick: (Int) -> Unit,
    viewModel: ViewerViewModel
) {
    val viewerColors = MaterialTheme.viewerColors
    val mediaStateVersion by viewModel.mediaStateVersion.collectAsStateWithLifecycle()
    val chat = rememberViewerChat(viewModel, chatId)
    val chatName = remember(chat?.id, chat?.subject, chat?.jid, chat?.isGroup) {
        chat?.let {
            if (it.isGroup) it.subject.ifEmpty { "Group" } else viewModel.getContactName(it.jid)
        } ?: "Media"
    }

    val allMessages = remember(chatId) { viewModel.getMediaMessages(chatId) }
    val mediaMessages = remember(allMessages) { allMessages.filter { it.messageType in listOf(1, 3) } }
    val docMessages = remember(allMessages) { allMessages.filter { it.messageType == 9 } }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Media", "Docs", "Links")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(viewerColors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(viewerColors.header)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    "Back",
                    tint = viewerColors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                chatName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = viewerColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.Search, null, tint = viewerColors.textPrimary, modifier = Modifier.size(22.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(viewerColors.background)
        ) {
            tabs.forEachIndexed { idx, label ->
                val selected = selectedTab == idx
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = idx }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) viewerColors.textPrimary else viewerColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) viewerColors.action else Color.Transparent)
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = viewerColors.divider)

        when (selectedTab) {
            0 -> MediaTab(
                mediaMessages = mediaMessages,
                mediaStateVersion = mediaStateVersion,
                onMediaClick = onMediaClick,
                viewModel = viewModel
            )
            1 -> DocsTab(docMessages = docMessages, viewModel = viewModel)
            2 -> LinksTab()
        }
    }
}

@Composable
private fun MediaTab(
    mediaMessages: List<Message>,
    mediaStateVersion: Int,
    onMediaClick: (Int) -> Unit,
    viewModel: ViewerViewModel
) {
    if (mediaMessages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No media", color = MaterialTheme.viewerColors.textSecondary, fontSize = 14.sp)
        }
        return
    }

    val listState = rememberLazyListState()
    val isListScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    var settledMediaStateVersion by remember { mutableIntStateOf(mediaStateVersion) }
    var videoThumbnailVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(mediaStateVersion, isListScrolling) {
        if (!isListScrolling) {
            settledMediaStateVersion = mediaStateVersion
        }
    }
    val sections = remember(mediaMessages) { buildMediaSections(mediaMessages) }
    val globalIndexMap = remember(mediaMessages) {
        mediaMessages.mapIndexed { index, message -> message.id to index }.toMap()
    }
    val firstVisibleMediaIndex by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex * 3).coerceAtLeast(0)
        }
    }
    val availabilityByMessageId = remember(mediaMessages, settledMediaStateVersion) {
        buildViewerMediaAvailabilityMap(viewModel, mediaMessages)
    }
    LaunchedEffect(listState, mediaMessages, availabilityByMessageId) {
        snapshotFlow {
            listState.isScrollInProgress to firstVisibleMediaIndex
        }.collectLatest { (isScrolling, firstVisibleIndex) ->
            if (isScrolling) return@collectLatest
            kotlinx.coroutines.delay(240)
            if (listState.isScrollInProgress) return@collectLatest
            if (mediaMessages.isEmpty()) return@collectLatest
            val start = firstVisibleIndex.coerceAtMost(mediaMessages.lastIndex)
            val endExclusive = (start + 12).coerceAtMost(mediaMessages.size)
            val candidateMessages = mediaMessages.subList(start, endExclusive)
            if (candidateMessages.isNotEmpty() &&
                warmViewerVideoThumbnails(candidateMessages, availabilityByMessageId, limit = 8)
            ) {
                videoThumbnailVersion += 1
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState
    ) {
        sections.forEach { section ->
            item(key = "header_${section.label}") {
                MediaSectionHeader(section.label)
            }
            items(
                items = section.rows,
                key = { row -> "row_${section.label}_${row.firstOrNull()?.id ?: 0L}" },
                contentType = { 0 }
            ) { row ->
                MediaGridRow(
                    rowMessages = row,
                    availabilityByMessageId = availabilityByMessageId,
                    globalIndexMap = globalIndexMap,
                    videoThumbnailVersion = videoThumbnailVersion,
                    onMediaClick = onMediaClick,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun MediaSectionHeader(label: String) {
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.viewerColors.textSecondary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp, end = 16.dp)
    )
}

@Composable
private fun MediaGridRow(
    rowMessages: List<Message>,
    availabilityByMessageId: Map<Long, ViewerMediaAvailability>,
    globalIndexMap: Map<Long, Int>,
    videoThumbnailVersion: Int,
    onMediaClick: (Int) -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        rowMessages.forEach { msg ->
            val globalIdx = globalIndexMap[msg.id] ?: 0
            val availability = availabilityByMessageId[msg.id] ?: resolveViewerMediaAvailability(
                mediaState = null,
                entry = null,
                fallbackPath = msg.mediaFilePath
            )
            val isVideo = msg.messageType == 3

            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(viewerColors.placeholder)
                    .clickable { onMediaClick(globalIdx) }
            ) {
                when {
                    availability.isAvailable -> {
                        if (isVideo) {
                            val thumbnail = remember(availability.file?.absolutePath, videoThumbnailVersion) {
                                availability.file?.absolutePath?.let(ViewerVideoThumbnailCache::get)
                            }
                            if (thumbnail != null) {
                                Image(
                                    bitmap = thumbnail.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(viewerColors.surfaceVariant)
                                )
                            }
                        } else {
                            val imageModel = rememberViewerImageModel(availability.file)
                            AsyncImage(
                                model = imageModel,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        if (isVideo) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.PlayCircle,
                                    null,
                                    tint = viewerColors.fullScreenOnColor.copy(alpha = 0.9f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    else -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val icon = if (availability.isSkipped) Icons.Rounded.Block else Icons.Rounded.CloudDownload
                            Icon(icon, null, tint = viewerColors.textSecondary, modifier = Modifier.size(20.dp))
                            Text(
                                mediaUnavailableLabel(if (isVideo) "Video" else "Image", availability),
                                fontSize = 9.sp,
                                color = viewerColors.textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        repeat(3 - rowMessages.size) {
            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
        }
    }
}

@Composable
private fun DocsTab(
    docMessages: List<Message>,
    viewModel: ViewerViewModel
) {
    val viewerColors = MaterialTheme.viewerColors
    val context = LocalContext.current

    if (docMessages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No documents", color = viewerColors.textSecondary, fontSize = 14.sp)
        }
        return
    }

    val grouped = remember(docMessages) {
        docMessages.groupBy { msg ->
            val cal = Calendar.getInstance().apply { timeInMillis = msg.timestamp }
            val now = Calendar.getInstance()
            val monthDiff = (now.get(Calendar.YEAR) - cal.get(Calendar.YEAR)) * 12 +
                (now.get(Calendar.MONTH) - cal.get(Calendar.MONTH))
            when {
                monthDiff == 0 -> "THIS MONTH"
                monthDiff == 1 -> "LAST MONTH"
                else -> SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                    .format(Date(msg.timestamp))
                    .uppercase()
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        grouped.forEach { (section, msgs) ->
            item(key = "header_$section") {
                Text(
                    section,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = viewerColors.textSecondary,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp)
                )
            }
            items(msgs, key = { it.id }, contentType = { 1 }) { msg ->
                DocRow(msg = msg, context = context)
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    thickness = 0.5.dp,
                    color = viewerColors.divider
                )
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun DocRow(msg: Message, context: android.content.Context) {
    val viewerColors = MaterialTheme.viewerColors
    val fileName = msg.mediaFilePath.substringAfterLast("/").ifEmpty { "Document" }
    val ext = fileName.substringAfterLast(".").uppercase().take(4)
    val extColor = when (ext) {
        "PDF" -> viewerColors.error
        "XLS", "XLSX" -> viewerColors.success
        "DOC", "DOCX" -> viewerColors.info
        else -> viewerColors.icon
    }
    val dateStr = remember(msg.timestamp) {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(msg.timestamp))
    }
    val sizeStr = if (msg.mediaSize > 0) Formatter.formatShortFileSize(context, msg.mediaSize) else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {}
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(extColor),
            contentAlignment = Alignment.Center
        ) {
            Text(ext, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = viewerColors.fullScreenOnColor)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                fileName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = viewerColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sizeStr.isNotEmpty()) {
                Text(
                    "$sizeStr • $ext",
                    fontSize = 12.sp,
                    color = viewerColors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        Text(dateStr, fontSize = 12.sp, color = viewerColors.textSecondary)
    }
}

@Composable
private fun LinksTab() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No links found", color = MaterialTheme.viewerColors.textSecondary, fontSize = 14.sp)
    }
}
