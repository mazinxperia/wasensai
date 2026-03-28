package com.mazin.wasensai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Label(
    val id: Long                                          = 0,
    val name: String                                      = "",
    @SerialName("color_id")      val colorId: Int        = 0,
    @SerialName("predefined_id") val predefinedId: Int   = 0
)

@Serializable
data class LabeledMessage(
    @SerialName("label_id")   val labelId: Long   = 0,
    @SerialName("message_id") val messageId: Long = 0
)
