package com.mazin.wasensai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// One entry per edit event — multiple entries per message if edited more than once.
// Ordered ASC by edited_timestamp so the list reads oldest→newest revision.
@Serializable
data class MessageEdit(
    @SerialName("message_id")       val messageId: Long       = 0,
    @SerialName("edited_timestamp") val editedTimestamp: Long = 0,
    @SerialName("previous_text")    val previousText: String  = ""
)
