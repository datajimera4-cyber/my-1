package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormatter {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())

    fun formatSecondsToMmSs(totalSeconds: Int): String {
        val minutes = (totalSeconds / 60).coerceAtLeast(0)
        val seconds = (totalSeconds % 60).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun formatMillisToMmSs(millis: Long): String {
        val totalSeconds = (millis / 1000).toInt()
        return formatSecondsToMmSs(totalSeconds)
    }

    fun formatTimestamp(millis: Long): String {
        return timeFormat.format(Date(millis))
    }

    fun formatDate(millis: Long): String {
        return dateFormat.format(Date(millis))
    }
}
