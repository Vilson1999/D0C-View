package com.example.model

import android.net.Uri
import androidx.core.net.toUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        .coerceIn(0, units.lastIndex)
    return String.format(
        Locale.US,
        if (digitGroups == 0) "%.0f %s" else "%.1f %s",
        bytes / 1024.0.pow(digitGroups.toDouble()),
        units[digitGroups]
    )
}

data class DocumentItem(
    val id: String,
    val name: String,
    val path: String,
    val uriString: String,
    val type: DocType,
    val extension: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val parentFolderName: String,
    val isFromDotFolder: Boolean,
    val isFilePath: Boolean
) {
    val uri: Uri get() = uriString.toUri()

    val formattedSize: String get() = formatFileSize(sizeBytes)

    val formattedDate: String
        get() {
            if (lastModified <= 0L) return "Unknown"
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }

    val formattedDateTime: String
        get() {
            if (lastModified <= 0L) return "Unknown"
            val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }
}
