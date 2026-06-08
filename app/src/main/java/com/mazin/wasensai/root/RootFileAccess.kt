package com.mazin.wasensai.root

import android.content.Context
import android.content.pm.PackageManager
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class ScanResult(
    val msgstoreSize: Long,
    val waDbSize: Long,
    val avatarCount: Int,
    val avatarSize: Long,
    val mediaCount: Int,
    val mediaSize: Long,
    val totalSize: Long,
    val imagesCount: Int = 0,
    val imagesSizeMb: Long = 0,
    val videosCount: Int = 0,
    val videosSizeMb: Long = 0,
    val audioCount: Int = 0,
    val audioSizeMb: Long = 0,
    val docsCount: Int = 0,
    val docsSizeMb: Long = 0,
    val othersCount: Int = 0,
    val othersSizeMb: Long = 0,
    val thumbnailsCount: Int = 0,
    val thumbnailsSizeMb: Long = 0
)

data class MediaSourceInfo(
    val packageName: String,
    val sourcePath: String,
    val topLevelCounts: List<Pair<String, Int>>,
    val relativePaths: List<String>
)

@Singleton
class RootFileAccess @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun getWhatsAppPackage(): String {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo("com.whatsapp.w4b", 0)
            "com.whatsapp.w4b"
        } catch (e: PackageManager.NameNotFoundException) {
            "com.whatsapp"
        }
    }

    private val NS = "nsenter --mount=/proc/1/ns/mnt --"

    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            Shell.getShell { shell ->
                cont.resume(shell.isRoot)
            }
        }
    }

    suspend fun verifyWhatsAppAccess(): Boolean = withContext(Dispatchers.IO) {
        val pkg = getWhatsAppPackage()
        Shell.cmd("$NS stat /data/data/$pkg/databases/msgstore.db > /dev/null 2>&1 && echo OK").exec()
            .out.any { it.trim() == "OK" }
    }

    suspend fun scanFiles(): ScanResult = withContext(Dispatchers.IO) {
        val pkg = getWhatsAppPackage()

        val msgstoreSize = Shell.cmd("$NS stat -c%s /data/data/$pkg/databases/msgstore.db").exec()
            .out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L

        val waDbSize = Shell.cmd("$NS stat -c%s /data/data/$pkg/databases/wa.db").exec()
            .out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L

        val avatarCount = Shell.cmd("$NS ls /data/data/$pkg/files/Avatars/ 2>/dev/null | wc -l").exec()
            .out.firstOrNull()?.trim()?.toIntOrNull() ?: 0

        val avatarSize = Shell.cmd("$NS du -sb /data/data/$pkg/files/Avatars/ 2>/dev/null | cut -f1").exec()
            .out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L

        val mediaCount = Shell.cmd("find /sdcard/Android/media/$pkg -type f 2>/dev/null | wc -l").exec()
            .out.firstOrNull()?.trim()?.toIntOrNull() ?: 0

        val mediaSize = Shell.cmd("du -sb /sdcard/Android/media/$pkg 2>/dev/null | cut -f1").exec()
            .out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L

        val imageExts = listOf("jpg", "jpeg", "png", "webp")
        val videoExts = listOf("mp4", "mkv", "3gp", "avi")
        val audioExts = listOf("mp3", "opus", "m4a", "aac", "ogg")
        val docExts   = listOf("pdf", "doc", "docx", "xls", "xlsx", "xlsm", "ppt", "pptx", "txt", "zip", "html", "msg")
        val knownExts = imageExts + videoExts + audioExts + docExts

        fun countByExts(exts: List<String>): Int {
            val pattern = exts.joinToString(" -o ") { "-iname \"*.$it\"" }
            return Shell.cmd("find /sdcard/Android/media/$pkg -type f \\( $pattern \\) 2>/dev/null | wc -l")
                .exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
        }

        fun sizeByExts(exts: List<String>): Long {
            val pattern = exts.joinToString(" -o ") { "-iname \"*.$it\"" }
            val result = Shell.cmd("find /sdcard/Android/media/$pkg -type f \\( $pattern \\) -print0 2>/dev/null | xargs -0 du -sb 2>/dev/null | awk '{sum+=\$1} END{print sum}'")
                .exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            return result / (1024 * 1024)
        }

        val imagesCount  = countByExts(imageExts)
        val imagesSizeMb = sizeByExts(imageExts)
        val videosCount  = countByExts(videoExts)
        val videosSizeMb = sizeByExts(videoExts)
        val audioCount   = countByExts(audioExts)
        val audioSizeMb  = sizeByExts(audioExts)
        val docsCount    = countByExts(docExts)
        val docsSizeMb   = sizeByExts(docExts)
        val othersCount  = mediaCount - imagesCount - videosCount - audioCount - docsCount
        val othersSizeMb = (mediaSize / (1024 * 1024)) - imagesSizeMb - videosSizeMb - audioSizeMb - docsSizeMb

        // Thumbnails live in .thumbs/ and .stickerthumbs/ inside the media folder
        val thumbnailDirs = listOf(".thumbs", ".stickerthumbs")
        val thumbnailsCount = thumbnailDirs.sumOf { dir ->
            Shell.cmd("find /sdcard/Android/media/$pkg/$dir -type f 2>/dev/null | wc -l")
                .exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
        }
        val thumbnailsSizeMb = thumbnailDirs.sumOf { dir ->
            val result = Shell.cmd("du -sb /sdcard/Android/media/$pkg/$dir 2>/dev/null | cut -f1")
                .exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            result / (1024 * 1024)
        }

        ScanResult(
            msgstoreSize     = msgstoreSize,
            waDbSize         = waDbSize,
            avatarCount      = avatarCount,
            avatarSize       = avatarSize,
            mediaCount       = mediaCount,
            mediaSize        = mediaSize,
            totalSize        = msgstoreSize + waDbSize + avatarSize + mediaSize,
            imagesCount      = imagesCount,
            imagesSizeMb     = imagesSizeMb,
            videosCount      = videosCount,
            videosSizeMb     = videosSizeMb,
            audioCount       = audioCount,
            audioSizeMb      = audioSizeMb,
            docsCount        = docsCount,
            docsSizeMb       = docsSizeMb,
            othersCount      = if (othersCount < 0) 0 else othersCount,
            othersSizeMb     = if (othersSizeMb < 0) 0 else othersSizeMb,
            thumbnailsCount  = thumbnailsCount,
            thumbnailsSizeMb = thumbnailsSizeMb
        )
    }

    suspend fun copyMsgstoreDb(cacheDir: File): Boolean = withContext(Dispatchers.IO) {
        val pkg = getWhatsAppPackage()
        val dest = File(cacheDir, "msgstore.db")
        val result = Shell.cmd(
            "$NS cp /data/data/$pkg/databases/msgstore.db /data/local/tmp/msgstore.db",
            "chmod 644 /data/local/tmp/msgstore.db"
        ).exec()
        if (!result.isSuccess) return@withContext false
        File("/data/local/tmp/msgstore.db").copyTo(dest, overwrite = true)
        Shell.cmd("rm /data/local/tmp/msgstore.db").exec()
        dest.exists()
    }

    suspend fun copyWaDb(cacheDir: File): Boolean = withContext(Dispatchers.IO) {
        val pkg = getWhatsAppPackage()
        val dest = File(cacheDir, "wa.db")
        val result = Shell.cmd(
            "$NS cp /data/data/$pkg/databases/wa.db /data/local/tmp/wa.db",
            "chmod 644 /data/local/tmp/wa.db"
        ).exec()
        if (!result.isSuccess) return@withContext false
        File("/data/local/tmp/wa.db").copyTo(dest, overwrite = true)
        Shell.cmd("rm /data/local/tmp/wa.db").exec()
        dest.exists()
    }

    suspend fun copyAvatars(cacheDir: File, onProgress: (String) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        val pkg = getWhatsAppPackage()
        val destDir = File(cacheDir, "avatars").absolutePath
        val listResult = Shell.cmd("$NS ls /data/data/$pkg/files/Avatars/").exec()
        if (!listResult.isSuccess) return@withContext false
        val files = listResult.out.map { it.trim() }.filter { it.isNotEmpty() }
        onProgress("Copying ${files.size} profile photos...")
        Shell.cmd("mkdir -p $destDir").exec()
        var copied = 0
        var allOk = true
        for (filename in files) {
            val r = Shell.cmd("$NS cp /data/data/$pkg/files/Avatars/$filename $destDir/$filename && chmod 644 $destDir/$filename").exec()
            if (r.isSuccess) copied++ else allOk = false
            if (copied % 50 == 0 || copied == files.size)
                onProgress("Avatars: $copied/${files.size} copied...")
        }
        allOk
    }

    suspend fun copyMedia(cacheDir: File, onProgress: (String) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        val primaryPkg = getWhatsAppPackage()
        val destDir = File(cacheDir, "media").absolutePath
        Shell.cmd("mkdir -p $destDir").exec()

        // Try primary package first, then the other variant as fallback
        val fallbackPkg = if (primaryPkg == "com.whatsapp.w4b") "com.whatsapp" else "com.whatsapp.w4b"
        val candidates = listOf(primaryPkg, fallbackPkg)

        for (srcPkg in candidates) {
            val srcPath = "/sdcard/Android/media/$srcPkg"

            // Check if this path has any content
            val check = Shell.cmd("ls \"$srcPath\" 2>/dev/null | wc -l").exec()
            val entryCount = check.out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
            if (entryCount == 0) {
                onProgress("$srcPkg — no media found, trying next...")
                continue
            }

            // Report top-level folders for display
            val folderList = Shell.cmd("ls \"$srcPath\" 2>/dev/null").exec()
            for (line in folderList.out) {
                val name = line.trim()
                if (name.isNotEmpty()) {
                    val countOut = Shell.cmd("find \"$srcPath/$name\" -type f 2>/dev/null | wc -l").exec()
                    val count = countOut.out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
                    onProgress("Found: $name/ ($count files)")
                }
            }

            onProgress("Copying all media from $srcPkg/...")
            // Use the original bulk copy that reliably works
            val result = Shell.cmd("cp -rp \"$srcPath/.\" \"$destDir/\"").exec()
            onProgress(if (result.isSuccess) "Media copy done ✓" else "Media copy finished (check result)")
            return@withContext true
        }

        onProgress("No media found in any WhatsApp installation")
        false
    }

    suspend fun listMediaSource(onProgress: (String) -> Unit = {}): MediaSourceInfo? = withContext(Dispatchers.IO) {
        val primaryPkg = getWhatsAppPackage()
        val fallbackPkg = if (primaryPkg == "com.whatsapp.w4b") "com.whatsapp" else "com.whatsapp.w4b"
        val candidates = listOf(primaryPkg, fallbackPkg)

        for (srcPkg in candidates) {
            val srcPath = "/sdcard/Android/media/$srcPkg"
            val check = Shell.cmd("ls \"$srcPath\" 2>/dev/null | wc -l").exec()
            val entryCount = check.out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
            if (entryCount == 0) {
                onProgress("$srcPkg — no media found, trying next...")
                continue
            }

            val folderList = Shell.cmd("ls \"$srcPath\" 2>/dev/null").exec()
            val topLevelCounts = mutableListOf<Pair<String, Int>>()
            for (line in folderList.out) {
                val name = line.trim()
                if (name.isNotEmpty()) {
                    val countOut = Shell.cmd("find \"$srcPath/$name\" -type f 2>/dev/null | wc -l").exec()
                    val count = countOut.out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
                    topLevelCounts += name to count
                    onProgress("Found: $name/ ($count files)")
                }
            }

            val listResult = Shell.cmd("cd \"$srcPath\" && find . -type f 2>/dev/null | sed 's#^\\./##'").exec()
            if (!listResult.isSuccess) return@withContext null
            val relativePaths = listResult.out.map { it.trim() }.filter { it.isNotEmpty() }
            return@withContext MediaSourceInfo(
                packageName = srcPkg,
                sourcePath = srcPath,
                topLevelCounts = topLevelCounts,
                relativePaths = relativePaths
            )
        }

        onProgress("No media found in any WhatsApp installation")
        null
    }

    suspend fun copyMediaFileTo(sourcePath: String, relativePath: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        destFile.parentFile?.mkdirs()
        if (destFile.exists()) destFile.delete()
        val escapedRelativePath = relativePath.replace("\"", "\\\"")
        val escapedDestPath = destFile.absolutePath.replace("\"", "\\\"")
        val result = Shell.cmd(
            "cp -p \"$sourcePath/$escapedRelativePath\" \"$escapedDestPath\"",
            "chmod 644 \"$escapedDestPath\""
        ).exec()
        result.isSuccess && destFile.exists()
    }
}
