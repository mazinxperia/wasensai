package com.mazin.wasensai.export

import android.content.Context
import android.os.Environment
import com.mazin.wasensai.BuildConfig
import com.mazin.wasensai.data.model.WaViewFile
import com.mazin.wasensai.data.repository.ExtractRepository
import com.mazin.wasensai.root.RootFileAccess
import com.topjohnwu.superuser.Shell
import com.mazin.wasensai.utils.DateUtils
import com.mazin.wasensai.utils.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extractRepository: ExtractRepository,
    private val rootFileAccess: RootFileAccess
) {
    private val json = Json { prettyPrint = true }

    private fun log(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("WASensai", "[$tag] $msg")
        }
    }

    suspend fun runExport(
        includeAvatars:    Boolean,
        includeImages:     Boolean,
        includeVideos:     Boolean,
        includeAudio:      Boolean,
        includeDocs:       Boolean,
        includeOthers:     Boolean = true,
        includeThumbnails: Boolean = false,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val exportStartTime = System.currentTimeMillis()
            val cacheDir = FileUtils.getCacheDir(context)
            val includeAnyMedia = includeImages || includeVideos || includeAudio || includeDocs || includeOthers

            val imageExts = setOf("jpg", "jpeg", "png", "webp")
            val videoExts = setOf("mp4", "mkv", "3gp", "avi")
            val audioExts = setOf("mp3", "opus", "m4a", "aac", "ogg")
            val docExts   = setOf("pdf", "doc", "docx", "xls", "xlsx", "xlsm", "ppt", "pptx", "txt", "zip", "html", "msg")
            val knownExts = imageExts + videoExts + audioExts + docExts

            // ─── Selection summary ───────────────────────────────────────────
            onProgress(0.01f, "══ EXPORT STARTED ══")
            onProgress(0.01f, "DBs: msgstore.db + wa.db (always)")
            onProgress(0.01f, "Avatars:    ${if (includeAvatars) "✓ included" else "✗ skipped"}")
            onProgress(0.01f, "Images:     ${if (includeImages) "✓ included" else "✗ skipped"}")
            onProgress(0.01f, "Videos:     ${if (includeVideos) "✓ included" else "✗ skipped"}")
            onProgress(0.01f, "Audio:      ${if (includeAudio) "✓ included" else "✗ skipped"}")
            onProgress(0.01f, "Documents:  ${if (includeDocs) "✓ included" else "✗ skipped"}")
            onProgress(0.01f, "Others:     ${if (includeOthers) "✓ included" else "✗ skipped"}")
            onProgress(0.01f, "Thumbnails: ${if (includeThumbnails) "✓ included" else "✗ skipped"}")
            onProgress(0.04f, "──────────────────────────────")

            // ─── Step 1: Copy DBs ────────────────────────────────────────────
            log("DB", "Copying msgstore.db and wa.db...")
            onProgress(0.05f, "[1/7] Copying WhatsApp databases...")
            onProgress(0.05f, "  Copying msgstore.db from /data/data/...")
            rootFileAccess.copyMsgstoreDb(cacheDir)
            val msgDbMb = File(cacheDir, "msgstore.db").length() / (1024 * 1024)
            onProgress(0.07f, "  msgstore.db → ${msgDbMb}MB ✓")
            onProgress(0.07f, "  Copying wa.db from /data/data/...")
            rootFileAccess.copyWaDb(cacheDir)
            val waDbMb  = File(cacheDir, "wa.db").length() / (1024 * 1024)
            log("SIZE", "msgstore.db: ${msgDbMb}MB | wa.db: ${waDbMb}MB")
            onProgress(0.09f, "  wa.db → ${waDbMb}MB ✓")
            onProgress(0.1f, "Databases ready (${msgDbMb + waDbMb}MB total)")

            // ─── Step 2: Extract data from DB ────────────────────────────────
            log("EXTRACT", "Reading messages and all metadata from DB...")
            onProgress(0.15f, "[2/7] Converting databases → data.json...")
            onProgress(0.15f, "  Reading contacts, chats, messages...")
            val waView = extractRepository.extractData { msg -> onProgress(0.15f, "  $msg") }
            log("EXTRACT", "Done: ${waView.exportInfo.totalChats} chats, ${waView.exportInfo.totalMessages} messages, ${waView.mediaIndex.size} media entries")
            onProgress(0.25f, "  ── data.json contents ──")
            onProgress(0.25f, "  Messages:     ${waView.exportInfo.totalMessages}")
            onProgress(0.25f, "  Chats:        ${waView.exportInfo.totalChats}")
            onProgress(0.25f, "  Contacts:     ${waView.contacts.size}")
            onProgress(0.25f, "  Groups:       ${waView.groups.size}")
            onProgress(0.25f, "  Reactions:    ${waView.reactions.size}")
            onProgress(0.25f, "  Polls:        ${waView.polls.size}")
            onProgress(0.25f, "  Call logs:    ${waView.callLogs.size}")
            onProgress(0.25f, "  Labels:       ${waView.labels.size}")
            onProgress(0.25f, "  Mentions:     ${waView.mentions.size}")
            onProgress(0.25f, "  VCards:       ${waView.vcards.size}")
            onProgress(0.25f, "  Statuses:     ${waView.statuses.size}")
            onProgress(0.25f, "  Msg edits:    ${waView.messageEdits.size}")
            onProgress(0.25f, "  Media index:  ${waView.mediaIndex.size} entries")
            onProgress(0.26f, "  Starred:      ${waView.starredMessages.size}")

            // ─── Step 3: Copy avatars ─────────────────────────────────────────
            if (includeAvatars) {
                log("AVATARS", "Starting avatar copy...")
                onProgress(0.35f, "[3/7] Copying profile photos...")
                onProgress(0.35f, "  Source: /data/data/.../files/Avatars/")
                rootFileAccess.copyAvatars(cacheDir) { msg ->
                    log("AVATARS", msg); onProgress(0.4f, "  $msg")
                }
                var avatarCount = 0
                var avatarBytes = 0L
                File(cacheDir, "avatars").walkTopDown().forEach { file ->
                    if (file.isFile) {
                        avatarCount += 1
                        avatarBytes += file.length()
                    }
                }
                val avatarSizeMb = avatarBytes / (1024 * 1024)
                log("AVATARS", "Done: $avatarCount avatars (${avatarSizeMb}MB)")
                onProgress(0.45f, "  $avatarCount profile photos copied (${avatarSizeMb}MB) ✓")
            } else {
                onProgress(0.35f, "[3/7] Profile photos — skipped by user")
            }

            // ─── Step 4: Copy media ───────────────────────────────────────────
            var mediaCount = 0
            if (includeAnyMedia) {
                log("MEDIA", "Starting media copy...")
                onProgress(0.46f, "[4/7] Copying media files...")
                onProgress(0.46f, "  Source: /sdcard/Android/media/...")
                rootFileAccess.copyMedia(cacheDir) { msg ->
                    log("MEDIA", msg); onProgress(0.5f, "  $msg")
                }

                val mediaDir = File(cacheDir, "media")

                // Show per-folder breakdown before filtering
                val folderStats = mediaDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }
                if (!folderStats.isNullOrEmpty()) {
                    onProgress(0.52f, "  ── Copied folders ──")
                    for (folder in folderStats) {
                        val files = folder.walkTopDown().filter { it.isFile }.toList()
                        val sizeMb = files.sumOf { it.length() } / (1024 * 1024)
                        onProgress(0.52f, "  ${folder.name}/ (${files.size} files, ${sizeMb}MB)")
                    }
                }

                // Remove junk — keep .thumbs/.stickerthumbs if thumbnails are included
                val junkDirs = buildSet {
                    addAll(listOf(".shared", "backups", "databases", ".trash", ".wamocache", ".statuses"))
                    if (!includeThumbnails) { add(".stickerthumbs"); add(".thumbs") }
                }
                val junkExts = setOf(".tmp", ".chk", ".enc", ".thumb", ".log", ".crypt14", ".bak")
                var junkRemoved = 0
                mediaDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val pathLower = file.absolutePath.lowercase().replace("\\", "/")
                    val isJunkDir = junkDirs.any { pathLower.contains("/$it/") }
                    val isJunkExt = junkExts.any { file.name.lowercase().endsWith(it) }
                    if (isJunkDir || isJunkExt) { file.delete(); junkRemoved++ }
                }
                if (junkRemoved > 0) {
                    log("MEDIA", "Removed $junkRemoved junk files")
                    onProgress(0.55f, "  Junk removed: $junkRemoved files (.enc, .tmp, .crypt14, etc.)")
                    if (!includeThumbnails) onProgress(0.55f, "  Thumbnail folders (.thumbs, .stickerthumbs) — skipped")
                }

                // Filter by user selection — remove unchecked categories
                var skippedImages = 0; var skippedVideos = 0; var skippedAudio = 0
                var skippedDocs = 0; var skippedOthers = 0
                mediaDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val ext = file.extension.lowercase()
                    val keep = when {
                        ext in imageExts -> includeImages
                        ext in videoExts -> includeVideos
                        ext in audioExts -> includeAudio
                        ext in docExts   -> includeDocs
                        else             -> includeOthers
                    }
                    if (!keep) {
                        when {
                            ext in imageExts -> skippedImages++
                            ext in videoExts -> skippedVideos++
                            ext in audioExts -> skippedAudio++
                            ext in docExts   -> skippedDocs++
                            else             -> skippedOthers++
                        }
                        file.delete()
                    }
                }
                val totalSkipped = skippedImages + skippedVideos + skippedAudio + skippedDocs + skippedOthers
                log("MEDIA", "Skipped $totalSkipped files (unchecked categories)")
                if (totalSkipped > 0) {
                    onProgress(0.58f, "  ── Removed (unchecked) ──")
                    if (skippedImages > 0) onProgress(0.58f, "  Images:    $skippedImages files removed")
                    if (skippedVideos > 0) onProgress(0.58f, "  Videos:    $skippedVideos files removed")
                    if (skippedAudio  > 0) onProgress(0.58f, "  Audio:     $skippedAudio files removed")
                    if (skippedDocs   > 0) onProgress(0.58f, "  Documents: $skippedDocs files removed")
                    if (skippedOthers > 0) onProgress(0.58f, "  Others:    $skippedOthers files removed")
                }

                val allMediaFiles = mediaDir.walkTopDown().filter { it.isFile }.toList()
                mediaCount = allMediaFiles.size

                val imgCount    = allMediaFiles.count { it.extension.lowercase() in imageExts }
                val vidCount    = allMediaFiles.count { it.extension.lowercase() in videoExts }
                val audCount    = allMediaFiles.count { it.extension.lowercase() in audioExts }
                val docCount2   = allMediaFiles.count { it.extension.lowercase() in docExts }
                val otherCount2 = allMediaFiles.count { it.extension.lowercase() !in knownExts }
                val totalMediaMb = allMediaFiles.sumOf { it.length() } / (1024 * 1024)
                log("MEDIA", "Total: $mediaCount files (${totalMediaMb}MB)")
                onProgress(0.62f, "  ── Final media selection ──")
                onProgress(0.62f, "  Images:    $imgCount files")
                onProgress(0.62f, "  Videos:    $vidCount files")
                onProgress(0.62f, "  Audio:     $audCount files")
                onProgress(0.62f, "  Documents: $docCount2 files")
                onProgress(0.62f, "  Others:    $otherCount2 files")
                onProgress(0.62f, "  Total:     $mediaCount files (${totalMediaMb}MB)")
            } else {
                onProgress(0.46f, "[4/7] Media — all types skipped by user")
            }

            // ─── Step 5: Update mediaIndex status ───────────────────────────
            // Now we know which files made it into the mediaDir — stamp each entry.
            onProgress(0.63f, "[5/7] Linking media paths...")
            val finalWaView = updateMediaIndexStatus(waView, cacheDir, includeImages, includeVideos, includeAudio, includeDocs, includeOthers, imageExts, videoExts, audioExts, docExts)
            val downloaded = finalWaView.mediaIndex.count { it.status == "downloaded" }
            val missing    = finalWaView.mediaIndex.count { it.status == "missing" }
            val skipped    = finalWaView.mediaIndex.count { it.status == "skipped" }
            log("MEDIA_INDEX", "downloaded=$downloaded missing=$missing skipped=$skipped")
            onProgress(0.65f, "Media linked: $downloaded | Missing: $missing | Skipped: $skipped")

            // ─── Step 6: Serialize data.json (AFTER media status is known) ───
            log("JSON", "Serializing data.json (format v3)...")
            onProgress(0.70f, "[6/7] Serializing data.json (format v3)...")
            onProgress(0.70f, "  Encoding all extracted data...")
            val dataJsonFile = File(cacheDir, "data.json")
            dataJsonFile.writeText(json.encodeToString(WaViewFile.serializer(), finalWaView))
            val jsonSizeKb = dataJsonFile.length() / 1024
            log("JSON", "data.json written: ${jsonSizeKb}KB")
            onProgress(0.75f, "  data.json written: ${jsonSizeKb}KB ✓")
            onProgress(0.75f, "  Status: downloaded=${finalWaView.mediaIndex.count { it.status == "downloaded" }} skipped=${finalWaView.mediaIndex.count { it.status == "skipped" }} missing=${finalWaView.mediaIndex.count { it.status == "missing" }}")

            // ─── Step 7: Zip ─────────────────────────────────────────────────
            log("ZIP", "Starting .waview archive creation...")
            onProgress(0.76f, "[7/7] Assembling .waview archive...")
            val fileName = DateUtils.formatExportFilename(finalWaView.exportInfo.phoneNumber.ifEmpty { "unknown" })
            val tempZip = File(context.cacheDir, "export_temp.zip").also { if (it.exists()) it.delete() }
            val zipFile = ZipFile(tempZip)
            val storeParams = ZipParameters().apply { compressionMethod = CompressionMethod.STORE }

            zipFile.addFile(dataJsonFile, storeParams)
            onProgress(0.76f, "  + data.json (${dataJsonFile.length() / 1024}KB)")
            log("ZIP", "data.json added")

            val msgstoreFile = File(cacheDir, "msgstore.db")
            if (msgstoreFile.exists() && msgstoreFile.length() > 0) {
                val msgstoreMb = msgstoreFile.length() / (1024 * 1024)
                onProgress(0.77f, "  + msgstore.db (${msgstoreMb}MB) adding...")
                zipFile.addFile(msgstoreFile, ZipParameters().apply {
                    compressionMethod = CompressionMethod.STORE
                    fileNameInZip = "msgstore.db"
                })
                log("ZIP", "msgstore.db added (${msgstoreMb}MB)")
                onProgress(0.77f, "  + msgstore.db ✓")
            }

            val waDbFile = File(cacheDir, "wa.db")
            if (waDbFile.exists() && waDbFile.length() > 0) {
                val waDbMbSize = waDbFile.length() / (1024 * 1024)
                onProgress(0.77f, "  + wa.db (${waDbMbSize}MB) adding...")
                zipFile.addFile(waDbFile, ZipParameters().apply {
                    compressionMethod = CompressionMethod.STORE
                    fileNameInZip = "wa.db"
                })
                log("ZIP", "wa.db added (${waDbMbSize}MB)")
                onProgress(0.77f, "  + wa.db ✓")
            }

            val metaDir = File(cacheDir, "meta").also { it.mkdirs() }
            val versionFile = File(metaDir, "version.json")
            versionFile.writeText("{\n  \"formatVersion\": 3,\n  \"createdBy\": \"WA Sensai\"\n}")
            zipFile.addFile(versionFile, ZipParameters().apply {
                compressionMethod = CompressionMethod.STORE
                fileNameInZip = "meta/version.json"
            })
            onProgress(0.77f, "  + meta/version.json ✓")

            if (includeAvatars) {
                val avatarDir = File(cacheDir, "avatars")
                if (avatarDir.exists()) {
                    Shell.cmd(
                        "chown -R u0_a269 ${avatarDir.absolutePath} 2>/dev/null || true",
                        "chmod -R 755 ${avatarDir.absolutePath}"
                    ).exec()
                    val files = avatarDir.listFiles()
                    if (!files.isNullOrEmpty()) {
                        onProgress(0.77f, "  + avatars/ — zipping ${files.size} photos...")
                        val safeDir = File(cacheDir, "avatars_safe").also { it.mkdirs() }
                        files.forEach { file ->
                            try { file.copyTo(File(safeDir, file.name.replace("@", "_")), overwrite = true) } catch (_: Exception) {}
                        }
                        val safeFiles = safeDir.listFiles() ?: emptyArray()
                        log("ZIP", "Zipping ${safeFiles.size} avatars...")
                        var avatarZipped = 0
                        safeFiles.forEachIndexed { i, file ->
                            try {
                                if (file.exists()) {
                                    zipFile.addFile(file, ZipParameters().apply {
                                        compressionMethod = CompressionMethod.STORE
                                        fileNameInZip = "avatars/${file.name}"
                                    })
                                    avatarZipped++
                                }
                                if ((i + 1) % 100 == 0 || i + 1 == safeFiles.size)
                                    onProgress(0.77f, "  avatars: ${i + 1}/${safeFiles.size}...")
                            } catch (_: Exception) {}
                        }
                        log("ZIP", "All avatars zipped")
                        onProgress(0.78f, "  + avatars/ — $avatarZipped files ✓")
                    }
                }
            }

            if (includeAnyMedia) {
                val mediaDir = File(cacheDir, "media")
                if (mediaDir.exists()) {
                    Shell.cmd("chmod -R 755 ${mediaDir.absolutePath}").exec()
                    val totalFiles = mediaDir.walkTopDown().filter { it.isFile }.count()
                    log("ZIP", "Zipping $totalFiles media files...")
                    onProgress(0.78f, "[7/7] Zipping $totalFiles media files...")
                    var lastPct = -1
                    zipFile.isRunInThread = true
                    zipFile.addFolder(mediaDir, storeParams)
                    val pm = zipFile.progressMonitor
                    while (pm.state != net.lingala.zip4j.progress.ProgressMonitor.State.READY) {
                        val pct = pm.percentDone
                        if (pct != lastPct) {
                            val filesDone = totalFiles * pct / 100
                            log("ZIP", "Progress: $pct% | $filesDone/$totalFiles")
                            onProgress(0.78f + (pct / 100f) * 0.12f, "[7/7] Zipping: $pct% ($filesDone/$totalFiles)")
                            lastPct = pct
                        }
                        delay(100)
                    }
                    zipFile.isRunInThread = false
                    if (pm.result == net.lingala.zip4j.progress.ProgressMonitor.Result.ERROR)
                        throw Exception("Zip failed: ${pm.exception?.message}")
                    log("ZIP", "Media zip complete!")
                    onProgress(0.91f, "[7/7] Media zipped successfully!")
                }
            }

            // ─── Save to Downloads ────────────────────────────────────────────
            log("SAVE", "Moving archive to Downloads...")
            onProgress(0.93f, "Saving to Downloads...")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).also { it.mkdirs() }
            val baseName = fileName.removeSuffix(".waview")
            var outputFile = File(downloadsDir, fileName)
            var counter = 1
            while (outputFile.exists()) { outputFile = File(downloadsDir, "$baseName($counter).waview"); counter++ }
            val mvResult = Shell.cmd("mv ${tempZip.absolutePath} ${outputFile.absolutePath}").exec()
            if (!mvResult.isSuccess) throw Exception("Failed to move to Downloads: ${mvResult.err.joinToString()}")

            val finalSizeMb = outputFile.length() / (1024 * 1024)
            val totalTimeSec = (System.currentTimeMillis() - exportStartTime) / 1000
            val totalTimeStr = if (totalTimeSec >= 60) "${totalTimeSec / 60}m ${totalTimeSec % 60}s" else "${totalTimeSec}s"
            log("SUMMARY", "File: ${outputFile.name} (${finalSizeMb}MB) in $totalTimeStr")

            onProgress(0.97f, "---")
            onProgress(0.97f, "EXPORT SUMMARY")
            onProgress(0.97f, "Chats: ${finalWaView.exportInfo.totalChats} | Messages: ${finalWaView.exportInfo.totalMessages}")
            onProgress(0.97f, "Media: $mediaCount files | Linked: $downloaded | Missing: $missing")
            onProgress(0.97f, "Reactions: ${finalWaView.reactions.size} | Polls: ${finalWaView.polls.size}")
            onProgress(0.97f, "Groups: ${finalWaView.groups.size} | Contacts: ${finalWaView.contacts.size}")
            onProgress(0.97f, "File: ${outputFile.name} (${finalSizeMb}MB)")
            onProgress(0.97f, "Time: $totalTimeStr")

            FileUtils.cleanCache(context)
            onProgress(1.0f, "Export complete! ${finalSizeMb}MB saved to Downloads")
            outputFile
        }
    }

    // ─── Stamp mediaIndex with downloaded / missing / skipped ─────────────────

    private fun updateMediaIndexStatus(
        waView: WaViewFile,
        cacheDir: File,
        includeImages: Boolean,
        includeVideos: Boolean,
        includeAudio:  Boolean,
        includeDocs:   Boolean,
        includeOthers: Boolean,
        imageExts: Set<String>,
        videoExts: Set<String>,
        audioExts: Set<String>,
        docExts:   Set<String>
    ): WaViewFile {
        val mediaDir = File(cacheDir, "media")
        if (!mediaDir.exists()) return waView

        // Build a set of lowercase relative paths that actually exist in the cache dir
        val existingRelPaths = mediaDir.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(mediaDir).path.replace("\\", "/").lowercase() }
            .toHashSet()

        val updatedIndex = waView.mediaIndex.map { entry ->
            val relLower = entry.relativePath.lowercase()
            val ext = entry.relativePath.substringAfterLast(".").lowercase()
            val status = when {
                relLower.isNotEmpty() && existingRelPaths.contains(relLower) -> "downloaded"
                !isTypeIncluded(ext, includeImages, includeVideos, includeAudio, includeDocs, includeOthers, imageExts, videoExts, audioExts, docExts) -> "skipped"
                else -> "missing"
            }
            if (status != entry.status) entry.copy(status = status) else entry
        }
        return waView.copy(mediaIndex = updatedIndex)
    }

    private fun isTypeIncluded(
        ext: String,
        includeImages: Boolean, includeVideos: Boolean,
        includeAudio: Boolean, includeDocs: Boolean, includeOthers: Boolean,
        imageExts: Set<String>, videoExts: Set<String>,
        audioExts: Set<String>, docExts: Set<String>
    ): Boolean = when {
        ext in imageExts -> includeImages
        ext in videoExts -> includeVideos
        ext in audioExts -> includeAudio
        ext in docExts   -> includeDocs
        else             -> includeOthers
    }
}
