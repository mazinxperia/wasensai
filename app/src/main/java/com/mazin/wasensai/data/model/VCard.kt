package com.mazin.wasensai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VCard(
    @SerialName("message_id") val messageId: Long = 0,
    val vcard: String                              = "",
    val jids: List<String>                         = emptyList()  // phone JIDs from message_vcard_jid
)
