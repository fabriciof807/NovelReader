package com.novelreader.util

import android.net.Uri

object StringUtils {
    fun fileNameFromUrl(url: String, fallback: String = "chapter"): String {
        val segments = Uri.parse(url).pathSegments
        val last = segments.lastOrNull() ?: return fallback
        return last.removeSuffix(".html").removeSuffix(".htm").removeSuffix(".php")
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "_")
            .replace(Regex("\\."), "_")
            .trim('_', ' ')
            .take(200)
            .ifBlank { fallback }
    }

    fun sanitizeFileName(name: String, fallback: String = "unnamed"): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "_")
            .replace(Regex("\\."), "_")
            .trim('_', ' ')
            .take(200)
            .ifBlank { fallback }
    }

    fun fromFileName(fileName: String, fallback: String = "Unknown"): String {
        return fileName.substringBeforeLast(".")
            .replace("-", " ")
            .replace("_", " ")
            .trim()
            .ifEmpty { fallback }
    }
}
