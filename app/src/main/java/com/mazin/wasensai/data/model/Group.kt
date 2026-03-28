package com.mazin.wasensai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Group(
    @SerialName("chat_id")           val chatId: Long                          = 0,
    val jid: String                                                             = "",
    val name: String                                                            = "",
    @SerialName("member_count")      val memberCount: Int                      = 0,
    val participants: List<GroupParticipant>                                    = emptyList(),
    @SerialName("past_participants") val pastParticipants: List<PastParticipant> = emptyList(),
    @SerialName("invite_link")       val inviteLink: String                    = ""
)

@Serializable
data class GroupParticipant(
    val jid: String                                       = "",
    val rank: Int                                         = 0,   // 0=member 1=admin 2=superadmin
    @SerialName("add_timestamp") val addTimestamp: Long  = 0,
    val pending: Boolean                                  = false
)

@Serializable
data class PastParticipant(
    val jid: String                              = "",
    @SerialName("is_leave") val isLeave: Boolean = true,
    val timestamp: Long                          = 0
)
