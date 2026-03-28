package com.mazin.wasensai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// WhatsApp status/story metadata — available but not rendered in current UI.
// Logged during viewer sync: "Status updates: N (available, UI not supported)"
@Serializable
data class StatusUpdate(
    val jid: String                                    = "",
    val timestamp: Long                                = 0,
    @SerialName("unseen_count") val unseenCount: Int   = 0,
    @SerialName("total_count")  val totalCount: Int    = 0
)
