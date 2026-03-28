package com.mazin.wasensai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExportInfo(
    @SerialName("phone_number")    val phoneNumber: String  = "",
    @SerialName("export_date")     val exportDate: String   = "",
    @SerialName("app_version")     val appVersion: String   = "1.0.0",
    @SerialName("format_version")  val formatVersion: Int   = 3,
    @SerialName("total_chats")     val totalChats: Int      = 0,
    @SerialName("total_messages")  val totalMessages: Int   = 0,
    @SerialName("total_media")     val totalMedia: Int      = 0
)

// ─── Root archive model ───────────────────────────────────────────────────────
// Format version 3 — flat, lossless, fully linked.
// All entities link to message via messageId (reactions, polls, media, mentions).
// Viewer populates @Transient fields at load time from their respective lists.

@Serializable
data class WaViewFile(
    @SerialName("export_info")      val exportInfo: ExportInfo         = ExportInfo(),
    val chats:                           List<Chat>                    = emptyList(),
    val contacts:                        List<Contact>                 = emptyList(),
    val groups:                          List<Group>                   = emptyList(),
    val messages:                        List<Message>                 = emptyList(),
    val reactions:                       List<Reaction>                = emptyList(),
    val polls:                           List<Poll>                    = emptyList(),
    @SerialName("media_index")      val mediaIndex: List<MediaEntry>   = emptyList(),
    @SerialName("call_logs")        val callLogs: List<CallLog>        = emptyList(),
    val labels:                          List<Label>                   = emptyList(),
    @SerialName("labeled_messages") val labeledMessages: List<LabeledMessage> = emptyList(),
    val mentions:                        List<Mention>                 = emptyList(),
    val vcards:                           List<VCard>                   = emptyList(),
    val statuses:                         List<StatusUpdate>            = emptyList(),
    @SerialName("message_edits")     val messageEdits: List<MessageEdit> = emptyList(),
    @SerialName("starred_messages")  val starredMessages: List<Long>   = emptyList()
)
