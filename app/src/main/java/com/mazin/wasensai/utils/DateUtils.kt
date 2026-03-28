package com.mazin.wasensai.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {

    fun formatChatTimestamp(timestampMs: Long): String {
        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { timeInMillis = timestampMs }

        return when {
            isSameDay(now, msgCal) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
            isYesterday(now, msgCal) -> "Yesterday"
            isSameWeek(now, msgCal) -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestampMs))
            else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestampMs))
        }
    }

    fun formatMessageTimestamp(timestampMs: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
    }

    fun formatDateSeparator(timestampMs: Long): String {
        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { timeInMillis = timestampMs }

        return when {
            isSameDay(now, msgCal) -> "Today"
            isYesterday(now, msgCal) -> "Yesterday"
            else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestampMs))
        }
    }

    fun formatCallDuration(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    fun formatExportFilename(phoneNumber: String): String {
        val date = SimpleDateFormat("dd_MMMM_yyyy", Locale.getDefault()).format(Date())
        val cleanPhone = phoneNumber.replace("+", "").replace(" ", "")
        return "+${cleanPhone}_${date}.waview"
    }

    fun currentIso8601(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())
    }

    private fun isSameDay(c1: Calendar, c2: Calendar): Boolean =
        c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(now: Calendar, msg: Calendar): Boolean {
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(yesterday, msg)
    }

    private fun isSameWeek(c1: Calendar, c2: Calendar): Boolean =
        c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.WEEK_OF_YEAR) == c2.get(Calendar.WEEK_OF_YEAR)
}
