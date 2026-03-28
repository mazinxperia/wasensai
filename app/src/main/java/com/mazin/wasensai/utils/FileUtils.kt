package com.mazin.wasensai.utils

import android.content.Context
import java.io.File

object FileUtils {

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun getAvatarPath(extractedDir: File, jid: String): String {
        val phone = jid.substringBefore("@")
        val server = jid.substringAfter("@")
        val fileName = "${phone}_${server}_j.jpeg"
        return File(extractedDir, "avatars/$fileName").absolutePath
    }

    fun getMediaPath(extractedDir: File, relativePath: String): String {
        return File(extractedDir, "media/$relativePath").absolutePath
    }

    fun getCacheDir(context: Context): File {
        return File(context.cacheDir, "wasensai_extract").also { it.mkdirs() }
    }

    fun cleanCache(context: Context) {
        getCacheDir(context).deleteRecursively()
    }
}
