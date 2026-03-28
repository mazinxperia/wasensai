package com.mazin.wasensai.ui.screens.viewer

import androidx.compose.runtime.Immutable

@Immutable
internal data class ChatListUiModel(
    val chatId: Long,
    val displayName: String,
    val avatarPath: String?,
    val previewText: String,
    val timestampText: String
)
