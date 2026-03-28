package com.mazin.wasensai.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class CallLog(
    val id: Long = 0,
    val jid: String = "",
    val timestamp: Long = 0,
    val duration: Long = 0,
    @SerialName("from_me") val fromMe: Boolean = false,
    @SerialName("call_id") val callId: String = "",
    @SerialName("call_result") val callResult: Int = 0,
    @SerialName("chat_subject") val chatSubject: String = "",
    @SerialName("is_video") val isVideo: Boolean = false,
    val participants: List<CallParticipant> = emptyList()
)

// Group call participant details from call_log_participant_v2
@Serializable
data class CallParticipant(
    val jid: String                              = "",
    @SerialName("call_result") val callResult: Int = 0,
    val duration: Long                           = 0
)
