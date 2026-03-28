package com.mazin.wasensai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Reaction(
    @SerialName("message_id") val messageId: Long   = 0,
    val emoji: String                                = "",
    @SerialName("sender_jid") val senderJid: String = "",
    val timestamp: Long                              = 0
)
