package com.mazin.wasensai.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Chat(
    val id: Long                                               = 0,
    val jid: String                                            = "",
    val subject: String                                        = "",
    @SerialName("chat_name")            val chatName: String  = "",
    @SerialName("is_group")             val isGroup: Boolean  = false,
    @SerialName("sort_timestamp")       val sortTimestamp: Long = 0,
    @SerialName("last_message")         val lastMessage: String = "",
    @SerialName("last_message_type")    val lastMessageType: Int = 0,
    @SerialName("member_count")         val memberCount: Int  = 0,
    @SerialName("avatar_file")          val avatarFile: String = "",
    val archived: Boolean                                      = false,
    @SerialName("unread_count")         val unreadCount: Int  = 0,
    @SerialName("ephemeral_expiration") val ephemeralExpiration: Int = 0,
    @SerialName("created_timestamp")    val createdTimestamp: Long = 0,
    val pinned: Boolean                                        = false,
    @SerialName("muted_until")          val mutedUntil: Long  = 0   // 0=not muted, -1=forever muted, else expiry ms
)
