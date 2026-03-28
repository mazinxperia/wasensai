package com.mazin.wasensai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Poll(
    @SerialName("message_id")       val messageId: Long               = 0,
    val question: String                                               = "",
    @SerialName("selectable_count") val selectableCount: Int          = 0,
    @SerialName("end_time")         val endTime: Long                  = 0,
    val options: List<PollOptionEntry>                                 = emptyList(),
    val votes: List<PollVote>                                          = emptyList()
)

@Serializable
data class PollOptionEntry(
    val id: Long                                            = 0,
    @SerialName("option_name") val optionName: String      = "",
    @SerialName("vote_total")  val voteTotal: Int          = 0
)

// Individual vote: who voted and which option(s) they chose
@Serializable
data class PollVote(
    @SerialName("voter_jid")  val voterJid: String  = "",
    @SerialName("option_ids") val optionIds: String = "",  // comma-separated option row IDs
    val timestamp: Long                              = 0
)
