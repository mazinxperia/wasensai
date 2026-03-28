package com.mazin.wasensai.ui.screens.viewer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.mazin.wasensai.data.model.Message
import com.mazin.wasensai.utils.DateUtils

@Immutable
internal data class ChatMessageUiMeta(
    val senderName: String = "",
    val senderAvatarPath: String? = null,
    val audioAvatarPath: String? = null,
    val quotedSenderDisplay: String = "",
    val quotedPreviewText: String = "",
    val messageText: AnnotatedString? = null,
    val mediaCaptionText: String = "",
    val documentFileName: String = "",
    val documentExt: String = "",
    val documentSizeText: String = "",
    val locationText: String = "",
    val reactionChips: List<String> = emptyList(),
    val timestampText: String = ""
)

@Immutable
internal sealed interface ChatTimelineItem {
    val stableKey: Any
    val contentType: Int
}

@Immutable
internal data object ChatTimelineStartItem : ChatTimelineItem {
    override val stableKey: Any = "__chat_start__"
    override val contentType: Int = 3
}

@Immutable
internal data class ChatTimelineDateItem(
    val label: String
) : ChatTimelineItem {
    override val stableKey: Any = "date_$label"
    override val contentType: Int = 3
}

@Immutable
internal data class ChatTimelineMessageItem(
    val message: Message,
    val uiMeta: ChatMessageUiMeta,
    val mediaIndex: Int
) : ChatTimelineItem {
    override val stableKey: Any = message.id
    override val contentType: Int = when (message.messageType) {
        0 -> 0
        1 -> 1
        3 -> 2
        else -> 4
    }
}

@Immutable
internal data class ChatRenderData(
    val timelineItems: List<ChatTimelineItem>,
    val keyIdToItemsIndex: Map<String, Int>,
    val itemIndexByMessageId: Map<Long, Int>,
    val messageCountsByItemIndex: IntArray,
    val sourceMessageCount: Int
)

internal fun buildChatRenderData(
    messages: List<Message>,
    chatIsGroup: Boolean,
    searchQuery: String,
    inChatMatchIdSet: Set<Long>,
    mediaIndexByMessageId: Map<Long, Int>,
    resolveContactName: (String) -> String,
    resolveAvatarPath: (String) -> String?,
    resolveMessageByKeyId: (String) -> Message?
): ChatRenderData {
    val items = ArrayList<ChatTimelineItem>(messages.size + 16)
    val keyIdIndex = HashMap<String, Int>(messages.size)
    val itemIndexByMessageId = HashMap<Long, Int>(messages.size)
    val messageCounts = ArrayList<Int>(messages.size + 16)
    var messageCount = 0

    fun addItem(item: ChatTimelineItem) {
        items.add(item)
        messageCounts.add(messageCount)
    }

    addItem(ChatTimelineStartItem)
    var lastDate = ""
    for (message in messages) {
        val dateLabel = DateUtils.formatDateSeparator(message.timestamp)
        if (dateLabel != lastDate) {
            lastDate = dateLabel
            addItem(ChatTimelineDateItem(dateLabel))
        }
        val senderName = when {
            !chatIsGroup -> ""
            message.fromMe == 1 -> "You"
            message.senderName.isNotEmpty() -> message.senderName
            else -> resolveContactName(message.senderJid.ifEmpty { message.chatJid })
        }
        val senderAvatarPath = when {
            chatIsGroup && message.fromMe == 1 -> resolveAvatarPath("me")
            chatIsGroup && message.fromMe == 0 && message.senderJid.isNotEmpty() -> resolveAvatarPath(message.senderJid)
            else -> null
        }
        val audioAvatarPath = when {
            message.messageType != 2 -> null
            message.fromMe == 1 -> resolveAvatarPath(message.chatJid)
            else -> resolveAvatarPath(message.senderJid.ifEmpty { message.chatJid })
        }
        val originalMsg = message.quotedKeyId
            .takeIf { it.isNotEmpty() }
            ?.let(resolveMessageByKeyId)
        val quotedSenderDisplay = when {
            message.quotedKeyId.isEmpty() -> ""
            message.quotedFromMe == 1 || originalMsg?.fromMe == 1 -> "You"
            originalMsg?.senderName?.isNotEmpty() == true -> originalMsg.senderName
            originalMsg != null -> resolveContactName(originalMsg.senderJid.ifEmpty { originalMsg.chatJid })
            message.quotedSender.isNotEmpty() -> resolveContactName(message.quotedSender)
            else -> ""
        }
        val messageText = if (message.messageType == 0 && message.textData.isNotEmpty()) {
            if (message.id in inChatMatchIdSet && searchQuery.isNotEmpty()) {
                buildHighlightedText(message.textData, searchQuery)
            } else {
                buildAnnotatedString { append(message.textData) }
            }
        } else {
            null
        }
        val reactionChips = if (message.reactions.isEmpty()) {
            emptyList()
        } else {
            message.reactions
                .groupBy { it.emoji }
                .map { (emoji, senders) -> if (senders.size > 1) "$emoji ${senders.size}" else emoji }
        }
        val uiMeta = ChatMessageUiMeta(
            senderName = senderName,
            senderAvatarPath = senderAvatarPath,
            audioAvatarPath = audioAvatarPath,
            quotedSenderDisplay = quotedSenderDisplay,
            quotedPreviewText = message.quotedText.ifEmpty { "Media" },
            messageText = messageText,
            mediaCaptionText = message.mediaCaption.ifEmpty { message.textData },
            documentFileName = message.mediaFilePath.substringAfterLast("/").ifEmpty { message.textData.ifEmpty { "Document" } },
            documentExt = message.mediaFilePath.substringAfterLast(".", "")
                .uppercase()
                .let { ext ->
                    if (ext.isEmpty() || ext == message.mediaFilePath.substringAfterLast("/").uppercase()) {
                        "DOC"
                    } else {
                        ext
                    }
                },
            documentSizeText = formatViewerFileSize(message.mediaSize),
            locationText = "${String.format("%.4f", message.latitude)}, ${String.format("%.4f", message.longitude)}",
            reactionChips = reactionChips,
            timestampText = DateUtils.formatMessageTimestamp(message.timestamp)
        )
        messageCount += 1
        val itemIndex = items.size
        items.add(
            ChatTimelineMessageItem(
                message = message,
                uiMeta = uiMeta,
                mediaIndex = mediaIndexByMessageId[message.id] ?: -1
            )
        )
        messageCounts.add(messageCount)
        itemIndexByMessageId[message.id] = itemIndex
        if (message.keyId.isNotEmpty()) {
            keyIdIndex[message.keyId] = itemIndex
        }
    }

    return ChatRenderData(
        timelineItems = items,
        keyIdToItemsIndex = keyIdIndex,
        itemIndexByMessageId = itemIndexByMessageId,
        messageCountsByItemIndex = messageCounts.toIntArray(),
        sourceMessageCount = messages.size
    )
}

internal fun buildHighlightedText(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return buildAnnotatedString { append(text) }
    return buildAnnotatedString {
        var start = 0
        val lower = text.lowercase()
        val lowerQuery = query.lowercase()
        while (true) {
            val idx = lower.indexOf(lowerQuery, start)
            if (idx < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, idx))
            withStyle(SpanStyle(background = Color(0xFFFFEB3B))) {
                append(text.substring(idx, idx + query.length))
            }
            start = idx + query.length
        }
    }
}
