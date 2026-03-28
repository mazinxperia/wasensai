package com.mazin.wasensai.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Immutable
@Serializable
data class Message(
    val id: Long = 0,
    @SerialName("chat_id")            val chatId: Long        = 0,
    @SerialName("chat_jid")           val chatJid: String     = "",
    @SerialName("text_data")          val textData: String    = "",
    @SerialName("from_me")            val fromMe: Int         = 0,
    val timestamp: Long                                        = 0,
    @SerialName("received_timestamp") val receivedTimestamp: Long = 0,
    @SerialName("sort_id")            val sortId: Long        = 0,
    @SerialName("message_type")       val messageType: Int    = 0,
    val status: Int                                            = 0,
    @SerialName("edit_version")       val editVersion: Int    = 0,
    @SerialName("key_id")             val keyId: String       = "",
    @SerialName("sender_jid")         val senderJid: String   = "",
    @SerialName("sender_name")        val senderName: String  = "",
    @SerialName("chat_subject")       val chatSubject: String = "",
    // Flags
    @SerialName("is_deleted")   val isDeleted: Boolean   = false,
    @SerialName("is_system")    val isSystem: Boolean    = false,
    @SerialName("is_video_call") val isVideoCall: Boolean = false,
    @SerialName("is_forwarded") val isForwarded: Boolean = false,
    @SerialName("forward_score") val forwardScore: Int   = 0,
    val starred: Int                                      = 0,
    val broadcast: Int                                    = 0,
    @SerialName("translated_text") val translatedText: String = "",
    // Location
    val latitude: Double  = 0.0,
    val longitude: Double = 0.0,
    // Quoted / Reply
    @SerialName("quoted_key_id")       val quotedKeyId: String      = "",
    @SerialName("quoted_from_me")      val quotedFromMe: Int        = 0,
    @SerialName("quoted_message_type") val quotedMessageType: Int   = 0,
    @SerialName("quoted_text")         val quotedText: String       = "",
    @SerialName("quoted_sender")       val quotedSender: String     = "",
    @SerialName("quoted_sender_name")  val quotedSenderName: String = "",
    // System message
    @SerialName("action_type") val actionType: Int = 0,
    // Deletion
    @SerialName("deleted_for_everyone") val deletedForEveryone: Boolean = false,
    // Location details (populated when message_type = 5)
    @SerialName("place_name")    val placeName: String    = "",
    @SerialName("place_address") val placeAddress: String = "",

    // ─── Transient — NOT in JSON ─────────────────────────────────────────────
    // Populated by ViewerRepository at load time from mediaIndex
    @Transient val mediaFilePath: String = "",
    @Transient val mediaMimeType: String = "",
    @Transient val mediaCaption: String  = "",
    @Transient val mediaName: String     = "",
    @Transient val mediaSize: Long       = 0L,
    // Populated from reactions list
    @Transient val reactions: List<Reaction> = emptyList(),
    // Populated from polls list
    @Transient val pollQuestion: String        = "",
    @Transient val pollOptions: List<PollOption> = emptyList()
)

// Used only by viewer screens (transient copy from Poll model)
@Serializable
data class PollOption(
    val text: String  = "",
    val votes: Int    = 0
)
