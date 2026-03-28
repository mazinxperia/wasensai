package com.mazin.wasensai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Mention(
    @SerialName("message_id")    val messageId: Long    = 0,
    val jid: String                                      = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("mention_type") val mentionType: Int    = 0
)
