package com.mazin.wasensai.export

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.mazin.wasensai.BuildConfig
import com.mazin.wasensai.data.repository.ExtractSnapshot
import com.mazin.wasensai.data.repository.ExtractRepository
import com.mazin.wasensai.root.RootFileAccess
import com.topjohnwu.superuser.Shell
import com.mazin.wasensai.utils.DateUtils
import com.mazin.wasensai.utils.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extractRepository: ExtractRepository,
    private val rootFileAccess: RootFileAccess
) {
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
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).also { it.mkdirs() }

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
            log("EXTRACT", "Reading archive metadata from DB...")
            onProgress(0.15f, "[2/7] Converting databases → data.json...")
            onProgress(0.15f, "  Reading contacts, chats, metadata...")
            val snapshot = extractRepository.collectExportSnapshot { msg -> onProgress(0.15f, "  $msg") }
            log("EXTRACT", "Done: ${snapshot.exportInfo.totalChats} chats, ${snapshot.exportInfo.totalMessages} messages, ${snapshot.mediaIndex.size} media entries")
            onProgress(0.25f, "  ── data.json contents ──")
            onProgress(0.25f, "  Messages:     ${snapshot.exportInfo.totalMessages}")
            onProgress(0.25f, "  Chats:        ${snapshot.exportInfo.totalChats}")
            onProgress(0.25f, "  Contacts:     ${snapshot.contacts.size}")
            onProgress(0.25f, "  Groups:       ${snapshot.groups.size}")
            onProgress(0.25f, "  Reactions:    ${snapshot.reactions.size}")
            onProgress(0.25f, "  Polls:        ${snapshot.polls.size}")
            onProgress(0.25f, "  Call logs:    ${snapshot.callLogs.size}")
            onProgress(0.25f, "  Labels:       ${snapshot.labels.size}")
            onProgress(0.25f, "  Mentions:     ${snapshot.mentions.size}")
            onProgress(0.25f, "  VCards:       ${snapshot.vcards.size}")
            onProgress(0.25f, "  Statuses:     ${snapshot.statuses.size}")
            onProgress(0.25f, "  Msg edits:    ${snapshot.messageEdits.size}")
            onProgress(0.25f, "  Media index:  ${snapshot.mediaIndex.size} entries")
            onProgress(0.26f, "  Starred:      ${snapshot.starredMessages.size}")

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

            // ─── Step 4: Build media selection without staging full media ─────
            var mediaCount = 0
            var selectedMediaPaths = emptyList<String>()
            var selectedMediaPathSet = emptySet<String>()
            var mediaSourcePath: String? = null
            if (includeAnyMedia) {
                log("MEDIA", "Scanning media source...")
                onProgress(0.46f, "[4/7] Preparing media selection...")
                onProgress(0.46f, "  Source: /sdcard/Android/media/...")
                val mediaSource = rootFileAccess.listMediaSource { msg ->
                    log("MEDIA", msg); onProgress(0.5f, "  $msg")
                }

                if (mediaSource != null) {
                    mediaSourcePath = mediaSource.sourcePath
                    val junkDirs = buildSet {
                        addAll(listOf(".shared", "backups", "databases", ".trash", ".wamocache", ".statuses"))
                        if (!includeThumbnails) {
                            add(".stickerthumbs")
                            add(".thumbs")
                        }
                    }
                    val junkExts = setOf(".tmp", ".chk", ".enc", ".thumb", ".log", ".crypt14", ".bak")
                    var junkRemoved = 0
                    var skippedImages = 0
                    var skippedVideos = 0
                    var skippedAudio = 0
                    var skippedDocs = 0
                    var skippedOthers = 0

                    selectedMediaPaths = mediaSource.relativePaths.filter { relativePath ->
                        val normalized = relativePath.replace("\\", "/")
                        val lowerPath = normalized.lowercase(Locale.ROOT)
                        val fileNameLower = File(normalized).name.lowercase(Locale.ROOT)
                        val isJunkDir = junkDirs.any { lowerPath.contains("/$it/") || lowerPath.startsWith("$it/") }
                        val isJunkExt = junkExts.any { fileNameLower.endsWith(it) }
                        if (isJunkDir || isJunkExt) {
                            junkRemoved++
                            return@filter false
                        }

                        val ext = normalized.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        val keep = isTypeIncluded(ext, includeImages, includeVideos, includeAudio, includeDocs, includeOthers, imageExts, videoExts, audioExts, docExts)
                        if (!keep) {
                            when {
                                ext in imageExts -> skippedImages++
                                ext in videoExts -> skippedVideos++
                                ext in audioExts -> skippedAudio++
                                ext in docExts -> skippedDocs++
                                else -> skippedOthers++
                            }
                        }
                        keep
                    }

                    selectedMediaPathSet = selectedMediaPaths.map { it.replace("\\", "/").lowercase(Locale.ROOT) }.toHashSet()
                    mediaCount = selectedMediaPaths.size

                    val imgCount = selectedMediaPaths.count { it.substringAfterLast('.', "").lowercase(Locale.ROOT) in imageExts }
                    val vidCount = selectedMediaPaths.count { it.substringAfterLast('.', "").lowercase(Locale.ROOT) in videoExts }
                    val audCount = selectedMediaPaths.count { it.substringAfterLast('.', "").lowercase(Locale.ROOT) in audioExts }
                    val docCount2 = selectedMediaPaths.count { it.substringAfterLast('.', "").lowercase(Locale.ROOT) in docExts }
                    val otherCount2 = selectedMediaPaths.count { it.substringAfterLast('.', "").lowercase(Locale.ROOT) !in knownExts }

                    if (junkRemoved > 0) {
                        log("MEDIA", "Excluded $junkRemoved junk files")
                        onProgress(0.55f, "  Junk excluded: $junkRemoved files")
                        if (!includeThumbnails) onProgress(0.55f, "  Thumbnail folders (.thumbs, .stickerthumbs) — skipped")
                    }

                    val totalSkipped = skippedImages + skippedVideos + skippedAudio + skippedDocs + skippedOthers
                    if (totalSkipped > 0) {
                        log("MEDIA", "Skipped $totalSkipped files (unchecked categories)")
                        onProgress(0.58f, "  ── Removed (unchecked) ──")
                        if (skippedImages > 0) onProgress(0.58f, "  Images:    $skippedImages files removed")
                        if (skippedVideos > 0) onProgress(0.58f, "  Videos:    $skippedVideos files removed")
                        if (skippedAudio  > 0) onProgress(0.58f, "  Audio:     $skippedAudio files removed")
                        if (skippedDocs   > 0) onProgress(0.58f, "  Documents: $skippedDocs files removed")
                        if (skippedOthers > 0) onProgress(0.58f, "  Others:    $skippedOthers files removed")
                    }

                    log("MEDIA", "Selected $mediaCount files for direct zip")
                    onProgress(0.62f, "  ── Final media selection ──")
                    onProgress(0.62f, "  Images:    $imgCount files")
                    onProgress(0.62f, "  Videos:    $vidCount files")
                    onProgress(0.62f, "  Audio:     $audCount files")
                    onProgress(0.62f, "  Documents: $docCount2 files")
                    onProgress(0.62f, "  Others:    $otherCount2 files")
                    onProgress(0.62f, "  Total:     $mediaCount files")
                }
            } else {
                onProgress(0.46f, "[4/7] Media — all types skipped by user")
            }

            // ─── Step 5: Update mediaIndex status ───────────────────────────
            // Now we know which source files are selected for the archive — stamp each entry.
            onProgress(0.63f, "[5/7] Linking media paths...")
            val mediaSelectedSnapshot = updateMediaIndexStatus(snapshot, selectedMediaPathSet, includeImages, includeVideos, includeAudio, includeDocs, includeOthers, imageExts, videoExts, audioExts, docExts)
            var finalSnapshot = mediaSelectedSnapshot
            val downloaded = finalSnapshot.mediaIndex.count { it.status == "downloaded" }
            val missing    = finalSnapshot.mediaIndex.count { it.status == "missing" }
            val skipped    = finalSnapshot.mediaIndex.count { it.status == "skipped" }
            log("MEDIA_INDEX", "downloaded=$downloaded missing=$missing skipped=$skipped")
            onProgress(0.65f, "Media linked: $downloaded | Missing: $missing | Skipped: $skipped")

            val fileName = DateUtils.formatExportFilename(finalSnapshot.exportInfo.phoneNumber.ifEmpty { "unknown" })
            val baseName = fileName.removeSuffix(".waview")
            var outputFile = File(downloadsDir, fileName)
            var counter = 1
            while (outputFile.exists()) {
                outputFile = File(downloadsDir, "$baseName($counter).waview")
                counter++
            }
            val partialOutputFile = File(downloadsDir, "${outputFile.name}.partial").also {
                if (it.exists()) it.delete()
            }

            try {
                // ─── Step 7: Zip ─────────────────────────────────────────────
                log("ZIP", "Starting .waview archive creation directly in Downloads...")
                onProgress(0.76f, "[7/7] Assembling .waview archive...")
                onProgress(0.76f, "Creating archive directly in Downloads...")
                val zipFile = ZipFile(partialOutputFile)
                val storeParams = ZipParameters().apply { compressionMethod = CompressionMethod.STORE }

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

                if (includeAnyMedia && mediaSourcePath != null && selectedMediaPaths.isNotEmpty()) {
                    log("ZIP", "Streaming ${selectedMediaPaths.size} media files directly from source...")
                    onProgress(0.78f, "[7/7] Zipping ${selectedMediaPaths.size} media files...")
                    val stageDir = File(cacheDir, "zip_stage").also { it.mkdirs() }
                    val stageFile = File(stageDir, "current_media")
                    val zippedMediaPaths = LinkedHashSet<String>()
                    selectedMediaPaths.forEachIndexed { index, relativePath ->
                        val ext = relativePath.substringAfterLast('.', "")
                        val stagedTarget = if (ext.isNotBlank()) File(stageDir, "current_media.$ext") else stageFile
                        if (rootFileAccess.copyMediaFileTo(mediaSourcePath, relativePath, stagedTarget)) {
                            try {
                                zipFile.addFile(stagedTarget, ZipParameters().apply {
                                    compressionMethod = CompressionMethod.STORE
                                    fileNameInZip = "media/${relativePath.replace("\\", "/")}"
                                })
                                zippedMediaPaths += relativePath.replace("\\", "/").lowercase(Locale.ROOT)
                            } finally {
                                stagedTarget.delete()
                            }
                        }
                        if ((index + 1) % 100 == 0 || index + 1 == selectedMediaPaths.size) {
                            val pct = (index + 1) * 100 / selectedMediaPaths.size
                            log("ZIP", "Media direct-zip progress: $pct% | ${index + 1}/${selectedMediaPaths.size}")
                            onProgress(0.78f + (pct / 100f) * 0.10f, "[7/7] Zipping: $pct% (${index + 1}/${selectedMediaPaths.size})")
                        }
                    }
                    if (zippedMediaPaths.size != selectedMediaPaths.size) {
                        finalSnapshot = updateMediaIndexStatus(snapshot, zippedMediaPaths, includeImages, includeVideos, includeAudio, includeDocs, includeOthers, imageExts, videoExts, audioExts, docExts)
                    } else {
                        finalSnapshot = mediaSelectedSnapshot
                    }
                    log("ZIP", "Media zip complete: ${zippedMediaPaths.size}/${selectedMediaPaths.size}")
                    onProgress(0.89f, "[7/7] Media zipped successfully!")
                }

                // ─── Step 6: Serialize data.json (AFTER actual media zip status is known) ───
                log("JSON", "Streaming data.json (format v3)...")
                onProgress(0.90f, "[6/7] Serializing data.json (format v3)...")
                onProgress(0.90f, "  Streaming export data to data.json...")
                val dataJsonFile = extractRepository.writeDataJson(cacheDir, finalSnapshot) { msg ->
                    onProgress(0.92f, "  $msg")
                }
                val jsonSizeKb = dataJsonFile.length() / 1024
                zipFile.addFile(dataJsonFile, ZipParameters().apply {
                    compressionMethod = CompressionMethod.STORE
                    fileNameInZip = "data.json"
                })
                log("JSON", "data.json written: ${jsonSizeKb}KB")
                onProgress(0.94f, "  data.json written: ${jsonSizeKb}KB ✓")
                onProgress(0.94f, "  Status: downloaded=${finalSnapshot.mediaIndex.count { it.status == "downloaded" }} skipped=${finalSnapshot.mediaIndex.count { it.status == "skipped" }} missing=${finalSnapshot.mediaIndex.count { it.status == "missing" }}")

                // ─── Finalize in Downloads ────────────────────────────────────
                log("SAVE", "Finalizing archive in Downloads...")
                onProgress(0.95f, "Finalizing archive in Downloads...")
                if (!partialOutputFile.renameTo(outputFile)) {
                    val mvResult = Shell.cmd(
                        "mv \"${partialOutputFile.absolutePath}\" \"${outputFile.absolutePath}\""
                    ).exec()
                    if (!mvResult.isSuccess) {
                        throw Exception("Failed to finalize in Downloads: ${mvResult.err.joinToString()}")
                    }
                }
                MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), null, null)

                val finalSizeMb = outputFile.length() / (1024 * 1024)
                val totalTimeSec = (System.currentTimeMillis() - exportStartTime) / 1000
                val totalTimeStr = if (totalTimeSec >= 60) "${totalTimeSec / 60}m ${totalTimeSec % 60}s" else "${totalTimeSec}s"
                log("SUMMARY", "File: ${outputFile.name} (${finalSizeMb}MB) in $totalTimeStr")

                val finalDownloaded = finalSnapshot.mediaIndex.count { it.status == "downloaded" }
                val finalMissing = finalSnapshot.mediaIndex.count { it.status == "missing" }
                onProgress(0.97f, "---")
                onProgress(0.97f, "EXPORT SUMMARY")
                onProgress(0.97f, "Chats: ${finalSnapshot.exportInfo.totalChats} | Messages: ${finalSnapshot.exportInfo.totalMessages}")
                onProgress(0.97f, "Media: $mediaCount files | Linked: $finalDownloaded | Missing: $finalMissing")
                onProgress(0.97f, "Reactions: ${finalSnapshot.reactions.size} | Polls: ${finalSnapshot.polls.size}")
                onProgress(0.97f, "Groups: ${finalSnapshot.groups.size} | Contacts: ${finalSnapshot.contacts.size}")
                onProgress(0.97f, "File: ${outputFile.name} (${finalSizeMb}MB)")
                onProgress(0.97f, "Time: $totalTimeStr")

                FileUtils.cleanCache(context)
                onProgress(1.0f, "Export complete! ${finalSizeMb}MB saved to Downloads")
                outputFile
            } catch (e: Exception) {
                partialOutputFile.delete()
                throw e
            }
        }
    }

    // ─── Stamp mediaIndex with downloaded / missing / skipped ─────────────────

    private fun updateMediaIndexStatus(
        snapshot: ExtractSnapshot,
        selectedRelPaths: Set<String>,
        includeImages: Boolean,
        includeVideos: Boolean,
        includeAudio:  Boolean,
        includeDocs:   Boolean,
        includeOthers: Boolean,
        imageExts: Set<String>,
        videoExts: Set<String>,
        audioExts: Set<String>,
        docExts:   Set<String>
    ): ExtractSnapshot {
        val updatedIndex = snapshot.mediaIndex.map { entry ->
            val relLower = entry.relativePath.lowercase(Locale.ROOT)
            val ext = entry.relativePath.substringAfterLast(".").lowercase(Locale.ROOT)
            val status = when {
                relLower.isNotEmpty() && selectedRelPaths.contains(relLower) -> "downloaded"
                !isTypeIncluded(ext, includeImages, includeVideos, includeAudio, includeDocs, includeOthers, imageExts, videoExts, audioExts, docExts) -> "skipped"
                else -> "missing"
            }
            if (status != entry.status) entry.copy(status = status) else entry
        }
        return snapshot.copy(mediaIndex = updatedIndex)
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
