package com.mazin.wasensai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One entry per media-bearing message.
 * relativePath = "WhatsApp Business/Media/WhatsApp Business Images/IMG-xxx.jpg"
 * status       = "downloaded" | "missing" | "skipped"
 *
 * Viewer lookup: zipPathIndex[("media/" + relativePath).lowercase()]
 */
@Serializable
data class MediaEntry(
    @SerialName("message_id")    val messageId: Long    = 0,
    @SerialName("relative_path") val relativePath: String = "",
    @SerialName("mime_type")     val mimeType: String   = "",
    val size: Long                                       = 0,
    @SerialName("file_name")     val fileName: String   = "",
    val caption: String                                  = "",
    val duration: Int                                    = 0,   // ms, audio/video
    val width: Int                                       = 0,
    val height: Int                                      = 0,
    val transferred: Int                                 = 0,   // DB download status
    val status: String                                   = "missing",
    @SerialName("media_hash") val mediaHash: String      = ""   // SHA-256 / enc hash for deduplication
)
