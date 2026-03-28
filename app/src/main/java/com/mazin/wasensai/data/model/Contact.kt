package com.mazin.wasensai.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Contact(
    val jid: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("wa_name") val waName: String = "",
    val number: String = "",
    val status: String = "",
    @SerialName("avatar_file") val avatarFile: String = ""
)
