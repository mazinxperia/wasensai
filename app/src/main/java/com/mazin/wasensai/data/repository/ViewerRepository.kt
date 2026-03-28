package com.mazin.wasensai.data.repository

import android.content.Context
import android.net.Uri
import com.mazin.wasensai.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class VerificationState(
    val totalMedia: Int    = 0,
    val resolvedMedia: Int = 0,
    val extractedMedia: Int = 0,
    val failedMedia: Int   = 0
)

/**
 * Step-by-step resolution log for a single media item that required fallback
 * (or completely failed). Items resolved via EXACT match are NOT stored here.
 */
data class SmartResolveLog(
    val messageId: Long,
    val originalPath: String,       // relative_path from data.json
    val steps: List<String>,        // each step's outcome
    val result: String,             // EXACT | EXT_FIXED | MOVED | RENAMED | DELETED
    val resolvedPath: String? = null // final matched zip path (null if DELETED)
)

/**
 * Rich summary of what was synced, what was skipped, and what's in data.json but not shown.
 *
 * Media status categories (smart distinction):
 *   inArchive        = status="downloaded"  → file is in the .waview zip ✅
 *   skippedByUser    = status="skipped"     → user unchecked that media type during export
 *   neverDownloaded  = relativePath="" OR (status="missing" AND transferred<2) → media never saved to device in WA
 *   missing          = status="missing" AND relativePath!="" AND transferred>=2 → was on device, not in archive
 */
data class SyncLogSummary(
    // Phase 1 — data
    val messagesLoaded: Int       = 0,
    val chatsLoaded: Int          = 0,
    val contactsLoaded: Int       = 0,
    val groupsLoaded: Int         = 0,
    val avatarsSynced: Int        = 0,
    // Media breakdown
    val mediaTotal: Int           = 0,
    val mediaInArchive: Int       = 0,     // ✅ in zip, ready to view
    val mediaSkippedByUser: Int   = 0,     // ⊘ user chose not to export this type
    val mediaNeverDownloaded: Int = 0,     // ℹ️ media sent in chat but never opened/downloaded in WA
    val mediaMissing: Int         = 0,     // ⚠️ was downloaded in WA but not found in archive
    // Phase 2 — zip resolution
    val mediaResolvedInZip: Int   = 0,     // paths matched in zip index (exact + fallback)
    val mediaNotFoundInZip: Int   = 0,     // DELETED after all 6 fallback steps
    val resolvedViaFallback: Int  = 0,     // recovered via EXT_FIXED / MOVED / RENAMED
    // Unused data (in data.json but no viewer UI yet)
    val unusedDataItems: List<String> = emptyList(),
    // Smart recovery logs — only for non-exact resolutions and failures
    val smartResolveLogs: List<SmartResolveLog> = emptyList()
)

data class SyncState(
    val isSyncing: Boolean          = false,
    val currentStep: String         = "",
    val progress: Int               = 0,
    val total: Int                  = 0,
    val logs: List<String>          = emptyList(),
    val isComplete: Boolean         = false,
    val verificationState: VerificationState = VerificationState(),
    val logSummary: SyncLogSummary  = SyncLogSummary()
)

enum class MediaFailureReason {
    STORAGE_FULL,
    EXTRACT_FAILED
}

private data class ExtractResult(
    val success: Boolean,
    val failureReason: MediaFailureReason? = null
)

