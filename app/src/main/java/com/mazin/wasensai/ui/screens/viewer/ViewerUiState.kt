package com.mazin.wasensai.ui.screens.viewer

import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mazin.wasensai.data.model.Chat
import com.mazin.wasensai.data.model.MediaEntry
import com.mazin.wasensai.data.model.Message
import com.mazin.wasensai.data.repository.MediaFailureReason
import com.mazin.wasensai.viewmodel.MediaState
import com.mazin.wasensai.viewmodel.ViewerViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun isValidViewerFile(file: File?): Boolean =
    file != null && file.exists() && file.length() > 0L

internal object ViewerVideoThumbnailCache {
    private val cache = object : LruCache<String, android.graphics.Bitmap>(24) {}

    fun get(path: String): android.graphics.Bitmap? = cache.get(path)

    fun put(path: String, bitmap: android.graphics.Bitmap) {
        cache.put(path, bitmap)
    }
}

internal suspend fun ensureViewerVideoThumbnail(file: File?): Boolean {
    val currentFile = file?.takeIf(::isValidViewerFile) ?: return false
    val path = currentFile.absolutePath
    if (ViewerVideoThumbnailCache.get(path) != null) return false
    val bitmap = withContext(Dispatchers.IO) {
        try {
            android.media.MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(path)
                retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (_: Exception) {
            null
        }
    } ?: return false
    ViewerVideoThumbnailCache.put(path, bitmap)
    return true
}

internal suspend fun warmViewerVideoThumbnails(
    messages: List<Message>,
    availabilityByMessageId: Map<Long, ViewerMediaAvailability>,
    limit: Int = 6
): Boolean {
    var warmedAny = false
    var warmedCount = 0
    for (message in messages) {
        if (message.messageType != 3) continue
        val availability = availabilityByMessageId[message.id] ?: continue
        if (!availability.isAvailable) continue
        val warmed = ensureViewerVideoThumbnail(availability.file)
        warmedAny = warmedAny || warmed
        warmedCount += 1
        if (warmedCount >= limit) break
    }
    return warmedAny
}

internal fun formatViewerFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return ""
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = sizeBytes.toDouble()
    var unitIndex = 0
    while (size >= 1024.0 && unitIndex < units.lastIndex) {
        size /= 1024.0
        unitIndex += 1
    }
    val precision = if (size >= 100 || unitIndex == 0) 0 else 1
    return "%.${precision}f %s".format(size, units[unitIndex])
}

@Composable
internal fun rememberMediaState(
    viewModel: ViewerViewModel,
    messageId: Long?
): MediaState? {
    val mediaFlow = remember(viewModel, messageId) {
        messageId?.let(viewModel::getMediaState)
    }
    val mediaState = mediaFlow?.collectAsStateWithLifecycle()
    return mediaState?.value
}

@Composable
internal fun rememberMediaFile(
    viewModel: ViewerViewModel,
    messageId: Long?
): File? = rememberMediaState(viewModel, messageId)?.file

@Composable
internal fun rememberAvatarFile(
    viewModel: ViewerViewModel,
    jid: String?
): File? = jid?.let(viewModel::getAvatar)

@Immutable
internal data class ViewerMediaAvailability(
    val file: File?,
    val isAvailable: Boolean,
    val isStorageFull: Boolean,
    val isPreparingFromBackup: Boolean,
    val isSkipped: Boolean,
    val isNotDownloaded: Boolean,
    val isMissingFromBackup: Boolean
)

internal fun resolveViewerMediaAvailability(
    mediaState: MediaState?,
    entry: MediaEntry?,
    fallbackPath: String
): ViewerMediaAvailability {
    val mediaFile = mediaState?.file
    val isAvailable = mediaState?.isReady == true && mediaFile != null
    val relativePath = entry?.relativePath ?: fallbackPath
    val hasPath = relativePath.isNotEmpty()
    val status = entry?.status.orEmpty()
    val transferred = entry?.transferred ?: 0
    val failureReason = mediaState?.failureReason
    val isStorageFull = !isAvailable && failureReason == MediaFailureReason.STORAGE_FULL
    val isPreparingFromBackup = !isAvailable && status == "downloaded"
    val isSkipped = !isAvailable && status == "skipped"
    val isNotDownloaded = !isAvailable && status == "missing" && !hasPath && transferred <= 0
    val isMissingFromBackup = !isAvailable && !isStorageFull && !isPreparingFromBackup && !isSkipped && !isNotDownloaded
    return ViewerMediaAvailability(
        file = mediaFile,
        isAvailable = isAvailable,
        isStorageFull = isStorageFull,
        isPreparingFromBackup = isPreparingFromBackup,
        isSkipped = isSkipped,
        isNotDownloaded = isNotDownloaded,
        isMissingFromBackup = isMissingFromBackup
    )
}

internal fun resolveViewerMediaAvailability(
    viewModel: ViewerViewModel,
    message: Message
): ViewerMediaAvailability = resolveViewerMediaAvailability(
    mediaState = viewModel.getMediaStateSnapshot(message.id),
    entry = viewModel.getMediaEntry(message.id),
    fallbackPath = message.mediaFilePath
)

internal fun buildViewerMediaAvailabilityMap(
    viewModel: ViewerViewModel,
    messages: List<Message>
): Map<Long, ViewerMediaAvailability> = messages.associate { message ->
    message.id to resolveViewerMediaAvailability(viewModel, message)
}

internal fun mediaUnavailableLabel(
    mediaType: String,
    availability: ViewerMediaAvailability
): String = when {
    availability.isStorageFull -> "Device storage is full. Cannot load media."
    availability.isPreparingFromBackup -> "$mediaType loading from backup"
    availability.isSkipped -> "$mediaType skipped during export"
    availability.isNotDownloaded -> "Not downloaded in WhatsApp"
    availability.isMissingFromBackup -> "$mediaType missing from backup"
    else -> "$mediaType unavailable"
}

@Composable
internal fun rememberViewerMediaAvailability(
    viewModel: ViewerViewModel,
    message: Message
): ViewerMediaAvailability {
    val mediaState = rememberMediaState(viewModel, message.id)
    val mediaEntry = remember(message.id) { viewModel.getMediaEntry(message.id) }
    return remember(mediaState, mediaEntry, message.mediaFilePath) {
        resolveViewerMediaAvailability(
            mediaState = mediaState,
            entry = mediaEntry,
            fallbackPath = message.mediaFilePath
        )
    }
}

@Composable
internal fun rememberContactName(
    viewModel: ViewerViewModel,
    jid: String?,
    fallback: String = ""
): String {
    return remember(jid, fallback) {
        if (jid.isNullOrEmpty()) return@remember fallback
        viewModel.getContactName(jid)
    }
}

@Composable
internal fun rememberViewerImageModel(file: File?): Any? {
    return rememberViewerImageModel(file?.absolutePath)
}

@Composable
internal fun rememberViewerImageModel(path: String?): Any? {
    val context = LocalContext.current
    return remember(path, context) {
        path?.let {
            ImageRequest.Builder(context)
                .data(it)
                .memoryCacheKey(it)
                .diskCacheKey(it)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(false)
                .build()
        }
    }
}

@Composable
internal fun rememberViewerChat(
    viewModel: ViewerViewModel,
    chatId: Long
): Chat? = remember(viewModel, chatId) {
    viewModel.getChatById(chatId)
}