@Singleton
class ViewerRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val repoScope = CoroutineScope(Dispatchers.IO)

    var currentWaViewFile: WaViewFile? = null
        private set
    private var currentZipFile: ZipFile? = null

    // ─── Zip index ────────────────────────────────────────────────────────────
    // zipPathIndex:  lowercase zip path → FileHeader
    // fileNameIndex: lowercase filename → list of FileHeaders (for filename fallback)
    private val zipPathIndex  = ConcurrentHashMap<String, FileHeader>(4096)
    private val fileNameIndex = ConcurrentHashMap<String, MutableList<FileHeader>>(4096)

    // ─── Media resolution ─────────────────────────────────────────────────────
    // mediaEntryMap:    messageId → MediaEntry (from mediaIndex in data.json)
    // messageMediaIndex: messageId → resolved FileHeader (built in Phase 2)
    // deletedMediaSet:  messageIds confirmed DELETED after all fallback steps (Phase 3 skips these)
    private val mediaEntryMap     = ConcurrentHashMap<Long, MediaEntry>(2048)
    private val messageMediaIndex = ConcurrentHashMap<Long, FileHeader>(2048)
    private val deletedMediaSet   = ConcurrentHashMap<Long, Boolean>(256)

    // ─── Runtime data ─────────────────────────────────────────────────────────
    private val messagesByChatId = ConcurrentHashMap<Long, List<Message>>(512)
    // keyIdIndex: keyId → Message (for reply-scroll lookups, built per-chat)
    val avatarCache  = ConcurrentHashMap<String, File>(512)
    val mediaCache   = ConcurrentHashMap<Long, File>(2048)
    private val mediaFailureReasons = ConcurrentHashMap<Long, MediaFailureReason>(512)
    private val chatReadySet = ConcurrentHashMap<Long, Boolean>(256)

    private val extractionLocks = ConcurrentHashMap<String, Mutex>()
    private fun getLock(key: String): Mutex = extractionLocks.getOrPut(key) { Mutex() }

    // ZipFile (zip4j) is NOT thread-safe: concurrent getInputStream() calls on the same
    // ZipFile instance corrupt each other's reads. Serialize ALL zip reads with this mutex.
    private val zipReadMutex = Mutex()

    private val avatarCacheDir: File by lazy { File(context.filesDir, "wa_avatars").also { it.mkdirs() } }
    private val mediaCacheDir:  File by lazy { File(context.filesDir, "wa_media").also { it.mkdirs() } }
    private val importWorkDir:  File by lazy { File(context.filesDir, "waview_import").also { it.mkdirs() } }

    private val highPriorityQueue = ArrayDeque<Long>()
    private val queueMutex = Mutex()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    private val logBuffer = ArrayDeque<String>(500)
    @Volatile private var logSnapshot: List<String> = emptyList()
    private var phase2Job: Job? = null
    @Volatile private var currentLogSummary = SyncLogSummary()
    @Volatile private var allMessagesCache: List<Message> = emptyList()
    @Volatile private var starredMessagesCache: List<Message> = emptyList()

    @Volatile private var lastProgressTime = System.currentTimeMillis()

    private val junkPathSegments = setOf(".shared/","backups/","databases/",".stickerthumbs/",".trash/",".thumbs/",".wamocache/",".statuses/",".links/")
    private val junkExtensions   = setOf(".tmp",".chk",".enc",".thumb",".log",".crypt14",".chck",".bak")
    private val junkFileNames    = setOf(".nomedia","media.zip","future_media.json")

    // ─── PHASE 1 ─────────────────────────────────────────────────────────────

    suspend fun loadWaView(uri: Uri): Result<WaViewFile> = withContext(Dispatchers.IO) {
        runCatching {
            clear()
            log("Opening .waview archive...")
            emitSync(isSyncing = true, step = "Opening file...")

            val tempDir = importWorkDir.also { it.deleteRecursively(); it.mkdirs() }
            val tempZip = File(tempDir, "import.zip")

            context.contentResolver.openInputStream(uri)?.use { i ->
                tempZip.outputStream().use { o -> i.copyTo(o, bufferSize = 8 * 1024 * 1024) }
            } ?: error("Could not open file")

            log("Copied: ${tempZip.length() / (1024 * 1024)}MB — building index...")
            emitSync(step = "Building index...")

            val zipFile = ZipFile(tempZip)
            currentZipFile = zipFile
            emitSync(step = "Indexing archive files...", progress = 0, total = zipFile.fileHeaders.size)

            // Check format version — fail fast with clear message for old archives
            val versionHeader = zipFile.fileHeaders.firstOrNull {
                it.fileName.equals("meta/version.json", ignoreCase = true)
            }
            if (versionHeader != null) {
                val versionText = zipFile.getInputStream(versionHeader).use { it.bufferedReader().readText() }
                val fv = json.parseToJsonElement(versionText)
                    .let { it as? kotlinx.serialization.json.JsonObject }
                    ?.get("formatVersion")
                    ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                    ?.content?.toIntOrNull() ?: 1
                if (fv < 3) error("Archive format v$fv is outdated. Re-extract with the latest WA Sensai app.")
            }

            var indexed = 0; var skipped = 0; var processedHeaders = 0
            for (h in zipFile.fileHeaders) {
                processedHeaders++
                if (h.isDirectory) continue
                val pathNorm      = h.fileName.replace("\\", "/").trim()
                val pathLower     = pathNorm.lowercase()
                val fileNameLower = pathNorm.substringAfterLast("/").lowercase()
                if (isJunk(pathLower, fileNameLower)) { skipped++; continue }
                zipPathIndex[pathLower] = h
                fileNameIndex.getOrPut(fileNameLower) { mutableListOf() }.add(h)
                indexed++
                if (processedHeaders % 500 == 0 || processedHeaders == zipFile.fileHeaders.size) {
                    emitSync(
                        step = "Indexing archive files...",
                        progress = processedHeaders,
                        total = zipFile.fileHeaders.size
                    )
                }
            }
            log("Index: $indexed entries, $skipped junk skipped")
            emitSync(step = "Indexed $indexed files")

            val dataHeader = zipPathIndex["data.json"]
                ?: zipFile.fileHeaders.firstOrNull { it.fileName.equals("data.json", true) }
                ?: error("Invalid .waview: missing data.json")
            val extractDir = File(tempDir, "extracted").also { it.mkdirs() }
            emitSync(step = "Parsing archive data...")
            zipFile.extractFile(dataHeader, extractDir.absolutePath)
            val waView = json.decodeFromString<WaViewFile>(File(extractDir, "data.json").readText())
            currentWaViewFile = waView

            // ── Build mediaEntryMap ──────────────────────────────────────────
            waView.mediaIndex.forEach { entry ->
                mediaEntryMap[entry.messageId] = normalizeMediaEntry(entry)
            }

            // ── Enrich messages (populate @Transient fields from related lists) ──
            val reactionsMap = waView.reactions.groupBy { it.messageId }
            val pollsMap     = waView.polls.associateBy { it.messageId }
            val enrichedMessages = waView.messages.map { msg ->
                val media    = mediaEntryMap[msg.id]
                val poll     = pollsMap[msg.id]
                msg.copy(
                    mediaFilePath = media?.relativePath ?: "",
                    mediaMimeType = media?.mimeType     ?: "",
                    mediaCaption  = media?.caption      ?: "",
                    mediaName     = media?.fileName     ?: "",
                    mediaSize     = media?.size         ?: 0L,
                    reactions     = reactionsMap[msg.id] ?: emptyList(),
                    pollQuestion  = poll?.question ?: "",
                    pollOptions   = poll?.options?.map { PollOption(it.optionName, it.voteTotal) } ?: emptyList()
                )
            }

            // ── Index messages by chatId ─────────────────────────────────────
            val byChat = mutableMapOf<Long, MutableList<Message>>()
            for (msg in enrichedMessages) byChat.getOrPut(msg.chatId) { mutableListOf() }.add(msg)
            byChat.forEach { (chatId, list) ->
                list.sortBy { it.timestamp }
                messagesByChatId[chatId] = list
            }
            allMessagesCache = byChat.values.asSequence().flatten().toList()
            val starredIds = waView.starredMessages.toHashSet()
            starredMessagesCache = allMessagesCache.filter { it.id in starredIds }
            emitSync(step = "Preparing viewer data...")
            log("[SYNC] Loaded: ${waView.chats.size} chats, ${enrichedMessages.size} messages, ${waView.mediaIndex.size} media entries")
            log("[SYNC] Contacts: ${waView.contacts.size} | Groups: ${waView.groups.size} | Reactions: ${waView.reactions.size}")

            // ── Sync log: data inventory ─────────────────────────────────────
            logDataInventory(waView)

            // ── Avatars (synchronous, Phase 1 — UI blocked until done) ───────
            val totalAvatars = zipPathIndex.keys.count { it.startsWith("avatars/") }
            log("[SYNC] Syncing $totalAvatars profile photos...")
            emitSync(isSyncing = true, step = "Syncing profile photos...", progress = 0, total = totalAvatars)
            extractAllAvatars(zipFile)
            log("[OK]   Profile photos synced: ${avatarCache.size} / $totalAvatars")
            currentLogSummary = currentLogSummary.copy(avatarsSynced = avatarCache.size)
            emitSync(isSyncing = true, step = "Chats ready — syncing media in background...",
                logSummary = currentLogSummary)
            phase2Job = repoScope.launch { runPhase2(waView) }

            waView
        }
    }

    // ─── PHASE 2 — background media resolution ────────────────────────────────

    private suspend fun runPhase2(waView: WaViewFile) {
        log("[SYNC] Phase 2: resolving media paths from mediaIndex...")
        val mediaMessages = allMessagesCache.filter { it.mediaFilePath.isNotEmpty() }
        val total = mediaMessages.size
        emitSync(isSyncing = true, step = "Resolving media...", progress = 0, total = total)

        var resolved = 0; var failed = 0; var fallbackResolved = 0
        val smartLogs = mutableListOf<SmartResolveLog>()

        for (msg in mediaMessages) {
            yield()
            if (messageMediaIndex.containsKey(msg.id)) { resolved++; continue }

            // ── Fast-path: exact lookup, no logging overhead ──────────────────
            val exactHit = if (msg.mediaFilePath.isNotEmpty())
                zipPathIndex["media/${msg.mediaFilePath}".lowercase()]
            else null

            if (exactHit != null) {
                messageMediaIndex[msg.id] = exactHit
                resolved++
            } else {
                // ── Full fallback with step-by-step logging ───────────────────
                val steps = mutableListOf<String>()
                steps.add("[PATH] ${msg.mediaFilePath.ifEmpty { "<empty>" }}")
                val fallbackHeader = resolveMediaHeader(msg, steps)

                if (fallbackHeader != null) {
                    messageMediaIndex[msg.id] = fallbackHeader
                    resolved++
                    fallbackResolved++
                    val classification = classifyResolution(steps)
                    smartLogs.add(SmartResolveLog(msg.id, msg.mediaFilePath, steps.toList(), classification, fallbackHeader.fileName))
                    log("[OK]   Media #${msg.id} → $classification via fallback → ${fallbackHeader.fileName.substringAfterLast('/')}")
                } else {
                    failed++
                    deletedMediaSet[msg.id] = true
                    smartLogs.add(SmartResolveLog(msg.id, msg.mediaFilePath, steps.toList(), "DELETED", null))

                    // Brief log entry (full steps are in smartResolveLogs)
                    val entry = mediaEntryMap[msg.id]
                    when {
                        entry == null || entry.relativePath.isEmpty() ->
                            log("[INFO] Media #${msg.id}: no path in data.json")
                        entry.status == "skipped" ->
                            log("[SKIP] Media #${msg.id}: ${entry.relativePath.substringAfterLast('/')} — not extracted during export")
                        entry.transferred >= 2 ->
                            log("[WARN] Media #${msg.id}: ${entry.relativePath.substringAfterLast('/')} — DELETED (all 6 steps failed)")
                        else ->
                            log("[INFO] Media #${msg.id}: ${entry.relativePath.substringAfterLast('/')} — never downloaded in WA")
                    }
                }
            }

            if ((resolved + failed) % 50 == 0) {
                lastProgressTime = System.currentTimeMillis()
                emitSync(isSyncing = true,
                    step = "Syncing media — ${resolved}/${total} resolved${if (failed > 0) ", $failed DELETED" else ""}${if (fallbackResolved > 0) ", $fallbackResolved via fallback" else ""}",
                    progress = resolved, total = total,
                    verificationState = VerificationState(total, resolved, mediaCache.size, failed),
                    logSummary = currentLogSummary.copy(mediaResolvedInZip = resolved, mediaNotFoundInZip = failed, resolvedViaFallback = fallbackResolved))
            }
        }

        val finalSummary = currentLogSummary.copy(
            mediaResolvedInZip  = resolved,
            mediaNotFoundInZip  = failed,
            resolvedViaFallback = fallbackResolved,
            smartResolveLogs    = smartLogs
        )
        currentLogSummary = finalSummary

        log("[SYNC] Phase 2 complete — resolved: $resolved / $total${if (fallbackResolved > 0) " ($fallbackResolved via fallback)" else ""}${if (failed > 0) ", DELETED: $failed" else ""}")
        if (fallbackResolved > 0) {
            val byClass = smartLogs.filter { it.result != "DELETED" }.groupBy { it.result }
            log("[OK]   Smart recovery: ${byClass["MOVED"]?.size ?: 0} MOVED, ${byClass["RENAMED"]?.size ?: 0} RENAMED, ${byClass["EXT_FIXED"]?.size ?: 0} EXT_FIXED")
        }
        if (failed > 0) {
            log("[WARN] $failed files DELETED — not found after all 6 fallback steps")
            log("[INFO] Full step-by-step details in 'Smart Recovery Logs' section of the log viewer")
        }
        log("[OK]   Media sync done.")
        val stepSuffix = buildString {
            if (fallbackResolved > 0) append(" · $fallbackResolved rescued")
            if (failed > 0) append(" · $failed deleted")
        }
        emitSync(
            isSyncing = false,
            step = "✓ Ready — ${waView.chats.size} chats · $resolved media resolved$stepSuffix",
            progress = total, total = total, isComplete = true,
            verificationState = VerificationState(total, resolved, mediaCache.size, failed),
            logSummary = finalSummary
        )
    }

    // ─── PHASE 3 — per-chat extraction ────────────────────────────────────────

    suspend fun loadChatMedia(chatId: Long): Map<Long, File> = withContext(Dispatchers.IO) {
        // FIX: wait for Phase 2 to fully complete so messageMediaIndex is 100% populated
        if (!syncState.value.isComplete) {
            phase2Job?.join()
        }

        if (chatReadySet[chatId] == true) {
            val messages = messagesByChatId[chatId] ?: emptyList()
            val ids = messages.map { it.id }.toSet()
            // Verify cache is actually complete — count resolvable files missing from cache
            val missingCount = messages.count { msg ->
                msg.mediaFilePath.isNotEmpty() &&
                !mediaCache.containsKey(msg.id) &&
                !deletedMediaSet.containsKey(msg.id) &&
                messageMediaIndex.containsKey(msg.id)
            }
            if (missingCount == 0) {
                return@withContext mediaCache.filterKeys { it in ids }
            }
            // Missing files detected — invalidate and re-extract
            log("[CACHE] Chat $chatId: $missingCount missing files, re-extracting...")
            chatReadySet.remove(chatId)
        }
        queueMutex.withLock { if (!highPriorityQueue.contains(chatId)) highPriorityQueue.addFirst(chatId) }

        val messages      = messagesByChatId[chatId] ?: emptyList()
        val mediaMessages = messages.filter { it.mediaFilePath.isNotEmpty() }
        if (mediaMessages.isEmpty()) {
            queueMutex.withLock { highPriorityQueue.remove(chatId) }
            chatReadySet[chatId] = true
            return@withContext emptyMap()
        }

        log("Chat $chatId: extracting ${mediaMessages.size} media (HIGH PRIORITY)")
        val extracted = AtomicInteger(0)
        val failed    = AtomicInteger(0)
        lastProgressTime = System.currentTimeMillis()

        mediaMessages.chunked(6).forEach { batch ->
            if (System.currentTimeMillis() - lastProgressTime > 25_000) {
                log("STALL detected during chat $chatId extraction — continuing")
                lastProgressTime = System.currentTimeMillis()
            }
            coroutineScope {
                batch.map { msg ->
                    async {
                        if (mediaCache.containsKey(msg.id)) {
                            mediaFailureReasons.remove(msg.id)
                            extracted.incrementAndGet()
                            return@async
                        }
                        if (deletedMediaSet.containsKey(msg.id)) {
                            mediaFailureReasons.remove(msg.id)
                            failed.incrementAndGet()
                            return@async
                        }
                        val header = messageMediaIndex[msg.id] ?: resolveMediaHeader(msg)
                        if (header == null) {
                            mediaFailureReasons.remove(msg.id)
                            failed.incrementAndGet()
                            return@async
                        }
                        if (!messageMediaIndex.containsKey(msg.id)) messageMediaIndex[msg.id] = header
                        val cacheFile = buildCacheFile(msg.mediaFilePath, mediaCacheDir)
                        getLock("media_${msg.id}").withLock {
                            if (cacheFile.exists() && cacheFile.length() > 0) {
                                mediaCache[msg.id] = cacheFile
                                mediaFailureReasons.remove(msg.id)
                                extracted.incrementAndGet()
                                lastProgressTime = System.currentTimeMillis(); return@withLock
                            }
                            val firstAttempt = extractToFile(header, cacheFile)
                            val finalAttempt = if (!firstAttempt.success && firstAttempt.failureReason != MediaFailureReason.STORAGE_FULL) {
                                extractToFile(header, cacheFile)
                            } else {
                                firstAttempt
                            }
                            if (finalAttempt.success) {
                                mediaCache[msg.id] = cacheFile
                                mediaFailureReasons.remove(msg.id)
                                extracted.incrementAndGet()
                                lastProgressTime = System.currentTimeMillis()
                            } else {
                                finalAttempt.failureReason?.let { mediaFailureReasons[msg.id] = it }
                                failed.incrementAndGet()
                            }
                        }
                    }
                }.awaitAll()
            }
        }
        queueMutex.withLock { highPriorityQueue.remove(chatId) }
        chatReadySet[chatId] = true
        val chatName = currentWaViewFile?.chats?.firstOrNull { it.id == chatId }?.let {
            if (it.isGroup) it.subject.ifEmpty { "Group" } else it.jid.substringBefore("@")
        } ?: "Chat $chatId"
        log("[CACHE] $chatName — cached: ${extracted.get()}${if (failed.get() > 0) ", failed: ${failed.get()}" else ""}")
        val ids = messages.map { it.id }.toSet()
        mediaCache.filterKeys { it in ids }
    }

    suspend fun awaitSyncComplete() {
        if (!syncState.value.isComplete) {
            phase2Job?.join()
        }
    }

    fun isChatReady(chatId: Long): Boolean = chatReadySet[chatId] == true

    // ─── AVATARS ──────────────────────────────────────────────────────────────

    private suspend fun extractAllAvatars(zipFile: ZipFile) {
        val headers = zipPathIndex.entries.filter { it.key.startsWith("avatars/") }.map { it.value }
        log("Extracting ${headers.size} avatars...")
        val extractedCount = AtomicInteger(0)
        coroutineScope {
            headers.map { header ->
                async {
                    val rawName   = header.fileName.substringAfterLast("/")
                    val jidKey    = avatarFilenameToJid(rawName)
                    val cacheFile = File(avatarCacheDir, "${rawName}.jpg")
                    getLock("avatar_$rawName").withLock {
                        if (!cacheFile.exists() || cacheFile.length() == 0L) extractToFile(header, cacheFile)
                        if (cacheFile.exists() && cacheFile.length() > 0) {
                            cacheAvatarAliases(jidKey, cacheFile)
                        }
                    }
                    val done = extractedCount.incrementAndGet()
                    if (done % 25 == 0 || done == headers.size) {
                        emitSync(step = "Syncing profile photos...", progress = done, total = headers.size)
                    }
                }
            }.awaitAll()
        }
        log("Avatars done: ${avatarCache.size}")
    }

    private fun avatarFilenameToJid(rawName: String): String {
        if (rawName == "me.j" || rawName == "me") return "me"
        val s = rawName.removeSuffix(".j")
        val i = s.lastIndexOf('_')
        return if (i > 0) "${s.substring(0, i)}@${s.substring(i + 1)}" else s
    }

    private fun buildAvatarJsonAlias(jid: String): String {
        val trimmed = jid.trim().lowercase()
        if (trimmed == "me" || trimmed == "lid_me") return "me"
        val phone = trimmed.substringBefore("@")
        val server = trimmed.substringAfter("@", "").replace(".", "_").replace("@", "_")
        return if (server.isNotEmpty()) "${phone}_${server}_j.jpeg" else phone
    }

    private fun normalizeAvatarKey(jid: String): String =
        jid.trim()
            .substringBefore("@")
            .lowercase()

    private fun avatarLookupKeys(jid: String): List<String> {
        val trimmed = jid.trim().lowercase()
        if (trimmed.isEmpty()) return emptyList()
        val normalized = normalizeAvatarKey(trimmed)
        val own = currentWaViewFile?.exportInfo?.phoneNumber?.removePrefix("+")?.lowercase() ?: ""
        return buildList {
            add(trimmed)
            add(normalized)
            if (trimmed == "me" || trimmed == "lid_me" || normalized == "lid_me") {
                add("me")
                add("lid_me")
            }
            if (normalized.isNotEmpty()) {
                add(normalized)
                add("$normalized@s.whatsapp.net")
                add("$normalized@g.us")
            }
            if (own.isNotEmpty() && normalized == own) {
                add("me")
                add("lid_me")
                add(own)
                add("$own@s.whatsapp.net")
            }
        }.distinct()
    }

    private fun cacheAvatarAliases(jid: String, file: File) {
        avatarLookupKeys(jid).forEach { key ->
            if (key.isNotEmpty()) {
                avatarCache.putIfAbsent(key, file)
            }
        }
        val jsonAlias = buildAvatarJsonAlias(jid)
        if (jsonAlias.isNotEmpty()) {
            avatarCache.putIfAbsent(jsonAlias, file)
        }
    }

    fun getAvatarFile(jid: String): File? {
        val directKey = jid.trim().lowercase()
        if (directKey.isEmpty()) return null
        avatarCache[directKey]
            ?.takeIf { it.exists() && it.length() > 0L }
            ?.let { return it }
        val phone = normalizeAvatarKey(directKey).substringBefore("@")
        if (phone == "0") return null   // WhatsApp Business identity — no real avatar
        avatarLookupKeys(directKey).forEach { key ->
            avatarCache[key]?.takeIf { it.exists() && it.length() > 0L }?.let { return it }
        }
        return null
    }

    // ─── MEDIA RESOLUTION ─────────────────────────────────────────────────────
    // 6-step smart resolver with optional step-by-step logging.
    // Phase 2 calls with a steps list (full logging) after exact pre-check fails.
    // Phase 3 calls with steps=null (silent, full fallback chain still runs).

    private fun removeMediaPrefix(path: String): String {
        val normalized = path.replace("\\", "/").trim()
        return if (normalized.lowercase().startsWith("media/")) normalized.substring(6) else normalized
    }

    private fun mediaFolderHintFromMime(mimeType: String): String = when {
        mimeType.startsWith("image/") -> "images"
        mimeType.startsWith("video/") -> "video"
        mimeType.startsWith("audio/") -> "voice"
        mimeType.startsWith("application/") || mimeType.startsWith("text/") -> "documents"
        else -> ""
    }

    private fun inferRelativePathFromFileName(entry: MediaEntry): String {
        val fileNameLower = entry.fileName.lowercase()
        if (fileNameLower.isBlank()) return ""
        val candidates = fileNameIndex[fileNameLower].orEmpty()
        if (candidates.isEmpty()) return ""
        val chosen = if (candidates.size == 1) {
            candidates.first()
        } else {
            val hint = mediaFolderHintFromMime(entry.mimeType)
            val hinted = candidates.filter { it.fileName.lowercase().contains(hint) }
            if (hint.isNotEmpty() && hinted.size == 1) hinted.first() else return ""
        }
        return removeMediaPrefix(chosen.fileName)
    }

    private fun normalizeMediaEntry(entry: MediaEntry): MediaEntry {
        val normalizedPath = when {
            entry.relativePath.isNotBlank() -> removeMediaPrefix(entry.relativePath)
            else -> inferRelativePathFromFileName(entry)
        }
        val exactArchiveHit = normalizedPath.isNotEmpty() &&
            zipPathIndex.containsKey("media/$normalizedPath".lowercase())
        val normalizedStatus = when {
            entry.status == "skipped" -> "skipped"
            entry.status == "downloaded" -> "downloaded"
            exactArchiveHit -> "downloaded"
            else -> "missing"
        }
        val normalizedFileName = entry.fileName.ifEmpty {
            normalizedPath.substringAfterLast("/", "")
        }
        return if (
            normalizedPath != entry.relativePath ||
            normalizedStatus != entry.status ||
            normalizedFileName != entry.fileName
        ) {
            entry.copy(
                relativePath = normalizedPath,
                status = normalizedStatus,
                fileName = normalizedFileName
            )
        } else {
            entry
        }
    }

    private fun resolveMediaHeader(msg: Message, steps: MutableList<String>? = null): FileHeader? {
        var relativePath = msg.mediaFilePath
        if (relativePath.isEmpty()) {
            steps?.add("[STEP0] SKIP — empty path in data.json")
            return null
        }

        // ── STEP 1: Extension fix ─────────────────────────────────────────────
        // If filename has no extension, derive one from mime_type and try lookup
        val fileName = relativePath.substringAfterLast("/")
        if (!fileName.contains('.') && msg.mediaMimeType.isNotEmpty()) {
            val ext = mimeTypeToExtension(msg.mediaMimeType)
            if (ext.isNotEmpty()) {
                val fixedPath = "$relativePath.$ext"
                zipPathIndex["media/$fixedPath".lowercase()]?.let { h ->
                    steps?.add("[STEP1] ext_fix → HIT  added .$ext → ${h.fileName}")
                    return h
                }
                steps?.add("[STEP1] ext_fix → MISS  tried: $fixedPath")
                relativePath = fixedPath   // carry ext-fixed path into subsequent steps
            } else {
                steps?.add("[STEP1] ext_fix → SKIP  no ext mapping for mime: ${msg.mediaMimeType}")
            }
        } else {
            steps?.add("[STEP1] ext_fix → SKIP  filename already has extension")
        }

        // ── STEP 2: Exact path match ──────────────────────────────────────────
        zipPathIndex["media/$relativePath".lowercase()]?.let { h ->
            steps?.add("[STEP2] exact   → HIT  → ${h.fileName}")
            return h
        }
        steps?.add("[STEP2] exact   → MISS  tried: media/$relativePath")

        // ── STEP 3: Sent folder variants ──────────────────────────────────────
        val dir  = relativePath.substringBeforeLast("/")
        val file = relativePath.substringAfterLast("/")
        if (relativePath.contains("/Sent/", ignoreCase = true)) {
            // 3a: file is IN Sent/ — try without Sent/
            val withoutSent = relativePath.replace(Regex("/Sent/", RegexOption.IGNORE_CASE), "/")
            zipPathIndex["media/$withoutSent".lowercase()]?.let { h ->
                steps?.add("[STEP3a] sent_remove → HIT  → ${h.fileName}")
                return h
            }
            steps?.add("[STEP3a] sent_remove → MISS  tried: media/$withoutSent")
        } else if (dir.isNotEmpty()) {
            // 3b: file NOT in Sent/ — try adding Sent/ before filename
            val withSent = "$dir/Sent/$file"
            zipPathIndex["media/$withSent".lowercase()]?.let { h ->
                steps?.add("[STEP3b] sent_add   → HIT  → ${h.fileName}")
                return h
            }
            steps?.add("[STEP3b] sent_add   → MISS  tried: media/$withSent")
        }

        // ── STEP 4: Global filename search (any folder) ───────────────────────
        val fileNameLower = file.lowercase()
        val candidates = fileNameIndex[fileNameLower]
        if (!candidates.isNullOrEmpty()) {
            val hint = folderHintFromPath(relativePath)
            val best = if (hint.isNotEmpty())
                candidates.firstOrNull { it.fileName.lowercase().contains(hint) } ?: candidates[0]
            else candidates[0]
            steps?.add("[STEP4] global   → HIT  ${candidates.size} match(es) → ${best.fileName}")
            return best
        }
        steps?.add("[STEP4] global   → MISS  filename: $fileNameLower")

        // ── STEP 5: Fuzzy — filename without extension ────────────────────────
        val fileNameNoExt = fileNameLower.substringBeforeLast(".")
        if (fileNameNoExt.length >= 5) {
            val fuzzy = fileNameIndex.entries
                .firstOrNull { (k, _) -> k.substringBeforeLast(".") == fileNameNoExt }
            if (fuzzy != null) {
                val h = fuzzy.value.firstOrNull()
                if (h != null) {
                    steps?.add("[STEP5] fuzzy    → HIT  prefix match → ${h.fileName}")
                    return h
                }
            }
            steps?.add("[STEP5] fuzzy    → MISS  prefix: $fileNameNoExt")
        } else {
            steps?.add("[STEP5] fuzzy    → SKIP  name too short")
        }

        // ── STEP 6: Last resort — date/WA-counter pattern ─────────────────────
        val keyPattern = Regex("""\d{8}""").find(fileNameNoExt)?.value
            ?: Regex("""wa\d{4}""", RegexOption.IGNORE_CASE).find(fileNameNoExt)?.value
        if (keyPattern != null) {
            val kl = keyPattern.lowercase()
            val patternMatch = fileNameIndex.entries
                .firstOrNull { (k, _) -> k.contains(kl) && k != fileNameLower }
            if (patternMatch != null) {
                val h = patternMatch.value.firstOrNull()
                if (h != null) {
                    steps?.add("[STEP6] pattern  → HIT  key: $keyPattern → ${h.fileName}")
                    return h
                }
            }
            steps?.add("[STEP6] pattern  → MISS  key: $keyPattern")
        } else {
            steps?.add("[STEP6] pattern  → SKIP  no 8-digit date or WA#### in: $fileNameNoExt")
        }

        return null
    }

    /** Map mime type to a file extension for Step 1. */
    private fun mimeTypeToExtension(mime: String): String = when {
        "jpeg" in mime || mime == "image/jpg"  -> "jpg"
        "image/png"  in mime -> "png"
        "image/gif"  in mime -> "gif"
        "image/webp" in mime -> "webp"
        "video/mp4"  in mime -> "mp4"
        "video/3gpp" in mime -> "3gp"
        "audio/ogg"  in mime || "audio/opus" in mime -> "opus"
        "audio/mpeg" in mime || "audio/mp3"  in mime -> "mp3"
        "audio/aac"  in mime -> "aac"
        "application/pdf" in mime -> "pdf"
        "officedocument.wordprocessing" in mime || "application/msword" in mime -> "docx"
        "officedocument.spreadsheet"    in mime || "vnd.ms-excel"       in mime -> "xlsx"
        "text/plain" in mime -> "txt"
        "text/html"  in mime -> "html"
        else -> ""
    }

    /** Classify how a resolution was achieved based on which step logged "HIT". */
    private fun classifyResolution(steps: List<String>): String = when {
        steps.any { "[STEP1]" in it && "HIT" in it }  -> "EXT_FIXED"
        steps.any { "[STEP3a]" in it && "HIT" in it } -> "MOVED"
        steps.any { "[STEP3b]" in it && "HIT" in it } -> "MOVED"
        steps.any { "[STEP4]" in it && "HIT" in it }  -> "MOVED"
        steps.any { "[STEP5]" in it && "HIT" in it }  -> "RENAMED"
        steps.any { "[STEP6]" in it && "HIT" in it }  -> "RENAMED"
        else -> "EXACT"
    }

    private fun folderHintFromPath(path: String): String {
        val l = path.lowercase()
        return when {
            l.contains("images")     -> "images"
            l.contains("video")      -> "video"
            l.contains("voice")      -> "voice"
            l.contains("audio")      -> "audio"
            l.contains("documents") || l.contains("/doc") -> "documents"
            l.contains("sticker")    -> "sticker"
            l.contains("gif")        -> "gif"
            else -> ""
        }
    }

    // ─── EXTRACTION ───────────────────────────────────────────────────────────

    // suspend + zipReadMutex: serializes ALL zip reads so concurrent Phase-3 coroutines
    // never call ZipFile.getInputStream() at the same time (zip4j is not thread-safe).
    private suspend fun extractToFile(header: FileHeader, dest: File): ExtractResult {
        return zipReadMutex.withLock {
            try {
                val zf = currentZipFile ?: return@withLock ExtractResult(success = false, failureReason = MediaFailureReason.EXTRACT_FAILED)
                dest.parentFile?.mkdirs()
                zf.getInputStream(header).use { i ->
                    dest.outputStream().use { o -> i.copyTo(o, bufferSize = 128 * 1024) }
                }
                ExtractResult(success = dest.exists() && dest.length() > 0)
            } catch (e: Exception) {
                android.util.Log.e("WASensai", "[ViewerRepo] Extract failed [${header.fileName}]: ${e.message}")
                try { dest.delete() } catch (_: Exception) {}
                ExtractResult(success = false, failureReason = classifyExtractFailure(e))
            }
        }
    }

    private fun classifyExtractFailure(error: Exception): MediaFailureReason {
        val message = error.message.orEmpty().lowercase()
        return if (
            "enospc" in message ||
            "no space left on device" in message ||
            mediaCacheDir.usableSpace <= 0L
        ) {
            MediaFailureReason.STORAGE_FULL
        } else {
            MediaFailureReason.EXTRACT_FAILED
        }
    }

    // ─── DATA INVENTORY LOG ───────────────────────────────────────────────────

    private fun logDataInventory(waView: WaViewFile) {
        val m  = waView.messages
        val mi = mediaEntryMap.values.toList()

        // ── Smart media categorization ────────────────────────────────────────
        // "downloaded"  = file is in the archive ✅
        // "skipped"     = user unchecked that type during export ⊘
        // "missing" + relativePath="" = media message with no file path in DB → never downloaded in WA
        // "missing" + relativePath!="" + transferred<2 = path known, but file never saved to device
        // "missing" + relativePath!="" + transferred>=2 = was on device, not found in archive ⚠️
        val inArchive        = mi.count { it.status == "downloaded" }
        val skippedByUser    = mi.count { it.status == "skipped" }
        val missingEntries   = mi.filter { it.status == "missing" }
        val neverDownloaded  = missingEntries.count { it.relativePath.isEmpty() || it.transferred < 2 }
        val trulyMissing     = missingEntries.count { it.relativePath.isNotEmpty() && it.transferred >= 2 }

        // ── Unused data (in data.json but no viewer UI yet) ───────────────────
        val unused = mutableListOf<String>()
        if (waView.polls.isNotEmpty())          unused.add("Polls: ${waView.polls.size} (data available, viewer UI not built yet)")
        if (waView.labels.isNotEmpty())         unused.add("Labels: ${waView.labels.size} (data available, viewer UI not built yet)")
        if (waView.labeledMessages.isNotEmpty()) unused.add("Labeled messages: ${waView.labeledMessages.size}")
        if (waView.mentions.isNotEmpty())       unused.add("Mentions: ${waView.mentions.size} (data available, viewer UI not built yet)")
        if (waView.vcards.isNotEmpty())         unused.add("Contact cards (vCards): ${waView.vcards.size} (data available, viewer UI not built yet)")
        if (waView.statuses.isNotEmpty())       unused.add("Status updates: ${waView.statuses.size} — metadata only, actual media not exported")
        if (waView.messageEdits.isNotEmpty())   unused.add("Message edit history: ${waView.messageEdits.size} edits (data available, viewer UI not built yet)")
        if (waView.groups.any { it.inviteLink.isNotEmpty() }) unused.add("Group invite links: ${waView.groups.count { it.inviteLink.isNotEmpty() }} groups")
        if (waView.polls.any { it.votes.isNotEmpty() })      unused.add("Poll votes: ${waView.polls.sumOf { it.votes.size }} individual votes")
        if (waView.callLogs.any { it.participants.isNotEmpty() }) unused.add("Group call participants: ${waView.callLogs.sumOf { it.participants.size }} entries")

        // ── Store in summary for UI ───────────────────────────────────────────
        currentLogSummary = SyncLogSummary(
            messagesLoaded       = m.size,
            chatsLoaded          = waView.chats.size,
            contactsLoaded       = waView.contacts.size,
            groupsLoaded         = waView.groups.size,
            avatarsSynced        = avatarCache.size,
            mediaTotal           = mi.size,
            mediaInArchive       = inArchive,
            mediaSkippedByUser   = skippedByUser,
            mediaNeverDownloaded = neverDownloaded,
            mediaMissing         = trulyMissing,
            unusedDataItems      = unused
        )

        // ── Write to log buffer ───────────────────────────────────────────────
        log("[INFO] ─── DATA INVENTORY ─────────────────────────────")
        log("[INFO] Messages:        ${m.size}")
        log("[INFO]   Text:          ${m.count { it.messageType == 0 }}")
        log("[INFO]   Media:         ${m.count { it.messageType in listOf(1,2,3,9,13,20) }}")
        log("[INFO]   Location:      ${m.count { it.messageType == 5 }}")
        log("[INFO]   Deleted:       ${m.count { it.isDeleted }} (${m.count { it.deletedForEveryone }} deleted for everyone)")
        log("[INFO]   System events: ${m.count { it.isSystem }}")
        log("[INFO]   Forwarded:     ${m.count { it.isForwarded }}")
        log("[INFO]   Starred:       ${waView.starredMessages.size}")
        log("[INFO]   Broadcast:     ${m.count { it.broadcast != 0 }}")
        log("[INFO] Chats:           ${waView.chats.size} (${waView.chats.count { it.isGroup }} groups, ${waView.chats.count { !it.isGroup }} individuals)")
        log("[INFO]   Pinned:        ${waView.chats.count { it.pinned }}")
        log("[INFO]   Archived:      ${waView.chats.count { it.archived }}")
        log("[INFO]   Muted:         ${waView.chats.count { it.mutedUntil != 0L }}")
        log("[INFO] Contacts:        ${waView.contacts.size}")
        log("[INFO] Groups:          ${waView.groups.size}")
        log("[INFO]   With invite:   ${waView.groups.count { it.inviteLink.isNotEmpty() }}")
        log("[INFO] ─── MEDIA ──────────────────────────────────────")
        log("[INFO] Total media entries: ${mi.size}")
        log("[OK]   In archive:          $inArchive  ← ready to view")
        if (skippedByUser > 0)    log("[SKIP] Not extracted (user):  $skippedByUser  ← you unchecked this type during export")
        if (neverDownloaded > 0)  log("[INFO] Never downloaded:      $neverDownloaded  ← media in chats but never opened in WhatsApp")
        if (trulyMissing > 0)     log("[WARN] Missing (error):       $trulyMissing  ← was on device but not found in archive")
        log("[INFO] ─── EXTRA DATA IN archive ─────────────────────")
        log("[INFO] Reactions:       ${waView.reactions.size}")
        log("[INFO] Polls:           ${waView.polls.size} (${waView.polls.sumOf { it.votes.size }} votes)")
        log("[INFO] Call logs:       ${waView.callLogs.size} (${waView.callLogs.sumOf { it.participants.size }} group call participants)")
        log("[INFO] Labels:          ${waView.labels.size} (${waView.labeledMessages.size} labeled messages)")
        log("[INFO] Mentions:        ${waView.mentions.size}")
        log("[INFO] Contact cards:   ${waView.vcards.size}")
        log("[INFO] Status updates:  ${waView.statuses.size} (metadata only)")
        log("[INFO] Message edits:   ${waView.messageEdits.size} edits across ${waView.messageEdits.map { it.messageId }.distinct().size} messages")
        if (unused.isNotEmpty()) {
            log("[INFO] ─── NOT SHOWN IN VIEWER (data available) ─────")
            unused.forEach { log("[INFO] $it") }
        }
        log("[INFO] ─────────────────────────────────────────────────")

        emitSync(logSummary = currentLogSummary)
    }

    // ─── JUNK FILTER ──────────────────────────────────────────────────────────

    private fun isJunk(pathLower: String, fileNameLower: String): Boolean {
        if (fileNameLower in junkFileNames) return true
        if (junkExtensions.any { fileNameLower.endsWith(it) }) return true
        if (junkPathSegments.any { pathLower.contains(it) }) return true
        return false
    }

    // ─── CACHE FILE NAMING ────────────────────────────────────────────────────

    private fun buildCacheFile(mediaFilePath: String, dir: File): File =
        File(dir, buildCacheFileName(mediaFilePath))

    private fun buildCacheFileName(fullPath: String): String {
        val fileName = fullPath.substringAfterLast('/', fullPath)
        val safeName = sanitizeFileName(fileName)
        val hash     = shortHash(fullPath)
        val combined = "${hash}_${safeName}"
        val maxLen   = 120
        return if (combined.length > maxLen) {
            val ext  = safeName.substringAfterLast('.', "")
            val base = combined.take(maxLen - ext.length - 1)
            if (ext.isNotEmpty()) "$base.$ext" else base
        } else combined
    }

    private fun sanitizeFileName(name: String): String {
        var cleaned = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC)
        cleaned = cleaned.trim().replace(Regex("\\s+"), "_")
        cleaned = cleaned.replace(Regex("[\\\\/:*?\"<>|]"), "")
        cleaned = cleaned.trimStart('.')
        if (cleaned.isBlank()) cleaned = "file"
        return cleaned
    }

    private fun shortHash(input: String): String {
        val md     = java.security.MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    // ─── DATA ACCESS ──────────────────────────────────────────────────────────

    fun getMessagesByChatId(chatId: Long): List<Message> = messagesByChatId[chatId] ?: emptyList()

    fun getMediaEntry(messageId: Long): MediaEntry? = mediaEntryMap[messageId]

    fun getMediaFailureReason(messageId: Long): MediaFailureReason? = mediaFailureReasons[messageId]

    fun getStarredMessages(): List<Message> = starredMessagesCache

    fun getMediaMessages(chatId: Long): List<Message> =
        getMessagesByChatId(chatId).filter { it.messageType in listOf(1, 2, 3, 9, 13, 20) && it.mediaFilePath.isNotEmpty() }

    suspend fun searchMessages(query: String): List<Message> = withContext(Dispatchers.Default) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.lowercase()
            allMessagesCache.filter {
                it.textData.lowercase().contains(q) ||
                    it.mediaCaption.lowercase().contains(q) ||
                    it.mediaName.lowercase().contains(q) ||
                    it.senderName.lowercase().contains(q)
            }
        }
    }

    suspend fun searchChats(query: String): List<Chat> = withContext(Dispatchers.Default) {
        if (query.isBlank()) emptyList()
        else { val q = query.lowercase(); currentWaViewFile?.chats?.filter { it.subject.lowercase().contains(q) } ?: emptyList() }
    }

    // ─── LOGGING ──────────────────────────────────────────────────────────────

    private fun log(msg: String) {
        val line = "[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] $msg"
        android.util.Log.d("WASensai", "[ViewerRepo] $line")
        synchronized(logBuffer) {
            if (logBuffer.size >= 200) logBuffer.removeFirst()
            logBuffer.addLast(line)
            logSnapshot = logBuffer.toList()
        }
    }

    private fun emitSync(
        isSyncing: Boolean = _syncState.value.isSyncing,
        step: String = _syncState.value.currentStep,
        progress: Int = _syncState.value.progress,
        total: Int = _syncState.value.total,
        isComplete: Boolean = _syncState.value.isComplete,
        verificationState: VerificationState = _syncState.value.verificationState,
        logSummary: SyncLogSummary = _syncState.value.logSummary
    ) {
        _syncState.value = SyncState(isSyncing, step, progress, total,
            logSnapshot, isComplete, verificationState, logSummary)
    }

    // ─── CLEAR ────────────────────────────────────────────────────────────────

    fun clear() {
        phase2Job?.cancel(); phase2Job = null
        try { currentZipFile?.close() } catch (_: Exception) {}
        currentZipFile = null; currentWaViewFile = null
        zipPathIndex.clear(); fileNameIndex.clear()
        mediaEntryMap.clear(); messageMediaIndex.clear(); deletedMediaSet.clear()
        messagesByChatId.clear(); avatarCache.clear(); mediaCache.clear(); mediaFailureReasons.clear()
        allMessagesCache = emptyList()
        starredMessagesCache = emptyList()
        chatReadySet.clear(); highPriorityQueue.clear(); extractionLocks.clear()
        synchronized(logBuffer) { logBuffer.clear() }
        logSnapshot = emptyList()
        currentLogSummary = SyncLogSummary()
        _syncState.value = SyncState()
        importWorkDir.deleteRecursively()
        mediaCacheDir.deleteRecursively()
        avatarCacheDir.deleteRecursively()
        File(context.cacheDir, "waview_import").deleteRecursively()
        File(context.cacheDir, "wa_media").deleteRecursively()
        File(context.cacheDir, "wa_avatars").deleteRecursively()
    }
}
